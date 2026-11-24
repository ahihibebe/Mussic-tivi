package com.example.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.model.WifiMusicRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class TvPlaybackSnapshot(
    val trackId: String? = null,
    val title: String = "Chưa phát bài nào",
    val artist: String = "TV Music",
    val album: String = "",
    val format: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class LocalBackgroundServer(
    private val context: Context,
    private val getTracks: () -> List<AudioTrack> = { emptyList() },
    private val getCoverArtStyle: () -> CoverArtStyle = { CoverArtStyle.SPINNING_DISC },
    private val getRole: () -> WifiMusicRole = { WifiMusicRole.TV_WEB_HUB },
    private val getTvStatus: () -> TvPlaybackSnapshot = { TvPlaybackSnapshot() },
    private val onPlayTrackOnTv: (String) -> Unit = {},
    private val onTvControl: (String) -> Unit = {},
    private val onAudioUploaded: (File, Boolean) -> Unit = { _, _ -> },
    private val onImageUploaded: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit,
    private val onClientActivity: (String) -> Unit = {}
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var broadcastJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var serverUrl: String? = null
        private set

    val isRunning: Boolean
        get() = serverSocket?.let { !it.isClosed } == true && serverUrl != null

    @Synchronized
    fun start(): String {
        val current = serverUrl
        if (isRunning && current != null) {
            return current
        }
        stop()
        val ip = getLocalIpv4Address() ?: "127.0.0.1"
        val socket = try {
            ServerSocket(8765)
        } catch (e: Exception) {
            ServerSocket(0)
        }
        serverSocket = socket
        val port = socket.localPort
        val url = "http://$ip:$port"
        serverUrl = url

        onStatusUpdate("Đang mở máy chủ Wi-Fi tại $url")
        onClientActivity("Sẵn sàng tại $url")

        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch { handleClient(client) }
                } catch (e: Exception) {
                    if (!socket.isClosed) {
                        Log.e("LocalBgServer", "Accept error", e)
                    }
                }
            }
        }

        broadcastJob = scope.launch {
            while (isActive && !socket.isClosed) {
                if (getRole() == WifiMusicRole.PHONE_SERVER_HOST) {
                    sendDiscoveryBeacon(url, port, getTracks().size)
                }
                delay(1800L)
            }
        }
        return url
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        broadcastJob?.cancel()
        broadcastJob = null
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {
        }
        serverSocket = null
        serverUrl = null
    }

    private fun sendDiscoveryBeacon(url: String, port: Int, tracksCount: Int) {
        try {
            val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                .replace("|", " ")
                .trim()
                .ifBlank { "Android Phone" }
            val payload = "TVMUSIC_DISCOVER|1|$url|$deviceName|$tracksCount|PHONE_SERVER|$port"
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            DatagramSocket().use { udp ->
                udp.broadcast = true
                val packet = DatagramPacket(
                    bytes,
                    bytes.size,
                    InetAddress.getByName("255.255.255.255"),
                    DISCOVERY_UDP_PORT
                )
                udp.send(packet)

                // Also send to each interface directed broadcast for routers blocking 255.255.255.255
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { intf ->
                    if (!intf.isUp || intf.isLoopback) return@forEach
                    intf.interfaceAddresses.forEach { ia ->
                        val bcast = ia.broadcast ?: return@forEach
                        try {
                            udp.send(DatagramPacket(bytes, bytes.size, bcast, DISCOVERY_UDP_PORT))
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = 60_000
                socket.tcpNoDelay = true
                val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
                val output = socket.getOutputStream()

                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase(Locale.ROOT)
                val rawTarget = parts[1]
                val path = rawTarget.substringBefore("?")
                val queryParams = parseQueryParams(rawTarget.substringAfter("?", ""))

                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                        val value = line.substring(colon + 1).trim()
                        headers[key] = value
                    }
                }

                when {
                    method == "OPTIONS" -> {
                        sendResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                    }

                    method == "GET" && (path == "/" || path == "/index.html") -> {
                        onClientActivity("Điện thoại đã kết nối trình phát Wi-Fi")
                        val html = buildWebAppHtml()
                        sendResponse(
                            output,
                            200,
                            "OK",
                            "text/html; charset=utf-8",
                            html.toByteArray(StandardCharsets.UTF_8)
                        )
                    }

                    method == "GET" && path == "/api/server-info" -> {
                        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
                        val roleStr = if (getRole() == WifiMusicRole.PHONE_SERVER_HOST) "PHONE_SERVER" else "TV_HUB"
                        val port = serverSocket?.localPort ?: 8765
                        val url = serverUrl ?: ""
                        val json = """{"app":"TVMusic","deviceName":"${escapeJson(deviceName)}","role":"$roleStr","tracksCount":${getTracks().size},"port":$port,"url":"${escapeJson(url)}"}"""
                        sendResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            json.toByteArray(StandardCharsets.UTF_8)
                        )
                    }

                    method == "GET" && path == "/api/tracks" -> {
                        val tracks = getTracks()
                        onClientActivity("Thiết bị từ Wi-Fi đã quét ${tracks.size} bài hát")
                        val json = buildTracksJson(
                            tracks = tracks,
                            coverArtStyle = getCoverArtStyle(),
                            tvStatus = getTvStatus()
                        )
                        sendResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            json.toByteArray(StandardCharsets.UTF_8)
                        )
                    }

                    method == "GET" && path == "/api/tv/status" -> {
                        val json = buildTvStatusJson(getTvStatus(), getCoverArtStyle(), getTracks().size)
                        sendResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            json.toByteArray(StandardCharsets.UTF_8)
                        )
                    }

                    method == "POST" && path == "/api/tv/play" -> {
                        val id = queryParams["id"].orEmpty()
                        val track = getTracks().firstOrNull { it.id == id }
                        if (track != null) {
                            onPlayTrackOnTv(track.id)
                            onClientActivity("Điện thoại chọn phát trên TV: ${track.title}")
                            sendJsonOk(output, "Đang phát trên TV: ${track.title}")
                        } else {
                            sendJsonError(output, 404, "Không tìm thấy bài hát")
                        }
                    }

                    method == "POST" && path == "/api/tv/control" -> {
                        val action = queryParams["action"].orEmpty()
                        if (action in setOf("toggle", "next", "prev")) {
                            onTvControl(action)
                            sendJsonOk(output, "Đã gửi lệnh điều khiển TV")
                        } else {
                            sendJsonError(output, 400, "Lệnh không hợp lệ")
                        }
                    }

                    method == "GET" && path == "/api/cover" -> {
                        val id = queryParams["id"].orEmpty()
                        val track = getTracks().firstOrNull { it.id == id }
                        handleCoverRequest(output, track)
                    }

                    (method == "GET" || method == "HEAD") && path == "/api/stream" -> {
                        val id = queryParams["id"].orEmpty()
                        val track = getTracks().firstOrNull { it.id == id }
                        if (track == null) {
                            sendResponse(
                                output,
                                404,
                                "Not Found",
                                "text/plain; charset=utf-8",
                                "Track not found".toByteArray(StandardCharsets.UTF_8)
                            )
                        } else {
                            if (method == "GET") {
                                onClientActivity("Điện thoại đang phát: ${track.title}")
                            }
                            socket.soTimeout = 0
                            handleAudioStream(
                                output = output,
                                track = track,
                                rangeHeader = headers["range"],
                                headOnly = (method == "HEAD")
                            )
                        }
                    }

                    method == "POST" && path == "/api/upload-audio" -> {
                        socket.soTimeout = 180_000
                        handleAudioUpload(input, output, headers, queryParams)
                    }

                    method == "POST" && path == "/upload" -> {
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        if (contentLength <= 0 || contentLength > 25 * 1024 * 1024) {
                            sendJsonError(output, 400, "Kích thước ảnh không hợp lệ (tối đa 25MB)")
                            return
                        }

                        val bodyBytes = readExactBytes(input, contentLength)
                        val savedPath = saveUploadedImage(bodyBytes)
                        if (savedPath != null) {
                            onStatusUpdate("Đã nhận ảnh nền mới từ điện thoại!")
                            onClientActivity("Đã nhận ảnh nền mới từ điện thoại")
                            onImageUploaded(savedPath)
                            sendJsonOk(output, "Đổi ảnh nền Tivi thành công!")
                        } else {
                            sendJsonError(output, 400, "Không thể giải mã tệp ảnh")
                        }
                    }

                    else -> {
                        sendResponse(
                            output,
                            404,
                            "Not Found",
                            "text/plain; charset=utf-8",
                            "Not Found".toByteArray(StandardCharsets.UTF_8)
                        )
                    }
                }
            }
        } catch (ignored: Exception) {
            // Client closed connection mid-stream (normal when seeking or skipping tracks)
        }
    }

    private fun handleCoverRequest(output: OutputStream, track: AudioTrack?) {
        val artUri = track?.albumArtUri
        if (!artUri.isNullOrBlank()) {
            try {
                val bytes = if (artUri.startsWith("/")) {
                    val file = File(artUri)
                    if (file.exists() && file.length() > 0L) file.readBytes() else null
                } else {
                    context.contentResolver.openInputStream(Uri.parse(artUri))?.use { it.readBytes() }
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    val mime = if (artUri.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
                    sendResponse(
                        output = output,
                        statusCode = 200,
                        statusText = "OK",
                        contentType = mime,
                        body = bytes,
                        extraHeaders = mapOf("Cache-Control" to "public, max-age=3600")
                    )
                    return
                }
            } catch (ignored: Exception) {
            }
        }

        // Clean built-in SVG fallback cover art
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#1e293b"/><stop offset="100%" stop-color="#0f172a"/></linearGradient></defs><rect width="200" height="200" fill="url(#g)"/><circle cx="100" cy="100" r="54" fill="none" stroke="#38bdf8" stroke-width="4" opacity="0.35"/><path d="M118 62v58.5a17.5 17.5 0 1 1-10-15.8V74l-26 6.5v45a17.5 17.5 0 1 1-10-15.8V69.5a6 6 0 0 1 4.5-5.8l36-9A4.5 4.5 0 0 1 118 59z" fill="#38bdf8"/></svg>"""
        sendResponse(
            output = output,
            statusCode = 200,
            statusText = "OK",
            contentType = "image/svg+xml; charset=utf-8",
            body = svg.toByteArray(StandardCharsets.UTF_8),
            extraHeaders = mapOf("Cache-Control" to "public, max-age=3600")
        )
    }

    private fun handleAudioStream(
        output: OutputStream,
        track: AudioTrack,
        rangeHeader: String?,
        headOnly: Boolean
    ) {
        val mime = guessAudioMime(track)
        val file = File(track.filePath)

        if (file.exists() && file.canRead() && file.length() > 0L) {
            val totalLength = file.length()
            val range = parseByteRange(rangeHeader, totalLength)
            if (rangeHeader != null && range == null) {
                val errHeaders = buildString {
                    append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                    append("Content-Range: bytes */$totalLength\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(errHeaders.toByteArray(StandardCharsets.US_ASCII))
                output.flush()
                return
            }

            val start = range?.first ?: 0L
            val end = range?.last ?: (totalLength - 1)
            val contentLength = end - start + 1
            val isPartial = range != null

            val header = buildString {
                append(if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mime\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $contentLength\r\n")
                if (isPartial) {
                    append("Content-Range: bytes $start-$end/$totalLength\r\n")
                }
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(StandardCharsets.US_ASCII))
            if (!headOnly) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(16 * 1024)
                    var remaining = contentLength
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = raf.read(buffer, 0, toRead)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            output.flush()
            return
        }

        // Fallback via ContentResolver if direct File path is unavailable
        val uriStr = track.contentUri
        if (!uriStr.isNullOrBlank()) {
            val uri = Uri.parse(uriStr)
            val afdLength = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (ignored: Exception) {
                -1L
            }
            val totalLength = when {
                afdLength > 0L -> afdLength
                track.fileSizeBytes > 0L -> track.fileSizeBytes
                else -> -1L
            }

            if (totalLength > 0L && rangeHeader != null) {
                val range = parseByteRange(rangeHeader, totalLength)
                if (range != null) {
                    val start = range.first
                    val end = range.last
                    val contentLength = end - start + 1
                    val header = buildString {
                        append("HTTP/1.1 206 Partial Content\r\n")
                        append("Content-Type: $mime\r\n")
                        append("Accept-Ranges: bytes\r\n")
                        append("Content-Length: $contentLength\r\n")
                        append("Content-Range: bytes $start-$end/$totalLength\r\n")
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    output.write(header.toByteArray(StandardCharsets.US_ASCII))
                    if (!headOnly) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            skipFully(stream, start)
                            val buffer = ByteArray(16 * 1024)
                            var remaining = contentLength
                            while (remaining > 0L) {
                                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                val read = stream.read(buffer, 0, toRead)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                remaining -= read
                            }
                        }
                    }
                    output.flush()
                    return
                }
            }

            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mime\r\n")
                if (totalLength > 0L) {
                    append("Accept-Ranges: bytes\r\n")
                    append("Content-Length: $totalLength\r\n")
                }
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(StandardCharsets.US_ASCII))
            if (!headOnly) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            output.flush()
            return
        }

        sendResponse(
            output,
            404,
            "Not Found",
            "text/plain; charset=utf-8",
            "Audio stream unavailable".toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun handleAudioUpload(
        input: BufferedInputStream,
        output: OutputStream,
        headers: Map<String, String>,
        queryParams: Map<String, String>
    ) {
        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
        val maxAudioBytes = 200L * 1024L * 1024L
        if (contentLength <= 0L || contentLength > maxAudioBytes) {
            sendJsonError(output, 400, "Kích thước file nhạc không hợp lệ (tối đa 200MB)")
            return
        }

        val rawName = queryParams["filename"].orEmpty().ifBlank {
            "wifi_track_${System.currentTimeMillis()}.mp3"
        }
        val safeName = sanitizeAudioFilename(rawName)
        val ext = safeName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in SUPPORTED_AUDIO_EXTENSIONS) {
            sendJsonError(output, 400, "Định dạng .$ext chưa được hỗ trợ")
            return
        }

        val autoplay = queryParams["autoplay"] == "1" || queryParams["autoplay"] == "true"
        val wifiDir = File(context.filesDir, "wifi_music").apply { mkdirs() }
        val targetFile = uniqueTargetFile(wifiDir, safeName)

        try {
            FileOutputStream(targetFile).use { fos ->
                val buffer = ByteArray(16 * 1024)
                var remaining = contentLength
                while (remaining > 0L) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    remaining -= read
                }
                fos.flush()
            }

            if (targetFile.length() <= 0L) {
                targetFile.delete()
                sendJsonError(output, 400, "File nhạc tải lên bị rỗng")
                return
            }

            onClientActivity("Đã nhận bài hát từ điện thoại: ${targetFile.name}")
            onAudioUploaded(targetFile, autoplay)
            sendJsonOk(
                output,
                if (autoplay) "Đã gửi '${targetFile.name}' và phát trên TV!"
                else "Đã lưu '${targetFile.name}' vào thư mục Nhạc từ Wi-Fi!"
            )
        } catch (e: Exception) {
            targetFile.delete()
            Log.e("LocalBgServer", "Audio upload failed", e)
            sendJsonError(output, 500, "Lỗi khi lưu file nhạc lên TV")
        }
    }

    private fun saveUploadedImage(rawBytes: ByteArray): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            val maxDim = 2560
            while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null

            context.filesDir.listFiles { f ->
                f.name.startsWith("custom_bg_") && f.name.endsWith(".jpg")
            }?.forEach { it.delete() }

            val outFile = File(context.filesDir, "custom_bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            bitmap.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e("LocalBgServer", "Failed saving uploaded image", e)
            null
        }
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (read <= 0) break
                remaining -= read
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                val len = sb.length
                if (len > 0 && sb[len - 1] == '\r') {
                    sb.deleteCharAt(len - 1)
                }
                return sb.toString()
            }
            sb.append(b.toChar())
        }
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        val baos = ByteArrayOutputStream(length)
        val buffer = ByteArray(8192)
        var remaining = length
        while (remaining > 0) {
            val toRead = minOf(buffer.size, remaining)
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            baos.write(buffer, 0, read)
            remaining -= read
        }
        return baos.toByteArray()
    }

    private fun sendJsonOk(output: OutputStream, message: String) {
        val json = """{"ok":true,"message":"${escapeJson(message)}"}"""
        sendResponse(output, 200, "OK", "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendJsonError(output: OutputStream, code: Int, error: String) {
        val label = if (code == 404) "Not Found" else if (code >= 500) "Internal Server Error" else "Bad Request"
        val json = """{"ok":false,"error":"${escapeJson(error)}"}"""
        sendResponse(output, code, label, "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap()
    ) {
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            extraHeaders.forEach { (k, v) ->
                append("$k: $v\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    companion object {
        const val DEFAULT_HTTP_PORT = 8765
        const val DISCOVERY_UDP_PORT = 8766

        fun getLocalIpv4Address(): String? {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                val candidates = mutableListOf<Pair<Int, String>>()
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    val name = intf.name.lowercase(Locale.ROOT)
                    val priority = when {
                        name.startsWith("wlan") || name.startsWith("wifi") || name.startsWith("ap") -> 0
                        name.startsWith("eth") || name.startsWith("en") -> 1
                        name.startsWith("tun") || name.startsWith("ppp") -> 4
                        else -> 2
                    }
                    for (addr in intf.inetAddresses.toList()) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            if (!host.startsWith("127.")) {
                                val privateBonus = if (host.startsWith("192.168.") || host.startsWith("10.")) 0 else 1
                                candidates.add((priority * 10 + privateBonus) to host)
                            }
                        }
                    }
                }
                candidates.minByOrNull { it.first }?.second
            } catch (e: Exception) {
                null
            }
        }

        val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma"
        )

        fun parseQueryParams(queryString: String): Map<String, String> {
            if (queryString.isBlank()) return emptyMap()
            val result = mutableMapOf<String, String>()
            for (pair in queryString.split("&")) {
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val key = tryDecode(pair.substring(0, eq))
                    val value = tryDecode(pair.substring(eq + 1))
                    result[key] = value
                } else if (pair.isNotBlank()) {
                    result[tryDecode(pair)] = ""
                }
            }
            return result
        }

        private fun tryDecode(raw: String): String {
            return try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (e: Exception) {
                raw
            }
        }

        fun parseByteRange(rangeHeader: String?, totalLength: Long): LongRange? {
            if (rangeHeader.isNullOrBlank() || totalLength <= 0L) return null
            val trimmed = rangeHeader.trim()
            if (!trimmed.startsWith("bytes=", ignoreCase = true)) return null
            val spec = trimmed.substring(6).substringBefore(",").trim()
            val dash = spec.indexOf('-')
            if (dash < 0) return null

            val startPart = spec.substring(0, dash).trim()
            val endPart = spec.substring(dash + 1).trim()

            return if (startPart.isEmpty()) {
                val suffix = endPart.toLongOrNull() ?: return null
                if (suffix <= 0L) return null
                val start = (totalLength - suffix).coerceAtLeast(0L)
                start..(totalLength - 1L)
            } else {
                val start = startPart.toLongOrNull() ?: return null
                if (start < 0L || start >= totalLength) return null
                val end = if (endPart.isEmpty()) {
                    totalLength - 1L
                } else {
                    (endPart.toLongOrNull() ?: return null).coerceAtMost(totalLength - 1L)
                }
                if (end < start) return null
                start..end
            }
        }

        fun sanitizeAudioFilename(rawName: String): String {
            val baseName = rawName.substringAfterLast('/').substringAfterLast('\\')
            val cleaned = baseName.replace(Regex("[^a-zA-Z0-9._\\- ()\\p{L}]"), "_").trim()
            return if (cleaned.isBlank()) "track_${System.currentTimeMillis()}.mp3" else cleaned
        }

        private fun uniqueTargetFile(dir: File, filename: String): File {
            val dot = filename.lastIndexOf('.')
            val name = if (dot > 0) filename.substring(0, dot) else filename
            val ext = if (dot > 0) filename.substring(dot) else ""
            var candidate = File(dir, filename)
            var counter = 1
            while (candidate.exists()) {
                candidate = File(dir, "${name}_$counter$ext")
                counter++
            }
            return candidate
        }

        fun guessAudioMime(track: AudioTrack): String {
            return when (track.format.uppercase(Locale.ROOT)) {
                "FLAC" -> "audio/flac"
                "WAV" -> "audio/wav"
                "M4A", "AAC" -> "audio/mp4"
                "OGG" -> "audio/ogg"
                "OPUS" -> "audio/opus"
                "WMA" -> "audio/x-ms-wma"
                else -> "audio/mpeg"
            }
        }

        fun escapeJson(value: String): String {
            val sb = StringBuilder(value.length + 8)
            for (ch in value) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (ch.code < 0x20) {
                            sb.append("\\u%04x".format(ch.code))
                        } else {
                            sb.append(ch)
                        }
                    }
                }
            }
            return sb.toString()
        }

        fun buildTracksJson(
            tracks: List<AudioTrack>,
            coverArtStyle: CoverArtStyle,
            tvStatus: TvPlaybackSnapshot
        ): String {
            val sb = StringBuilder()
            sb.append('{')
            sb.append("\"coverStyle\":\"${coverArtStyle.name}\",")
            sb.append("\"tvStatus\":")
            sb.append(buildTvStatusObj(tvStatus))
            sb.append(",\"tracks\":[")
            tracks.forEachIndexed { index, t ->
                if (index > 0) sb.append(',')
                sb.append('{')
                sb.append("\"id\":\"${escapeJson(t.id)}\",")
                sb.append("\"title\":\"${escapeJson(t.title)}\",")
                sb.append("\"artist\":\"${escapeJson(t.artist)}\",")
                sb.append("\"album\":\"${escapeJson(t.album)}\",")
                sb.append("\"durationMs\":${t.durationMs},")
                sb.append("\"formattedDuration\":\"${escapeJson(t.formattedDuration)}\",")
                sb.append("\"format\":\"${escapeJson(t.format)}\",")
                sb.append("\"bitrate\":\"${escapeJson(t.bitrate)}\",")
                sb.append("\"hasCover\":${!t.albumArtUri.isNullOrBlank()}")
                sb.append('}')
            }
            sb.append("]}")
            return sb.toString()
        }

        fun buildTvStatusJson(
            tvStatus: TvPlaybackSnapshot,
            coverArtStyle: CoverArtStyle,
            tracksCount: Int
        ): String {
            return "{\"coverStyle\":\"${coverArtStyle.name}\",\"tracksCount\":$tracksCount,\"tvStatus\":${buildTvStatusObj(tvStatus)}}"
        }

        private fun buildTvStatusObj(tvStatus: TvPlaybackSnapshot): String {
            return buildString {
                append('{')
                if (tvStatus.trackId != null) {
                    append("\"trackId\":\"${escapeJson(tvStatus.trackId)}\",")
                } else {
                    append("\"trackId\":null,")
                }
                append("\"title\":\"${escapeJson(tvStatus.title)}\",")
                append("\"artist\":\"${escapeJson(tvStatus.artist)}\",")
                append("\"format\":\"${escapeJson(tvStatus.format)}\",")
                append("\"isPlaying\":${tvStatus.isPlaying},")
                append("\"positionMs\":${tvStatus.positionMs},")
                append("\"durationMs\":${tvStatus.durationMs}")
                append('}')
            }
        }
    }

    private fun buildWebAppHtml(): String = """
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <title>TV Music • Phát Nhạc Wi-Fi</title>
  <style>
    :root {
      --bg: #0b1120;
      --card: rgba(30, 41, 59, 0.85);
      --border: rgba(148, 163, 184, 0.18);
      --text: #f8fafc;
      --muted: #94a3b8;
      --accent: #38bdf8;
      --accent2: #2563eb;
      --green: #10b981;
      --red: #fb7185;
    }
    * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; -webkit-tap-highlight-color: transparent; }
    body {
      margin: 0;
      background: radial-gradient(circle at 15% 10%, #1e3a8a 0%, var(--bg) 52%);
      background-attachment: fixed;
      color: var(--text);
      padding-bottom: 160px;
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 20;
      backdrop-filter: blur(18px);
      -webkit-backdrop-filter: blur(18px);
      background: rgba(11, 17, 32, 0.85);
      border-bottom: 1px solid var(--border);
      padding: 12px 16px;
    }
    .brand-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
    .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; font-size: 18px; letter-spacing: 0.2px; }
    .wifi-pill {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 4px 10px; border-radius: 999px; font-size: 11px; font-weight: 700;
      background: rgba(16, 185, 129, 0.16); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.35);
    }
    .dot { width: 7px; height: 7px; border-radius: 50%; background: #34d399; }
    .tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .tab-btn {
      padding: 9px 6px; border-radius: 12px; border: 1px solid var(--border);
      background: rgba(255,255,255,0.05); color: var(--muted); font-size: 12px; font-weight: 700;
      cursor: pointer; transition: all 0.15s;
    }
    .tab-btn.active {
      background: linear-gradient(135deg, var(--accent2), #0284c7);
      color: #fff; border-color: rgba(56, 189, 248, 0.6);
      box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35);
    }
    .container { max-width: 680px; margin: 0 auto; padding: 14px 14px 20px; }
    .panel { display: none; }
    .panel.active { display: block; }
    .card {
      background: var(--card); border: 1px solid var(--border); border-radius: 18px;
      padding: 16px; margin-bottom: 14px; backdrop-filter: blur(16px);
    }
    .tv-remote {
      display: flex; align-items: center; justify-content: space-between; gap: 10px;
      padding: 12px 14px; border-radius: 16px;
      background: linear-gradient(135deg, rgba(37, 99, 235, 0.22), rgba(15, 23, 42, 0.85));
      border: 1px solid rgba(56, 189, 248, 0.28); margin-bottom: 14px;
    }
    .tv-remote-info { min-width: 0; flex: 1; }
    .tv-remote-label { font-size: 10px; text-transform: uppercase; letter-spacing: 0.7px; color: var(--accent); font-weight: 800; }
    .tv-remote-title { font-size: 13px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }
    .tv-remote-btns { display: flex; gap: 6px; flex-shrink: 0; }
    .icon-btn {
      width: 36px; height: 36px; border-radius: 10px; border: 1px solid var(--border);
      background: rgba(255,255,255,0.08); color: #fff; font-size: 14px; cursor: pointer;
      display: inline-flex; align-items: center; justify-content: center;
    }
    .search-box {
      width: 100%; padding: 11px 14px; border-radius: 12px; border: 1px solid var(--border);
      background: rgba(15, 23, 42, 0.8); color: #fff; font-size: 14px; outline: none; margin-bottom: 10px;
    }
    .search-box:focus { border-color: var(--accent); }
    .chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 8px; margin-bottom: 6px; }
    .chip {
      padding: 6px 12px; border-radius: 999px; border: 1px solid var(--border);
      background: rgba(255,255,255,0.05); color: var(--muted); font-size: 12px; font-weight: 600;
      white-space: nowrap; cursor: pointer;
    }
    .chip.active { background: rgba(56, 189, 248, 0.18); color: var(--accent); border-color: var(--accent); }
    .track-list { display: flex; flex-direction: column; gap: 8px; }
    .track-item {
      display: flex; align-items: center; gap: 12px; padding: 10px; border-radius: 14px;
      background: rgba(15, 23, 42, 0.65); border: 1px solid var(--border); cursor: pointer;
      transition: border-color 0.15s, background 0.15s;
    }
    .track-item.playing { background: rgba(37, 99, 235, 0.22); border-color: rgba(56, 189, 248, 0.55); }
    .cover {
      width: 48px; height: 48px; object-fit: cover; flex-shrink: 0;
      background: #1e293b; border: 1px solid rgba(255,255,255,0.14);
    }
    .cover.square { border-radius: 9px; }
    .cover.disc { border-radius: 50%; }
    .cover.spinning { animation: spin 7.5s linear infinite; }
    @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    .track-meta { flex: 1; min-width: 0; }
    .track-title { font-size: 14px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .track-sub { font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }
    .track-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
    .badge {
      font-size: 10px; font-weight: 800; padding: 3px 6px; border-radius: 6px;
      background: rgba(56, 189, 248, 0.15); color: var(--accent);
    }
    .cast-btn {
      padding: 6px 9px; border-radius: 9px; border: 1px solid var(--border);
      background: rgba(255,255,255,0.08); color: #e2e8f0; font-size: 11px; font-weight: 700; cursor: pointer;
    }
    .player-dock {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 30;
      background: rgba(15, 23, 42, 0.95); backdrop-filter: blur(22px);
      -webkit-backdrop-filter: blur(22px);
      border-top: 1px solid rgba(56, 189, 248, 0.3);
      padding: 10px 14px 14px;
      display: none;
    }
    .player-dock.visible { display: block; }
    .player-inner { max-width: 680px; margin: 0 auto; }
    .player-top { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
    .player-cover { width: 46px; height: 46px; object-fit: cover; flex-shrink: 0; border: 1px solid rgba(255,255,255,0.2); }
    .player-info { flex: 1; min-width: 0; }
    .player-title { font-size: 14px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .player-artist { font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .progress-row { display: flex; align-items: center; gap: 8px; font-size: 11px; color: var(--muted); margin-bottom: 8px; }
    .seek { flex: 1; accent-color: var(--accent); height: 4px; }
    .controls { display: flex; align-items: center; justify-content: space-between; }
    .ctrl-btn {
      border: none; background: transparent; color: #e2e8f0; font-size: 18px;
      padding: 6px 10px; border-radius: 10px; cursor: pointer;
    }
    .ctrl-btn.active { color: var(--accent); background: rgba(56, 189, 248, 0.14); }
    .play-main {
      width: 44px; height: 44px; border-radius: 50%; border: none;
      background: linear-gradient(135deg, var(--accent), var(--accent2));
      color: #fff; font-size: 18px; font-weight: 800; cursor: pointer;
      display: inline-flex; align-items: center; justify-content: center;
      box-shadow: 0 4px 14px rgba(56, 189, 248, 0.35);
    }
    .drop {
      display: block; border: 2px dashed rgba(56, 189, 248, 0.45); background: rgba(56, 189, 248, 0.08);
      border-radius: 16px; padding: 24px 16px; text-align: center; color: var(--accent);
      font-weight: 700; cursor: pointer; margin-bottom: 14px;
    }
    input[type=file] { display: none; }
    .primary-btn {
      width: 100%; padding: 13px; border: none; border-radius: 14px;
      background: linear-gradient(135deg, var(--accent2), #0284c7); color: #fff;
      font-size: 15px; font-weight: 700; cursor: pointer;
    }
    .primary-btn:disabled { opacity: 0.45; cursor: not-allowed; }
    .status-msg { margin-top: 12px; font-size: 13px; font-weight: 600; text-align: center; min-height: 20px; }
    .ok { color: #34d399; }
    .err { color: var(--red); }
    .progress-bar-wrap { width: 100%; height: 8px; border-radius: 999px; background: rgba(255,255,255,0.1); overflow: hidden; margin-top: 10px; display: none; }
    .progress-bar-fill { height: 100%; width: 0%; background: var(--accent); transition: width 0.15s; }
    #bgPreview { width: 100%; max-height: 240px; object-fit: cover; border-radius: 12px; display: none; margin-bottom: 14px; border: 1px solid var(--border); }
    .empty { text-align: center; padding: 32px 16px; color: var(--muted); font-size: 14px; }
    label.check-row { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #e2e8f0; margin-bottom: 14px; cursor: pointer; }
  </style>
</head>
<body>
  <div class="topbar">
    <div class="brand-row">
      <div class="brand">🎵 TV Music Wi-Fi</div>
      <div class="wifi-pill"><span class="dot"></span> Cùng mạng Wi-Fi</div>
    </div>
    <div class="tabs">
      <button class="tab-btn active" id="tabBtnPlay" onclick="switchTab('play')">🎧 Nghe trên ĐT</button>
      <button class="tab-btn" id="tabBtnUpload" onclick="switchTab('upload')">📤 Gửi nhạc lên TV</button>
      <button class="tab-btn" id="tabBtnBg" onclick="switchTab('bg')">🖼️ Ảnh nền TV</button>
    </div>
  </div>

  <div class="container">
    <!-- TAB 1: PHONE PLAYER -->
    <div class="panel active" id="panelPlay">
      <div class="tv-remote">
        <div class="tv-remote-info">
          <div class="tv-remote-label">📺 Trên Tivi</div>
          <div class="tv-remote-title" id="tvNowTitle">Đang tải trạng thái TV...</div>
        </div>
        <div class="tv-remote-btns">
          <button class="icon-btn" title="TV bài trước" onclick="tvControl('prev')">⏮</button>
          <button class="icon-btn" title="Phát/Dừng TV" id="tvToggleBtn" onclick="tvControl('toggle')">⏯</button>
          <button class="icon-btn" title="TV bài sau" onclick="tvControl('next')">⏭</button>
        </div>
      </div>

      <input class="search-box" id="searchInput" type="search" placeholder="🔍 Tìm tên bài hát, nghệ sĩ, album..." oninput="renderTracks()" />
      <div class="chips" id="formatChips">
        <button class="chip active" onclick="setFormat('ALL', this)">Tất cả</button>
        <button class="chip" onclick="setFormat('FLAC', this)">FLAC</button>
        <button class="chip" onclick="setFormat('MP3', this)">MP3</button>
        <button class="chip" onclick="setFormat('WAV', this)">WAV</button>
        <button class="chip" onclick="setFormat('M4A', this)">M4A</button>
      </div>

      <div id="trackList" class="track-list">
        <div class="empty">Đang tải danh sách nhạc từ Tivi...</div>
      </div>
    </div>

    <!-- TAB 2: UPLOAD AUDIO TO TV -->
    <div class="panel" id="panelUpload">
      <div class="card">
        <h3 style="margin:0 0 6px;">Gửi bài hát từ điện thoại sang TV</h3>
        <p style="margin:0 0 16px; color:var(--muted); font-size:13px; line-height:1.5;">
          Chọn tệp nhạc MP3, FLAC, WAV, AAC, M4A, OGG từ điện thoại để lưu vào thư mục <b>Nhạc từ Wi-Fi</b> và phát trên TV.
        </p>
        <label class="drop" for="audioInput">🎵 Chọn bài hát từ điện thoại (hỗ trợ nhiều bài)</label>
        <input id="audioInput" type="file" accept="audio/*,.mp3,.flac,.wav,.aac,.m4a,.ogg,.opus" multiple />
        <div id="audioFileList" style="font-size:13px; color:var(--muted); margin-bottom:12px;"></div>
        <label class="check-row">
          <input type="checkbox" id="autoplayCheck" checked style="width:18px;height:18px;accent-color:#38bdf8;" />
          <span>Phát ngay bài đầu tiên trên TV sau khi gửi xong</span>
        </label>
        <button class="primary-btn" id="uploadAudioBtn" disabled onclick="uploadAudioFiles()">Gửi nhạc lên Tivi</button>
        <div class="progress-bar-wrap" id="audioProgressWrap"><div class="progress-bar-fill" id="audioProgressFill"></div></div>
        <div id="audioStatus" class="status-msg"></div>
      </div>
    </div>

    <!-- TAB 3: BACKGROUND PHOTO UPLOAD -->
    <div class="panel" id="panelBg">
      <div class="card">
        <h3 style="margin:0 0 6px;">Đổi Ảnh Nền TV Music</h3>
        <p style="margin:0 0 16px; color:var(--muted); font-size:13px; line-height:1.5;">
          Chọn ảnh từ thư viện điện thoại để đặt làm hình nền trực tiếp cho ứng dụng trên Tivi.
        </p>
        <label class="drop" for="bgInput">📷 Chọn ảnh từ điện thoại</label>
        <input id="bgInput" type="file" accept="image/*" />
        <img id="bgPreview" alt="Xem trước ảnh" />
        <button class="primary-btn" id="sendBgBtn" disabled onclick="uploadBgFile()">Gửi ảnh nền lên Tivi</button>
        <div id="bgStatus" class="status-msg"></div>
      </div>
    </div>
  </div>

  <!-- STICKY PHONE AUDIO PLAYER -->
  <div class="player-dock" id="playerDock">
    <div class="player-inner">
      <div class="player-top">
        <img id="playerCover" class="player-cover disc" src="" alt="Cover" />
        <div class="player-info">
          <div class="player-title" id="playerTitle">Chưa chọn bài</div>
          <div class="player-artist" id="playerArtist">TV Music</div>
        </div>
        <button class="cast-btn" onclick="playCurrentOnTv()">📺 Phát TV</button>
      </div>
      <div class="progress-row">
        <span id="curTime">00:00</span>
        <input id="seekBar" class="seek" type="range" min="0" max="100" value="0" />
        <span id="totTime">00:00</span>
      </div>
      <div class="controls">
        <button class="ctrl-btn" id="shuffleBtn" title="Xáo trộn" onclick="toggleShuffle()">🔀</button>
        <button class="ctrl-btn" title="Bài trước" onclick="prevTrack()">⏮</button>
        <button class="play-main" id="playPauseBtn" onclick="togglePlay()">▶</button>
        <button class="ctrl-btn" title="Bài tiếp" onclick="nextTrack()">⏭</button>
        <button class="ctrl-btn" id="repeatBtn" title="Lặp lại" onclick="toggleRepeat()">🔁</button>
      </div>
    </div>
  </div>

  <audio id="audioEl" preload="metadata"></audio>

  <script>
    const audioEl = document.getElementById('audioEl');
    let allTracks = [];
    let filteredTracks = [];
    let currentFormat = 'ALL';
    let currentTrack = null;
    let coverStyle = 'SPINNING_DISC';
    let isShuffle = false;
    let isRepeatOne = false;
    let isSeeking = false;
    let selectedBgFile = null;
    let selectedAudioFiles = [];

    function switchTab(tab) {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
      if (tab === 'upload') {
        document.getElementById('tabBtnUpload').classList.add('active');
        document.getElementById('panelUpload').classList.add('active');
      } else if (tab === 'bg') {
        document.getElementById('tabBtnBg').classList.add('active');
        document.getElementById('panelBg').classList.add('active');
      } else {
        document.getElementById('tabBtnPlay').classList.add('active');
        document.getElementById('panelPlay').classList.add('active');
      }
    }

    function setFormat(fmt, el) {
      currentFormat = fmt;
      document.querySelectorAll('#formatChips .chip').forEach(c => c.classList.remove('active'));
      if (el) el.classList.add('active');
      renderTracks();
    }

    async function loadTracks() {
      try {
        const res = await fetch('/api/tracks');
        const data = await res.json();
        allTracks = data.tracks || [];
        coverStyle = data.coverStyle || 'SPINNING_DISC';
        updateTvBanner(data.tvStatus);
        renderTracks();
        updateDockCoverStyle();
      } catch (e) {
        document.getElementById('trackList').innerHTML = '<div class="empty">Không thể tải danh sách nhạc. Kiểm tra kết nối Wi-Fi.</div>';
      }
    }

    async function pollTvStatus() {
      try {
        const res = await fetch('/api/tv/status');
        const data = await res.json();
        updateTvBanner(data.tvStatus);
        if (data.coverStyle && data.coverStyle !== coverStyle) {
          coverStyle = data.coverStyle;
          updateDockCoverStyle();
          renderTracks();
        }
      } catch (e) {}
    }

    function updateTvBanner(st) {
      if (!st) return;
      const titleEl = document.getElementById('tvNowTitle');
      const toggleEl = document.getElementById('tvToggleBtn');
      if (st.trackId) {
        titleEl.textContent = (st.isPlaying ? '▶ ' : '⏸ ') + st.title + ' • ' + st.artist;
      } else {
        titleEl.textContent = 'Chưa phát bài nào trên TV';
      }
      toggleEl.textContent = st.isPlaying ? '⏸' : '▶';
    }

    function escapeHtml(s) {
      return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function renderTracks() {
      const q = (document.getElementById('searchInput').value || '').trim().toLowerCase();
      filteredTracks = allTracks.filter(t => {
        const fmtOk = currentFormat === 'ALL' || (t.format || '').toUpperCase() === currentFormat;
        const qOk = !q || (t.title || '').toLowerCase().includes(q) || (t.artist || '').toLowerCase().includes(q) || (t.album || '').toLowerCase().includes(q);
        return fmtOk && qOk;
      });
      const listEl = document.getElementById('trackList');
      if (filteredTracks.length === 0) {
        listEl.innerHTML = '<div class="empty">Không có bài hát nào. Hãy gửi nhạc từ tab <b>Gửi nhạc lên TV</b> hoặc thêm nhạc vào bộ nhớ TV!</div>';
        return;
      }
      const coverCls = coverStyle === 'ROUNDED_SQUARE' ? 'square' : 'disc';
      listEl.innerHTML = filteredTracks.map(t => {
        const active = currentTrack && currentTrack.id === t.id;
        const spinCls = (active && !audioEl.paused && coverStyle === 'SPINNING_DISC') ? ' spinning' : '';
        return '<div class="track-item ' + (active ? 'playing' : '') + '" onclick="playOnPhone(\'' + encodeURIComponent(t.id) + '\')">'
          + '<img class="cover ' + coverCls + spinCls + '" loading="lazy" src="/api/cover?id=' + encodeURIComponent(t.id) + '" alt="" />'
          + '<div class="track-meta">'
          + '<div class="track-title">' + escapeHtml(t.title) + '</div>'
          + '<div class="track-sub">' + escapeHtml(t.artist) + ' • ' + escapeHtml(t.formattedDuration) + '</div>'
          + '</div>'
          + '<div class="track-actions">'
          + '<span class="badge">' + escapeHtml(t.format) + '</span>'
          + '<button class="cast-btn" onclick="event.stopPropagation(); playOnTv(\'' + encodeURIComponent(t.id) + '\')">📺 TV</button>'
          + '</div></div>';
      }).join('');
    }

    function playOnPhone(encodedId) {
      const id = decodeURIComponent(encodedId);
      const track = allTracks.find(t => t.id === id);
      if (!track) return;
      currentTrack = track;
      audioEl.src = '/api/stream?id=' + encodeURIComponent(track.id);
      audioEl.play().catch(() => {});
      document.getElementById('playerDock').classList.add('visible');
      document.getElementById('playerTitle').textContent = track.title;
      document.getElementById('playerArtist').textContent = track.artist + ' • ' + track.format;
      document.getElementById('playerCover').src = '/api/cover?id=' + encodeURIComponent(track.id);
      updateDockCoverStyle();
      renderTracks();
      if ('mediaSession' in navigator) {
        navigator.mediaSession.metadata = new MediaMetadata({
          title: track.title,
          artist: track.artist,
          album: track.album || 'TV Music',
          artwork: [{ src: '/api/cover?id=' + encodeURIComponent(track.id), sizes: '256x256', type: 'image/jpeg' }]
        });
        navigator.mediaSession.setActionHandler('play', () => audioEl.play());
        navigator.mediaSession.setActionHandler('pause', () => audioEl.pause());
        navigator.mediaSession.setActionHandler('previoustrack', prevTrack);
        navigator.mediaSession.setActionHandler('nexttrack', nextTrack);
      }
    }

    function updateDockCoverStyle() {
      const img = document.getElementById('playerCover');
      img.className = 'player-cover ' + (coverStyle === 'ROUNDED_SQUARE' ? 'square' : 'disc');
      if (!audioEl.paused && coverStyle === 'SPINNING_DISC') {
        img.classList.add('spinning');
      }
    }

    function togglePlay() {
      if (!currentTrack) {
        if (filteredTracks.length > 0) playOnPhone(encodeURIComponent(filteredTracks[0].id));
        return;
      }
      if (audioEl.paused) audioEl.play(); else audioEl.pause();
    }

    function nextTrack() {
      const list = filteredTracks.length > 0 ? filteredTracks : allTracks;
      if (list.length === 0) return;
      if (isShuffle && list.length > 1) {
        const rand = list[Math.floor(Math.random() * list.length)];
        playOnPhone(encodeURIComponent(rand.id));
        return;
      }
      const idx = list.findIndex(t => currentTrack && t.id === currentTrack.id);
      const next = list[(idx + 1) % list.length];
      playOnPhone(encodeURIComponent(next.id));
    }

    function prevTrack() {
      const list = filteredTracks.length > 0 ? filteredTracks : allTracks;
      if (list.length === 0) return;
      if (audioEl.currentTime > 3) { audioEl.currentTime = 0; return; }
      const idx = list.findIndex(t => currentTrack && t.id === currentTrack.id);
      const prev = list[(idx <= 0 ? list.length - 1 : idx - 1)];
      playOnPhone(encodeURIComponent(prev.id));
    }

    function toggleShuffle() {
      isShuffle = !isShuffle;
      document.getElementById('shuffleBtn').classList.toggle('active', isShuffle);
    }

    function toggleRepeat() {
      isRepeatOne = !isRepeatOne;
      document.getElementById('repeatBtn').classList.toggle('active', isRepeatOne);
      document.getElementById('repeatBtn').textContent = isRepeatOne ? '🔂' : '🔁';
    }

    function playCurrentOnTv() {
      if (currentTrack) playOnTv(encodeURIComponent(currentTrack.id));
    }

    async function playOnTv(encodedId) {
      await fetch('/api/tv/play?id=' + encodedId, { method: 'POST' });
      setTimeout(pollTvStatus, 250);
    }

    async function tvControl(action) {
      await fetch('/api/tv/control?action=' + encodeURIComponent(action), { method: 'POST' });
      setTimeout(pollTvStatus, 250);
    }

    function fmtTime(sec) {
      if (!isFinite(sec) || sec < 0) return '00:00';
      const s = Math.floor(sec);
      const m = Math.floor(s / 60);
      const r = s % 60;
      return String(m).padStart(2, '0') + ':' + String(r).padStart(2, '0');
    }

    audioEl.addEventListener('play', () => {
      document.getElementById('playPauseBtn').textContent = '⏸';
      updateDockCoverStyle();
      renderTracks();
    });
    audioEl.addEventListener('pause', () => {
      document.getElementById('playPauseBtn').textContent = '▶';
      updateDockCoverStyle();
      renderTracks();
    });
    audioEl.addEventListener('ended', () => {
      if (isRepeatOne) { audioEl.currentTime = 0; audioEl.play(); } else { nextTrack(); }
    });
    audioEl.addEventListener('timeupdate', () => {
      if (isSeeking) return;
      const dur = audioEl.duration || 0;
      document.getElementById('curTime').textContent = fmtTime(audioEl.currentTime);
      document.getElementById('totTime').textContent = fmtTime(dur);
      const bar = document.getElementById('seekBar');
      bar.value = dur > 0 ? Math.floor((audioEl.currentTime / dur) * 1000) : 0;
      bar.max = 1000;
    });

    const seekBar = document.getElementById('seekBar');
    seekBar.addEventListener('input', () => { isSeeking = true; });
    seekBar.addEventListener('change', () => {
      const dur = audioEl.duration || 0;
      if (dur > 0) audioEl.currentTime = (seekBar.value / 1000) * dur;
      isSeeking = false;
    });

    // Audio Upload logic
    document.getElementById('audioInput').addEventListener('change', (e) => {
      selectedAudioFiles = Array.from(e.target.files || []);
      const infoEl = document.getElementById('audioFileList');
      const btn = document.getElementById('uploadAudioBtn');
      if (selectedAudioFiles.length > 0) {
        infoEl.textContent = 'Đã chọn ' + selectedAudioFiles.length + ' bài: ' + selectedAudioFiles.map(f => f.name).join(', ');
        btn.disabled = false;
      } else {
        infoEl.textContent = '';
        btn.disabled = true;
      }
      document.getElementById('audioStatus').textContent = '';
    });

    function uploadSingleAudio(file, autoplay, onProgress) {
      return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        const url = '/api/upload-audio?filename=' + encodeURIComponent(file.name) + '&autoplay=' + (autoplay ? '1' : '0');
        xhr.open('POST', url, true);
        xhr.upload.onprogress = (ev) => {
          if (ev.lengthComputable && onProgress) onProgress(ev.loaded / ev.total);
        };
        xhr.onload = () => {
          try {
            const json = JSON.parse(xhr.responseText || '{}');
            if (xhr.status >= 200 && xhr.status < 300 && json.ok) resolve(json);
            else reject(new Error(json.error || 'Tải lên thất bại'));
          } catch (err) {
            reject(new Error('Lỗi phản hồi từ TV'));
          }
        };
        xhr.onerror = () => reject(new Error('Mất kết nối với TV'));
        xhr.send(file);
      });
    }

    async function uploadAudioFiles() {
      if (selectedAudioFiles.length === 0) return;
      const btn = document.getElementById('uploadAudioBtn');
      const statusEl = document.getElementById('audioStatus');
      const wrap = document.getElementById('audioProgressWrap');
      const fill = document.getElementById('audioProgressFill');
      const autoplay = document.getElementById('autoplayCheck').checked;
      btn.disabled = true;
      wrap.style.display = 'block';
      fill.style.width = '0%';
      statusEl.className = 'status-msg';
      try {
        for (let i = 0; i < selectedAudioFiles.length; i++) {
          const f = selectedAudioFiles[i];
          statusEl.textContent = 'Đang gửi (' + (i + 1) + '/' + selectedAudioFiles.length + '): ' + f.name;
          await uploadSingleAudio(f, autoplay && i === 0, (ratio) => {
            const overall = ((i + ratio) / selectedAudioFiles.length) * 100;
            fill.style.width = Math.round(overall) + '%';
          });
        }
        statusEl.textContent = '✅ Đã gửi xong ' + selectedAudioFiles.length + ' bài hát lên TV!';
        statusEl.className = 'status-msg ok';
        selectedAudioFiles = [];
        document.getElementById('audioFileList').textContent = '';
        await loadTracks();
      } catch (err) {
        statusEl.textContent = '❌ ' + (err.message || 'Lỗi gửi nhạc');
        statusEl.className = 'status-msg err';
      } finally {
        btn.disabled = selectedAudioFiles.length === 0;
      }
    }

    // Background image upload logic
    document.getElementById('bgInput').addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;
      selectedBgFile = file;
      const preview = document.getElementById('bgPreview');
      preview.src = URL.createObjectURL(file);
      preview.style.display = 'block';
      document.getElementById('sendBgBtn').disabled = false;
      document.getElementById('bgStatus').textContent = '';
    });

    async function uploadBgFile() {
      if (!selectedBgFile) return;
      const btn = document.getElementById('sendBgBtn');
      const statusEl = document.getElementById('bgStatus');
      btn.disabled = true;
      btn.textContent = 'Đang gửi ảnh lên Tivi...';
      try {
        const res = await fetch('/upload', {
          method: 'POST',
          headers: { 'Content-Type': selectedBgFile.type || 'application/octet-stream' },
          body: selectedBgFile
        });
        const data = await res.json();
        if (res.ok && data.ok) {
          statusEl.textContent = '✅ ' + data.message;
          statusEl.className = 'status-msg ok';
        } else {
          statusEl.textContent = '❌ ' + (data.error || 'Tải lên thất bại');
          statusEl.className = 'status-msg err';
        }
      } catch (err) {
        statusEl.textContent = '❌ Mất kết nối với Tivi. Kiểm tra lại Wi-Fi.';
        statusEl.className = 'status-msg err';
      } finally {
        btn.disabled = false;
        btn.textContent = 'Gửi ảnh nền lên Tivi';
      }
    }

    // Read initial tab from query string (?tab=bg | ?tab=upload | ?tab=play)
    const initTab = new URLSearchParams(window.location.search).get('tab');
    if (initTab === 'bg' || initTab === 'upload' || initTab === 'play') {
      switchTab(initTab);
    }
    loadTracks();
    setInterval(pollTvStatus, 3000);
  </script>
</body>
</html>
""".trimIndent()
}
