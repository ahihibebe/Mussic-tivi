package com.example.mobile.webrtc

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.mobile.model.MobileAudioTrack
import com.example.mobile.network.TvRemoteStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class WebRtcConnectionStage(val label: String) {
    IDLE("Chưa bật WebRTC"),
    GATHERING_STUN("Đang lấy địa chỉ STUN Google..."),
    WAITING_FOR_ANSWER("Đã có mã Offer — Chờ mã Answer từ Tivi"),
    CONNECTING("Đang kết nối P2P qua STUN Google..."),
    CONNECTED("Đã kết nối WebRTC khác mạng"),
    ERROR("Lỗi kết nối WebRTC")
}

data class MobileWebRtcState(
    val stage: WebRtcConnectionStage = WebRtcConnectionStage.IDLE,
    val stunServer: String = MobileWebRtcHost.PRIMARY_GOOGLE_STUN,
    val publicStunEndpoint: String? = null,
    val offerToken: String? = null,
    val iceStateText: String = "NEW",
    val statusDetail: String = "Dùng máy chủ STUN Google để stream nhạc qua Internet / khác Wi-Fi",
    val isDataChannelOpen: Boolean = false
)

class MobileWebRtcHost(
    rawContext: Context,
    private val getDeviceName: () -> String,
    private val getTracks: () -> List<MobileAudioTrack>,
    private val onTvStatusReceived: (TvRemoteStatus) -> Unit,
    private val onActivityLog: (String) -> Unit
) {
    private val context = rawContext.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(MobileWebRtcState())
    val state: StateFlow<MobileWebRtcState> = _state.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    fun createOfferToken(onReady: (Result<String>) -> Unit = {}) {
        scope.launch {
            val result = createOfferTokenSuspend()
            onReady(result)
        }
    }

    suspend fun createOfferTokenSuspend(): Result<String> {
        return try {
            closeSession(resetMessage = false)
            _state.value = MobileWebRtcState(
                stage = WebRtcConnectionStage.GATHERING_STUN,
                statusDetail = "Đang khởi tạo WebRTC & truy vấn STUN Google (${PRIMARY_GOOGLE_STUN})..."
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
                        PeerConnection.IceConnectionState.CHECKING -> {
                            _state.value = _state.value.copy(
                                stage = WebRtcConnectionStage.CONNECTING,
                                statusDetail = "Đang bắt tay P2P qua STUN Google..."
                            )
                        }
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _state.value = _state.value.copy(
                                stage = WebRtcConnectionStage.CONNECTED,
                                statusDetail = "Kết nối P2P thành công qua STUN Google"
                            )
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _state.value = _state.value.copy(
                                stage = WebRtcConnectionStage.ERROR,
                                isDataChannelOpen = false,
                                statusDetail = "NAT symmetric hoặc tường lửa chặn P2P — thử tạo lại mã Offer"
                            )
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.CLOSED -> {
                            if (_state.value.isDataChannelOpen) {
                                _state.value = _state.value.copy(
                                    stage = WebRtcConnectionStage.IDLE,
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
            }) ?: return Result.failure(IllegalStateException("Không thể tạo WebRTC PeerConnection"))

            peerConnection = pc

            val dcInit = DataChannel.Init().apply {
                ordered = true
            }
            val dc = pc.createDataChannel(DATA_CHANNEL_LABEL, dcInit)
            attachDataChannel(dc)

            val offerCreated = CompletableDeferred<Result<SessionDescription>>()
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) {
                        offerCreated.complete(Result.success(sdp))
                    } else {
                        offerCreated.complete(Result.failure(IllegalStateException("SDP Offer rỗng")))
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    offerCreated.complete(Result.failure(IllegalStateException(error ?: "Lỗi tạo Offer")))
                }

                override fun onSetFailure(error: String?) {}
            }, MediaConstraints())

            val offerSdp = offerCreated.await().getOrElse { err ->
                _state.value = _state.value.copy(
                    stage = WebRtcConnectionStage.ERROR,
                    statusDetail = "Lỗi tạo Offer: ${err.message}"
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
            }, offerSdp)

            setLocalDone.await().getOrElse { err ->
                _state.value = _state.value.copy(
                    stage = WebRtcConnectionStage.ERROR,
                    statusDetail = "Lỗi lưu Offer: ${err.message}"
                )
                return Result.failure(err)
            }

            // Wait up to 3 seconds for Google STUN ICE gathering to finish
            withTimeoutOrNull(3000L) {
                gatheringDone.await()
            }

            val fullSdp = pc.localDescription?.description ?: offerSdp.description
            val token = encodeSignalToken(OFFER_PREFIX, "offer", fullSdp)

            _state.value = _state.value.copy(
                stage = WebRtcConnectionStage.WAITING_FOR_ANSWER,
                offerToken = token,
                publicStunEndpoint = discoveredSrflx ?: _state.value.publicStunEndpoint,
                statusDetail = if (discoveredSrflx != null) {
                    "Đã lấy STUN Google ($discoveredSrflx) — Hãy gửi mã Offer sang Tivi"
                } else {
                    "Đã tạo mã Offer WebRTC — Hãy nhập mã này trên Tivi rồi dán mã Answer về đây"
                }
            )
            onActivityLog("Đã tạo mã WebRTC Offer (STUN Google)")
            Result.success(token)
        } catch (t: Throwable) {
            Log.e(TAG, "createOfferToken error", t)
            _state.value = _state.value.copy(
                stage = WebRtcConnectionStage.ERROR,
                statusDetail = "Lỗi WebRTC: ${t.message}"
            )
            Result.failure(t)
        }
    }

    fun applyAnswerToken(rawAnswerToken: String, onComplete: (Result<Unit>) -> Unit = {}) {
        scope.launch {
            val res = applyAnswerTokenSuspend(rawAnswerToken)
            onComplete(res)
        }
    }

    suspend fun applyAnswerTokenSuspend(rawAnswerToken: String): Result<Unit> {
        val pc = peerConnection ?: return Result.failure(
            IllegalStateException("Hãy bấm \"Tạo mã Offer WebRTC\" trước")
        )
        val decoded = decodeSignalToken(rawAnswerToken, expectedType = "answer")
            ?: return Result.failure(IllegalArgumentException("Mã Answer WebRTC không hợp lệ (phải bắt đầu bằng $ANSWER_PREFIX)"))

        _state.value = _state.value.copy(
            stage = WebRtcConnectionStage.CONNECTING,
            statusDetail = "Đang áp dụng mã Answer & đục lỗ NAT qua STUN Google..."
        )

        val deferred = CompletableDeferred<Result<Unit>>()
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onSetSuccess() {
                deferred.complete(Result.success(Unit))
            }

            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                deferred.complete(Result.failure(IllegalStateException(error ?: "Lỗi setRemoteDescription")))
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, decoded))

        return deferred.await().onFailure { err ->
            _state.value = _state.value.copy(
                stage = WebRtcConnectionStage.ERROR,
                statusDetail = "Không thể ghép Answer: ${err.message}"
            )
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
                    stage = if (open) WebRtcConnectionStage.CONNECTED else _state.value.stage,
                    statusDetail = if (open) {
                        "WebRTC DataChannel đã thông — Sẵn sàng stream nhạc khác mạng!"
                    } else {
                        _state.value.statusDetail
                    }
                )
                if (open) {
                    onActivityLog("WebRTC P2P đã kết nối qua STUN Google")
                    sendTextMessage(JSONObject().put("t", "status_req").toString())
                    pushTrackLibraryOverWebRtc()
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val text = String(bytes, StandardCharsets.UTF_8)
                    scope.launch {
                        handleIncomingTextMessage(text)
                    }
                }
            }
        })
    }

    private suspend fun handleIncomingTextMessage(text: String) {
        try {
            val obj = JSONObject(text)
            when (obj.optString("t")) {
                "tracks_req" -> {
                    pushTrackLibraryOverWebRtc()
                }
                "stream_open" -> {
                    val reqId = obj.optInt("reqId")
                    val trackId = obj.optString("trackId")
                    val offset = obj.optLong("offset", 0L).coerceAtLeast(0L)
                    val length = obj.optLong("length", DEFAULT_WINDOW_BYTES.toLong()).coerceIn(1L, MAX_WINDOW_BYTES.toLong())
                    scope.launch(Dispatchers.IO) {
                        streamTrackRange(reqId, trackId, offset, length)
                    }
                }
                "cover_req" -> {
                    val reqId = obj.optInt("reqId")
                    val trackId = obj.optString("trackId")
                    scope.launch(Dispatchers.IO) {
                        streamTrackCover(reqId, trackId)
                    }
                }
                "status" -> {
                    val snap = obj.optJSONObject("snapshot") ?: return
                    onTvStatusReceived(
                        TvRemoteStatus(
                            connected = true,
                            tvUrl = "webrtc://google-stun",
                            deviceName = snap.optString("deviceName", "Android TV (WebRTC)").ifBlank { "Android TV (WebRTC)" },
                            trackId = snap.optString("trackId", "").takeIf { it.isNotBlank() && it != "null" },
                            title = snap.optString("title", "Chưa phát bài nào"),
                            artist = snap.optString("artist", "TV Music"),
                            format = snap.optString("format", ""),
                            isPlaying = snap.optBoolean("isPlaying", false),
                            positionMs = snap.optLong("positionMs", 0L),
                            durationMs = snap.optLong("durationMs", 0L),
                            isShuffle = snap.optBoolean("isShuffle", false),
                            repeatMode = snap.optString("repeatMode", "ALL")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WebRTC text message", e)
        }
    }

    fun pushTrackLibraryOverWebRtc() {
        if (dataChannel?.state() != DataChannel.State.OPEN) return
        scope.launch(Dispatchers.IO) {
            val tracks = getTracks()
            val pageSize = 60
            val totalPages = if (tracks.isEmpty()) 1 else ((tracks.size + pageSize - 1) / pageSize)
            for (page in 0 until totalPages) {
                val from = page * pageSize
                val to = ((page + 1) * pageSize).coerceAtMost(tracks.size)
                val slice = if (tracks.isEmpty()) emptyList() else tracks.subList(from, to)
                val arr = JSONArray()
                slice.forEach { t ->
                    arr.put(
                        JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("artist", t.artist)
                            put("album", t.album)
                            put("durationMs", t.durationMs)
                            put("format", t.format)
                            put("bitrate", t.bitrate)
                            put("hasCover", !t.albumArtUri.isNullOrBlank())
                        }
                    )
                }
                val payload = JSONObject().apply {
                    put("t", "tracks_page")
                    put("page", page)
                    put("totalPages", totalPages)
                    put("done", page == totalPages - 1)
                    put("deviceName", getDeviceName())
                    put("tracks", arr)
                }
                sendTextMessage(payload.toString())
                delay(12L)
            }
        }
    }

    fun playTrackOnTv(track: MobileAudioTrack, queueTracks: List<MobileAudioTrack>): Boolean {
        if (dataChannel?.state() != DataChannel.State.OPEN) return false
        fun toJson(t: MobileAudioTrack): JSONObject = JSONObject().apply {
            put("id", t.id)
            put("title", t.title)
            put("artist", t.artist)
            put("album", t.album)
            put("format", t.format)
            put("bitrate", t.bitrate)
            put("durationMs", t.durationMs)
            put("hasCover", !t.albumArtUri.isNullOrBlank())
        }

        val selectedIndex = queueTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val start = (selectedIndex - 10).coerceAtLeast(0)
        val end = (start + 50).coerceAtMost(queueTracks.size)
        val queueSlice = if (queueTracks.isNotEmpty()) queueTracks.subList(start, end) else listOf(track)

        val payload = JSONObject().apply {
            put("t", "play")
            put("track", toJson(track))
            val qArr = JSONArray()
            queueSlice.forEach { qArr.put(toJson(it)) }
            put("queue", qArr)
        }
        onActivityLog("WebRTC stream tới TV: ${track.title}")
        return sendTextMessage(payload.toString())
    }

    fun sendControl(action: String, positionMs: Long? = null): Boolean {
        if (dataChannel?.state() != DataChannel.State.OPEN) return false
        val encodedAction = if (action == "seek" && positionMs != null) {
            "seek:$positionMs"
        } else {
            action
        }
        val payload = JSONObject().apply {
            put("t", "control")
            put("action", encodedAction)
        }
        return sendTextMessage(payload.toString())
    }

    private suspend fun streamTrackRange(reqId: Int, trackId: String, offset: Long, requestedLength: Long) {
        val track = getTracks().firstOrNull { it.id == trackId }
        if (track == null) {
            sendStreamEnd(reqId, ok = false, error = "Track not found")
            return
        }
        val mime = guessAudioMime(track.format)
        try {
            val file = track.filePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.canRead() }
            if (file != null) {
                val totalSize = file.length()
                if (offset >= totalSize) {
                    sendStreamMeta(reqId, totalSize, mime, 416)
                    sendStreamEnd(reqId, ok = true)
                    return
                }
                val toSend = requestedLength.coerceAtMost(totalSize - offset)
                sendStreamMeta(reqId, totalSize, mime, if (offset > 0L) 206 else 200)
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(offset)
                    val buf = ByteArray(STREAM_CHUNK_BYTES)
                    var remaining = toSend
                    while (remaining > 0L && dataChannel?.state() == DataChannel.State.OPEN) {
                        val read = raf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        if (!sendBinaryChunkWithBackpressure(reqId, buf, read)) break
                        remaining -= read
                    }
                }
                sendStreamEnd(reqId, ok = true)
                return
            }

            val uriStr = track.contentUri
            if (!uriStr.isNullOrBlank()) {
                val uri = Uri.parse(uriStr)
                val totalSize = resolveContentLength(uri, track.fileSizeBytes)
                sendStreamMeta(reqId, totalSize, mime, if (offset > 0L) 206 else 200)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    var skipped = 0L
                    val skipBuf = ByteArray(16 * 1024)
                    while (skipped < offset) {
                        val s = input.read(skipBuf, 0, minOf(skipBuf.size.toLong(), offset - skipped).toInt())
                        if (s <= 0) break
                        skipped += s
                    }
                    val buf = ByteArray(STREAM_CHUNK_BYTES)
                    var remaining = requestedLength
                    while (remaining > 0L && dataChannel?.state() == DataChannel.State.OPEN) {
                        val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        if (!sendBinaryChunkWithBackpressure(reqId, buf, read)) break
                        remaining -= read
                    }
                }
                sendStreamEnd(reqId, ok = true)
                return
            }

            sendStreamEnd(reqId, ok = false, error = "Unreadable audio source")
        } catch (e: Exception) {
            Log.e(TAG, "streamTrackRange failed", e)
            sendStreamEnd(reqId, ok = false, error = e.message ?: "Stream read error")
        }
    }

    private suspend fun streamTrackCover(reqId: Int, trackId: String) {
        val track = getTracks().firstOrNull { it.id == trackId }
        val bytes: ByteArray? = try {
            val artPath = track?.albumArtUri
            if (!artPath.isNullOrBlank() && artPath.startsWith("/")) {
                File(artPath).takeIf { it.exists() }?.readBytes()
            } else {
                val mmr = MediaMetadataRetriever()
                try {
                    if (track?.filePath?.isNotBlank() == true) {
                        mmr.setDataSource(track.filePath)
                    } else if (!track?.contentUri.isNullOrBlank()) {
                        mmr.setDataSource(context, Uri.parse(track!!.contentUri))
                    }
                    mmr.embeddedPicture
                } finally {
                    runCatching { mmr.release() }
                }
            }
        } catch (_: Exception) {
            null
        }

        if (bytes != null && bytes.isNotEmpty()) {
            sendTextMessage(
                JSONObject().apply {
                    put("t", "cover_meta")
                    put("reqId", reqId)
                    put("totalSize", bytes.size)
                }.toString()
            )
            var offset = 0
            while (offset < bytes.size && dataChannel?.state() == DataChannel.State.OPEN) {
                val len = minOf(STREAM_CHUNK_BYTES, bytes.size - offset)
                val slice = bytes.copyOfRange(offset, offset + len)
                if (!sendBinaryChunkWithBackpressure(reqId, slice, len)) break
                offset += len
            }
            sendTextMessage(JSONObject().apply { put("t", "cover_end"); put("reqId", reqId); put("ok", true) }.toString())
        } else {
            sendTextMessage(JSONObject().apply { put("t", "cover_end"); put("reqId", reqId); put("ok", false) }.toString())
        }
    }

    private fun resolveContentLength(uri: Uri, fallback: Long): Long {
        if (fallback > 0L) return fallback
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) return afd.length
            }
        } catch (_: Exception) {
        }
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) cursor.getLong(idx).takeIf { it > 0L } ?: 0L else 0L
                } else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun sendStreamMeta(reqId: Int, totalSize: Long, mime: String, status: Int) {
        sendTextMessage(
            JSONObject().apply {
                put("t", "stream_meta")
                put("reqId", reqId)
                put("totalSize", totalSize)
                put("mime", mime)
                put("status", status)
            }.toString()
        )
    }

    private fun sendStreamEnd(reqId: Int, ok: Boolean, error: String = "") {
        sendTextMessage(
            JSONObject().apply {
                put("t", "stream_end")
                put("reqId", reqId)
                put("ok", ok)
                put("error", error)
            }.toString()
        )
    }

    private fun sendTextMessage(text: String): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        val buffer = ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8))
        return dc.send(DataChannel.Buffer(buffer, false))
    }

    private suspend fun sendBinaryChunkWithBackpressure(reqId: Int, data: ByteArray, length: Int): Boolean {
        val dc = dataChannel ?: return false
        var waited = 0
        while (dc.bufferedAmount() > MAX_BUFFERED_BYTES && dc.state() == DataChannel.State.OPEN && waited < 400) {
            delay(15L)
            waited++
        }
        if (dc.state() != DataChannel.State.OPEN) return false

        val packet = ByteBuffer.allocate(4 + length)
        packet.putInt(reqId)
        packet.put(data, 0, length)
        packet.flip()
        return dc.send(DataChannel.Buffer(packet, true))
    }

    fun closeSession(resetMessage: Boolean = true) {
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
            _state.value = MobileWebRtcState()
        }
    }

    companion object {
        private const val TAG = "MobileWebRtcHost"
        const val PRIMARY_GOOGLE_STUN = "stun:stun.l.google.com:19302"
        val GOOGLE_STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302"
        )
        const val DATA_CHANNEL_LABEL = "tvmusic_stream"
        const val OFFER_PREFIX = "TVRTC1:"
        const val ANSWER_PREFIX = "TVANS1:"
        private const val STREAM_CHUNK_BYTES = 16 * 1024
        private const val DEFAULT_WINDOW_BYTES = 512 * 1024
        private const val MAX_WINDOW_BYTES = 2 * 1024 * 1024
        private const val MAX_BUFFERED_BYTES = 1024 * 1024L

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

        private fun guessAudioMime(format: String): String = when (format.trim().uppercase()) {
            "FLAC" -> "audio/flac"
            "WAV" -> "audio/wav"
            "MP3" -> "audio/mpeg"
            "AAC", "M4A" -> "audio/mp4"
            "OGG" -> "audio/ogg"
            "OPUS" -> "audio/opus"
            else -> "application/octet-stream"
        }
    }
}
