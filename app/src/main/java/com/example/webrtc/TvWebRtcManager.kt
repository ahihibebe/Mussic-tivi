package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.model.AudioTrack
import com.example.qr.RemoteCastTrackRequest
import com.example.qr.TvPlaybackSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class TvWebRtcStage(val label: String) {
    IDLE("Chưa bật WebRTC"),
    GATHERING_STUN("Đang lấy STUN Google & tạo Answer..."),
    WAITING_CONNECTION("Đã có mã Answer — Chờ điện thoại kết nối..."),
    CONNECTED("Đã kết nối WebRTC khác mạng (STUN Google)"),
    ERROR("Lỗi kết nối WebRTC")
}

data class TvWebRtcUiState(
    val stage: TvWebRtcStage = TvWebRtcStage.IDLE,
    val stunServer: String = TvWebRtcManager.PRIMARY_GOOGLE_STUN,
    val publicStunEndpoint: String? = null,
    val answerToken: String? = null,
    val remoteDeviceName: String? = null,
    val iceStateText: String = "NEW",
    val statusDetail: String = "Nhận nhạc trực tiếp từ điện thoại khác mạng qua STUN Google",
    val isDataChannelOpen: Boolean = false
)

private data class StreamWindowMeta(
    val totalSize: Long,
    val mime: String,
    val status: Int
)

private class PendingWebRtcTransfer {
    val metaDeferred = CompletableDeferred<StreamWindowMeta>()
    val chunkChannel = Channel<ByteArray>(capacity = 64)
    val doneDeferred = CompletableDeferred<Boolean>()
}

class TvWebRtcManager(
    rawContext: Context,
    private val getTvStatus: () -> TvPlaybackSnapshot,
    private val onRemoteTracksReceived: (deviceName: String, tracks: List<AudioTrack>) -> Unit,
    private val onPlayRemoteTrack: (RemoteCastTrackRequest) -> Unit,
    private val onTvControl: (String) -> Unit
) {
    private val context = rawContext.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(TvWebRtcUiState())
    val state: StateFlow<TvWebRtcUiState> = _state.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private val nextRequestId = AtomicInteger(1)
    private val pendingTransfers = ConcurrentHashMap<Int, PendingWebRtcTransfer>()
    private val incomingTrackBuffer = ArrayList<AudioTrack>()
    private var incomingDeviceName: String = "Điện thoại (WebRTC)"

    private var proxyServer: ServerSocket? = null
    @Volatile
    private var proxyPort: Int = 0
    private val textCommandChannel = Channel<String>(capacity = Channel.UNLIMITED)

    init {
        scope.launch {
            for (msg in textCommandChannel) {
                handleIncomingTextMessage(msg)
            }
        }
    }

    suspend fun acceptOfferAndCreateAnswer(rawOfferToken: String): Result<String> {
        val offerSdp = decodeSignalToken(rawOfferToken, expectedType = "offer")
            ?: return Result.failure(IllegalArgumentException("Mã Offer WebRTC không hợp lệ (phải bắt đầu bằng $OFFER_PREFIX)"))

        return try {
            closeSession(resetMessage = false)
            ensureProxyStarted()

            _state.value = TvWebRtcUiState(
                stage = TvWebRtcStage.GATHERING_STUN,
                statusDetail = "Đang truy vấn STUN Google (${PRIMARY_GOOGLE_STUN})..."
            )

            val factory = getOrCreateFactory(context)
            val gatheringDone = CompletableDeferred<Unit>()
            var discoveredSrflx: String? = null

            val iceServers = GOOGLE_STUN_SERVERS.map { url ->
                PeerConnection.IceServer.builder(url).createIceServer()
            }
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            }

            val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    val name = newState?.name ?: "UNKNOWN"
                    _state.value = _state.value.copy(iceStateText = name)
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _state.value = _state.value.copy(
                                stage = TvWebRtcStage.CONNECTED,
                                statusDetail = "WebRTC P2P đã kết nối qua STUN Google!"
                            )
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _state.value = _state.value.copy(
                                stage = TvWebRtcStage.ERROR,
                                isDataChannelOpen = false,
                                statusDetail = "Kết nối P2P thất bại (NAT chặn) — thử tạo Offer/Answer mới"
                            )
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.CLOSED -> {
                            if (_state.value.isDataChannelOpen) {
                                _state.value = _state.value.copy(
                                    stage = TvWebRtcStage.IDLE,
                                    isDataChannelOpen = false,
                                    statusDetail = "Kết nối WebRTC đã ngắt"
                                )
                            }
                        }
                        else -> {}
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                    if (newState == PeerConnection.IceGatheringState.COMPLETE && !gatheringDone.isCompleted) {
                        gatheringDone.complete(Unit)
                    }
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    val sdp = candidate?.sdp ?: return
                    extractSrflxEndpoint(sdp)?.let { endpoint ->
                        discoveredSrflx = endpoint
                        _state.value = _state.value.copy(publicStunEndpoint = endpoint)
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(dc: DataChannel?) {
                    if (dc != null) {
                        attachDataChannel(dc)
                    }
                }

                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
            }) ?: return Result.failure(IllegalStateException("Không thể khởi tạo WebRTC trên TV"))

            peerConnection = pc

            val setRemoteDone = CompletableDeferred<Result<Unit>>()
            pc.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onSetSuccess() {
                    setRemoteDone.complete(Result.success(Unit))
                }

                override fun onCreateFailure(error: String?) {}
                override fun onSetFailure(error: String?) {
                    setRemoteDone.complete(Result.failure(IllegalStateException(error ?: "Lỗi setRemoteDescription")))
                }
            }, SessionDescription(SessionDescription.Type.OFFER, offerSdp))

            setRemoteDone.await().getOrElse { err ->
                _state.value = _state.value.copy(
                    stage = TvWebRtcStage.ERROR,
                    statusDetail = "Offer không hợp lệ: ${err.message}"
                )
                return Result.failure(err)
            }

            val answerCreated = CompletableDeferred<Result<SessionDescription>>()
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) {
                        answerCreated.complete(Result.success(sdp))
                    } else {
                        answerCreated.complete(Result.failure(IllegalStateException("Answer rỗng")))
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    answerCreated.complete(Result.failure(IllegalStateException(error ?: "Lỗi tạo Answer")))
                }

                override fun onSetFailure(error: String?) {}
            }, MediaConstraints())

            val answerSdp = answerCreated.await().getOrElse { err ->
                _state.value = _state.value.copy(
                    stage = TvWebRtcStage.ERROR,
                    statusDetail = "Lỗi tạo Answer: ${err.message}"
                )
                return Result.failure(err)
            }

            val setLocalDone = CompletableDeferred<Result<Unit>>()
            pc.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onSetSuccess() {
                    setLocalDone.complete(Result.success(Unit))
                }

                override fun onCreateFailure(error: String?) {}
                override fun onSetFailure(error: String?) {
                    setLocalDone.complete(Result.failure(IllegalStateException(error ?: "Lỗi setLocalDescription")))
                }
            }, answerSdp)

            setLocalDone.await().getOrElse { err ->
                _state.value = _state.value.copy(
                    stage = TvWebRtcStage.ERROR,
                    statusDetail = "Lỗi lưu Answer: ${err.message}"
                )
                return Result.failure(err)
            }

            withTimeoutOrNull(3000L) {
                gatheringDone.await()
            }

            val fullSdp = pc.localDescription?.description ?: answerSdp.description
            val answerToken = encodeSignalToken(ANSWER_PREFIX, "answer", fullSdp)

            _state.value = _state.value.copy(
                stage = TvWebRtcStage.WAITING_CONNECTION,
                answerToken = answerToken,
                publicStunEndpoint = discoveredSrflx ?: _state.value.publicStunEndpoint,
                statusDetail = if (discoveredSrflx != null) {
                    "STUN Google ($discoveredSrflx) sẵn sàng — Gửi mã Answer về điện thoại"
                } else {
                    "Đã tạo mã Answer — Hãy nhập mã này trên điện thoại để thông luồng WebRTC"
                }
            )
            Result.success(answerToken)
        } catch (t: Throwable) {
            Log.e(TAG, "acceptOfferAndCreateAnswer error", t)
            _state.value = _state.value.copy(
                stage = TvWebRtcStage.ERROR,
                statusDetail = "Lỗi WebRTC: ${t.message}"
            )
            Result.failure(t)
        }
    }

    private fun attachDataChannel(dc: DataChannel) {
        dataChannel?.unregisterObserver()
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val open = dc.state() == DataChannel.State.OPEN
                _state.value = _state.value.copy(
                    isDataChannelOpen = open,
                    stage = if (open) TvWebRtcStage.CONNECTED else _state.value.stage,
                    statusDetail = if (open) {
                        "Đã kết nối WebRTC khác mạng — Đang đồng bộ kho nhạc từ điện thoại..."
                    } else {
                        _state.value.statusDetail
                    }
                )
                if (open) {
                    ensureProxyStarted()
                    sendTextMessage(JSONObject().put("t", "tracks_req").toString())
                    pushTvStatus(getTvStatus())
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) {
                    if (buffer.data.remaining() < 4) return
                    val reqId = buffer.data.int
                    val payload = ByteArray(buffer.data.remaining())
                    buffer.data.get(payload)
                    pendingTransfers[reqId]?.chunkChannel?.trySend(payload)
                } else {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val text = String(bytes, StandardCharsets.UTF_8)
                    if (!tryHandleStreamControlFast(text)) {
                        textCommandChannel.trySend(text)
                    }
                }
            }
        })
    }

    private fun tryHandleStreamControlFast(text: String): Boolean {
        return try {
            val obj = JSONObject(text)
            when (obj.optString("t")) {
                "stream_meta" -> {
                    val reqId = obj.optInt("reqId")
                    val meta = StreamWindowMeta(
                        totalSize = obj.optLong("totalSize", 0L),
                        mime = obj.optString("mime", "application/octet-stream"),
                        status = obj.optInt("status", 200)
                    )
                    pendingTransfers[reqId]?.metaDeferred?.complete(meta)
                    true
                }
                "stream_end" -> {
                    val reqId = obj.optInt("reqId")
                    val ok = obj.optBoolean("ok", true)
                    pendingTransfers[reqId]?.let { t ->
                        t.chunkChannel.close()
                        t.doneDeferred.complete(ok)
                        if (!t.metaDeferred.isCompleted) {
                            t.metaDeferred.complete(StreamWindowMeta(0L, "application/octet-stream", 404))
                        }
                    }
                    true
                }
                "cover_meta" -> {
                    val reqId = obj.optInt("reqId")
                    val totalSize = obj.optLong("totalSize", 0L)
                    pendingTransfers[reqId]?.metaDeferred?.complete(StreamWindowMeta(totalSize, "image/jpeg", 200))
                    true
                }
                "cover_end" -> {
                    val reqId = obj.optInt("reqId")
                    val ok = obj.optBoolean("ok", false)
                    pendingTransfers[reqId]?.let { t ->
                        t.chunkChannel.close()
                        t.doneDeferred.complete(ok)
                        if (!t.metaDeferred.isCompleted) {
                            t.metaDeferred.complete(StreamWindowMeta(0L, "image/jpeg", 404))
                        }
                    }
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun handleIncomingTextMessage(text: String) {
        try {
            val obj = JSONObject(text)
            when (obj.optString("t")) {
                "tracks_page" -> {
                    val page = obj.optInt("page", 0)
                    val done = obj.optBoolean("done", true)
                    val devName = obj.optString("deviceName", "Điện thoại (WebRTC)").ifBlank { "Điện thoại (WebRTC)" }
                    if (page == 0) {
                        incomingTrackBuffer.clear()
                        incomingDeviceName = devName
                    }
                    val arr = obj.optJSONArray("tracks")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            incomingTrackBuffer.add(jsonToAudioTrack(item, devName))
                        }
                    }
                    if (done) {
                        val snapshotList = incomingTrackBuffer.toList()
                        _state.value = _state.value.copy(
                            remoteDeviceName = devName,
                            statusDetail = "WebRTC STUN: Đã tải ${snapshotList.size} bài hát từ $devName"
                        )
                        onRemoteTracksReceived(devName, snapshotList)
                    }
                }
                "play" -> {
                    val trackObj = obj.optJSONObject("track") ?: return
                    val primary = jsonToRemoteCast(trackObj)
                    val queueArr = obj.optJSONArray("queue")
                    val queueList = mutableListOf<RemoteCastTrackRequest>()
                    if (queueArr != null) {
                        for (i in 0 until queueArr.length()) {
                            queueArr.optJSONObject(i)?.let { queueList.add(jsonToRemoteCast(it)) }
                        }
                    }
                    onPlayRemoteTrack(primary.copy(queueTracks = queueList))
                    pushTvStatus(getTvStatus())
                }
                "control" -> {
                    val action = obj.optString("action")
                    if (action.isNotBlank()) {
                        onTvControl(action)
                    }
                }
                "status_req" -> {
                    pushTvStatus(getTvStatus())
                }
                "stream_meta" -> {
                    val reqId = obj.optInt("reqId")
                    val meta = StreamWindowMeta(
                        totalSize = obj.optLong("totalSize", 0L),
                        mime = obj.optString("mime", "application/octet-stream"),
                        status = obj.optInt("status", 200)
                    )
                    pendingTransfers[reqId]?.metaDeferred?.complete(meta)
                }
                "stream_end" -> {
                    val reqId = obj.optInt("reqId")
                    val ok = obj.optBoolean("ok", true)
                    pendingTransfers[reqId]?.let { t ->
                        t.chunkChannel.close()
                        t.doneDeferred.complete(ok)
                        if (!t.metaDeferred.isCompleted) {
                            t.metaDeferred.complete(StreamWindowMeta(0L, "application/octet-stream", 404))
                        }
                    }
                }
                "cover_meta" -> {
                    val reqId = obj.optInt("reqId")
                    val totalSize = obj.optLong("totalSize", 0L)
                    pendingTransfers[reqId]?.metaDeferred?.complete(StreamWindowMeta(totalSize, "image/jpeg", 200))
                }
                "cover_end" -> {
                    val reqId = obj.optInt("reqId")
                    val ok = obj.optBoolean("ok", false)
                    pendingTransfers[reqId]?.let { t ->
                        t.chunkChannel.close()
                        t.doneDeferred.complete(ok)
                        if (!t.metaDeferred.isCompleted) {
                            t.metaDeferred.complete(StreamWindowMeta(0L, "image/jpeg", 404))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleIncomingTextMessage error", e)
        }
    }

    fun requestTracksRefresh() {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            sendTextMessage(JSONObject().put("t", "tracks_req").toString())
        }
    }

    fun pushTvStatus(snapshot: TvPlaybackSnapshot) {
        if (dataChannel?.state() != DataChannel.State.OPEN) return
        val snapObj = JSONObject().apply {
            put("trackId", snapshot.trackId)
            put("title", snapshot.title)
            put("artist", snapshot.artist)
            put("album", snapshot.album)
            put("format", snapshot.format)
            put("isPlaying", snapshot.isPlaying)
            put("positionMs", snapshot.positionMs)
            put("durationMs", snapshot.durationMs)
            put("isShuffle", snapshot.isShuffle)
            put("repeatMode", snapshot.repeatMode)
            put("deviceName", snapshot.deviceName)
        }
        sendTextMessage(
            JSONObject().apply {
                put("t", "status")
                put("snapshot", snapObj)
            }.toString()
        )
    }

    private fun jsonToAudioTrack(item: JSONObject, devName: String): AudioTrack {
        val rawId = item.optString("id")
        val encodedId = URLEncoder.encode(rawId, "UTF-8")
        val hasCover = item.optBoolean("hasCover", false)
        val streamUrl = "http://127.0.0.1:$proxyPort/webrtc/stream?id=$encodedId"
        val coverUrl = if (hasCover) "http://127.0.0.1:$proxyPort/webrtc/cover?id=$encodedId" else null
        return AudioTrack(
            id = "webrtc_$rawId",
            title = item.optString("title", "Bài hát WebRTC"),
            artist = item.optString("artist", "Nghệ sĩ chưa rõ"),
            album = item.optString("album", devName),
            durationMs = item.optLong("durationMs", 0L),
            filePath = streamUrl,
            contentUri = streamUrl,
            format = item.optString("format", "MP3"),
            bitrate = item.optString("bitrate", "WebRTC P2P"),
            sampleRate = "WebRTC STUN",
            fileSizeBytes = 0L,
            parentDirectory = "WebRTC: $devName",
            albumArtUri = coverUrl
        )
    }

    private fun jsonToRemoteCast(item: JSONObject): RemoteCastTrackRequest {
        val rawId = item.optString("id")
        val encodedId = URLEncoder.encode(rawId, "UTF-8")
        val hasCover = item.optBoolean("hasCover", false)
        val streamUrl = "http://127.0.0.1:$proxyPort/webrtc/stream?id=$encodedId"
        val coverUrl = if (hasCover) "http://127.0.0.1:$proxyPort/webrtc/cover?id=$encodedId" else null
        return RemoteCastTrackRequest(
            id = "webrtc_$rawId",
            streamUrl = streamUrl,
            title = item.optString("title", "Bài hát từ điện thoại"),
            artist = item.optString("artist", "Nghệ sĩ chưa rõ"),
            album = item.optString("album", "WebRTC STUN"),
            format = item.optString("format", "MP3"),
            bitrate = item.optString("bitrate", "WebRTC P2P"),
            durationMs = item.optLong("durationMs", 0L),
            coverUrl = coverUrl
        )
    }

    @Synchronized
    private fun ensureProxyStarted() {
        if (proxyServer != null && proxyServer?.isClosed == false && proxyPort > 0) return
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        proxyServer = ss
        proxyPort = ss.localPort
        scope.launch {
            while (isActive && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    launch { handleProxyClient(client) }
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun handleProxyClient(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = 30_000
                socket.tcpNoDelay = true
                val input = BufferedInputStream(socket.getInputStream(), 8192)
                val output = socket.getOutputStream()

                val requestLine = readHttpLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase(Locale.ROOT)
                val rawTarget = parts[1]
                val path = rawTarget.substringBefore("?")
                val query = rawTarget.substringAfter("?", "")
                val trackId = query.split("&")
                    .firstOrNull { it.startsWith("id=") }
                    ?.substringAfter("id=")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
                    .orEmpty()

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase(Locale.ROOT)] = line.substring(idx + 1).trim()
                    }
                }

                if (trackId.isBlank() || dataChannel?.state() != DataChannel.State.OPEN) {
                    writeSimpleResponse(output, 503, "Service Unavailable", "text/plain", ByteArray(0))
                    return
                }

                when (path) {
                    "/webrtc/cover" -> {
                        val reqId = nextRequestId.getAndIncrement()
                        val transfer = PendingWebRtcTransfer()
                        pendingTransfers[reqId] = transfer
                        try {
                            sendTextMessage(
                                JSONObject().apply {
                                    put("t", "cover_req")
                                    put("reqId", reqId)
                                    put("trackId", trackId)
                                }.toString()
                            )
                            val meta = withTimeoutOrNull(8000L) { transfer.metaDeferred.await() }
                            if (meta == null || meta.status != 200) {
                                writeSimpleResponse(output, 404, "Not Found", "text/plain", ByteArray(0))
                            } else {
                                val baos = ByteArrayOutputStream()
                                for (chunk in transfer.chunkChannel) {
                                    baos.write(chunk)
                                }
                                writeSimpleResponse(output, 200, "OK", "image/jpeg", baos.toByteArray())
                            }
                        } finally {
                            pendingTransfers.remove(reqId)
                        }
                    }
                    "/webrtc/stream" -> {
                        val rangeHeader = headers["range"]
                        val startByte = parseRangeStart(rangeHeader)
                        val requestedEnd = parseRangeEnd(rangeHeader)

                        var currentOffset = startByte
                        val firstWindowLen = if (requestedEnd != null) {
                            (requestedEnd - startByte + 1L).coerceIn(1L, STREAM_WINDOW_BYTES.toLong())
                        } else {
                            STREAM_WINDOW_BYTES.toLong()
                        }

                        val firstReqId = nextRequestId.getAndIncrement()
                        val firstTransfer = PendingWebRtcTransfer()
                        pendingTransfers[firstReqId] = firstTransfer
                        var headersWritten = false
                        var endInclusive = 0L

                        try {
                            sendTextMessage(
                                JSONObject().apply {
                                    put("t", "stream_open")
                                    put("reqId", firstReqId)
                                    put("trackId", trackId)
                                    put("offset", currentOffset)
                                    put("length", firstWindowLen)
                                }.toString()
                            )

                            val meta = withTimeoutOrNull(12_000L) { firstTransfer.metaDeferred.await() }
                            if (meta == null || meta.totalSize <= 0L || meta.status == 404 || meta.status == 416) {
                                val code = meta?.status ?: 504
                                writeSimpleResponse(output, code, "Stream Unavailable", "text/plain", ByteArray(0))
                                return
                            }

                            val totalSize = meta.totalSize
                            endInclusive = (requestedEnd ?: (totalSize - 1L)).coerceAtMost(totalSize - 1L)
                            val contentLength = (endInclusive - startByte + 1L).coerceAtLeast(0L)

                            val sb = StringBuilder()
                            if (rangeHeader != null) {
                                sb.append("HTTP/1.1 206 Partial Content\r\n")
                                sb.append("Content-Range: bytes $startByte-$endInclusive/$totalSize\r\n")
                            } else {
                                sb.append("HTTP/1.1 200 OK\r\n")
                            }
                            sb.append("Content-Type: ${meta.mime}\r\n")
                            sb.append("Accept-Ranges: bytes\r\n")
                            sb.append("Content-Length: $contentLength\r\n")
                            sb.append("Connection: close\r\n\r\n")
                            output.write(sb.toString().toByteArray(StandardCharsets.US_ASCII))
                            output.flush()
                            headersWritten = true

                            if (method == "HEAD") return

                            for (chunk in firstTransfer.chunkChannel) {
                                output.write(chunk)
                                currentOffset += chunk.size
                            }
                            output.flush()
                        } finally {
                            pendingTransfers.remove(firstReqId)
                        }

                        if (!headersWritten) return

                        // Continue streaming subsequent 512KB windows cleanly until EOF or client disconnect
                        while (currentOffset <= endInclusive && dataChannel?.state() == DataChannel.State.OPEN) {
                            val remaining = endInclusive - currentOffset + 1L
                            val nextLen = remaining.coerceAtMost(STREAM_WINDOW_BYTES.toLong())
                            val reqId = nextRequestId.getAndIncrement()
                            val transfer = PendingWebRtcTransfer()
                            pendingTransfers[reqId] = transfer
                            var receivedInWindow = 0L
                            try {
                                sendTextMessage(
                                    JSONObject().apply {
                                        put("t", "stream_open")
                                        put("reqId", reqId)
                                        put("trackId", trackId)
                                        put("offset", currentOffset)
                                        put("length", nextLen)
                                    }.toString()
                                )
                                val meta = withTimeoutOrNull(12_000L) { transfer.metaDeferred.await() }
                                if (meta == null || meta.status >= 400) break
                                for (chunk in transfer.chunkChannel) {
                                    output.write(chunk)
                                    currentOffset += chunk.size
                                    receivedInWindow += chunk.size
                                }
                                output.flush()
                                if (receivedInWindow == 0L) break
                            } finally {
                                pendingTransfers.remove(reqId)
                            }
                        }
                    }
                    else -> {
                        writeSimpleResponse(output, 404, "Not Found", "text/plain", ByteArray(0))
                    }
                }
            }
        } catch (_: Exception) {
            // Normal when ExoPlayer closes socket on seek/skip
        }
    }

    private fun parseRangeStart(rangeHeader: String?): Long {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) return 0L
        return rangeHeader.removePrefix("bytes=").substringBefore("-").toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    }

    private fun parseRangeEnd(rangeHeader: String?): Long? {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) return null
        val after = rangeHeader.removePrefix("bytes=").substringAfter("-", "").trim()
        return if (after.isBlank()) null else after.toLongOrNull()
    }

    private fun readHttpLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (baos.size() == 0) null else baos.toString(StandardCharsets.UTF_8.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    private fun writeSimpleResponse(output: OutputStream, code: Int, reason: String, mime: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    private fun sendTextMessage(text: String): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        val buffer = ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8))
        return dc.send(DataChannel.Buffer(buffer, false))
    }

    fun closeSession(resetMessage: Boolean = true) {
        pendingTransfers.values.forEach {
            it.chunkChannel.close()
            it.doneDeferred.complete(false)
        }
        pendingTransfers.clear()
        try {
            dataChannel?.unregisterObserver()
            dataChannel?.close()
            dataChannel?.dispose()
        } catch (_: Exception) {
        }
        dataChannel = null
        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Exception) {
        }
        peerConnection = null
        if (resetMessage) {
            _state.value = TvWebRtcUiState()
        }
    }

    companion object {
        private const val TAG = "TvWebRtcManager"
        const val PRIMARY_GOOGLE_STUN = "stun:stun.l.google.com:19302"
        val GOOGLE_STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302"
        )
        const val OFFER_PREFIX = "TVRTC1:"
        const val ANSWER_PREFIX = "TVANS1:"
        private const val STREAM_WINDOW_BYTES = 512 * 1024

        @Volatile
        private var factory: PeerConnectionFactory? = null

        @Synchronized
        fun getOrCreateFactory(context: Context): PeerConnectionFactory {
            factory?.let { return it }
            val initOpts = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOpts)
            val created = PeerConnectionFactory.builder().createPeerConnectionFactory()
            factory = created
            return created
        }

        fun encodeSignalToken(prefix: String, type: String, sdp: String): String {
            val json = JSONObject().apply {
                put("t", type)
                put("sdp", sdp)
            }.toString()
            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gz ->
                gz.write(json.toByteArray(StandardCharsets.UTF_8))
            }
            val encoded = encodeBase64UrlNoPad(baos.toByteArray())
            return "$prefix$encoded"
        }

        fun decodeSignalToken(rawToken: String, expectedType: String): String? {
            return try {
                val cleaned = rawToken.trim().replace("\\s+".toRegex(), "")
                val payload = when {
                    cleaned.startsWith(OFFER_PREFIX) -> cleaned.removePrefix(OFFER_PREFIX)
                    cleaned.startsWith(ANSWER_PREFIX) -> cleaned.removePrefix(ANSWER_PREFIX)
                    else -> cleaned
                }
                val compressed = decodeBase64UrlNoPad(payload)
                val jsonStr = GZIPInputStream(ByteArrayInputStream(compressed)).use { gz ->
                    gz.readBytes().toString(StandardCharsets.UTF_8)
                }
                val obj = JSONObject(jsonStr)
                if (obj.optString("t") != expectedType) return null
                obj.optString("sdp").takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

        private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        internal fun encodeBase64UrlNoPad(data: ByteArray): String {
            if (data.isEmpty()) return ""
            val out = StringBuilder(((data.size * 4) + 2) / 3)
            var i = 0
            while (i < data.size) {
                val b0 = data[i].toInt() and 0xFF
                val hasB1 = i + 1 < data.size
                val hasB2 = i + 2 < data.size
                val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
                val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0
                val triple = (b0 shl 16) or (b1 shl 8) or b2
                out.append(BASE64_URL_ALPHABET[(triple ushr 18) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(triple ushr 12) and 0x3F])
                if (hasB1) out.append(BASE64_URL_ALPHABET[(triple ushr 6) and 0x3F])
                if (hasB2) out.append(BASE64_URL_ALPHABET[triple and 0x3F])
                i += 3
            }
            return out.toString()
        }

        internal fun decodeBase64UrlNoPad(input: String): ByteArray {
            if (input.isEmpty()) return ByteArray(0)
            val baos = ByteArrayOutputStream((input.length * 3) / 4)
            var buffer = 0
            var bits = 0
            for (ch in input) {
                if (ch == '=') break
                val v = when (ch) {
                    in 'A'..'Z' -> ch.code - 'A'.code
                    in 'a'..'z' -> ch.code - 'a'.code + 26
                    in '0'..'9' -> ch.code - '0'.code + 52
                    '-', '+' -> 62
                    '_', '/' -> 63
                    else -> continue
                }
                buffer = (buffer shl 6) or v
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    baos.write((buffer ushr bits) and 0xFF)
                }
            }
            return baos.toByteArray()
        }

        fun extractSrflxEndpoint(candidateSdp: String): String? {
            val parts = candidateSdp.trim().split(" ")
            val typIdx = parts.indexOf("typ")
            if (typIdx > 0 && typIdx + 1 < parts.size && parts[typIdx + 1] == "srflx") {
                val ip = parts.getOrNull(4) ?: return null
                val port = parts.getOrNull(5) ?: return null
                return "$ip:$port"
            }
            return null
        }
    }
}
