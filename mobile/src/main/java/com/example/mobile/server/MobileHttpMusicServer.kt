package com.example.mobile.server

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.mobile.model.MobileAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
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

class MobileHttpMusicServer(
    private val context: Context,
    private val getTracks: () -> List<MobileAudioTrack>,
    private val onStatusUpdate: (String) -> Unit,
    private val onClientStreamed: (String) -> Unit
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

    val deviceDisplayName: String
        get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            .replace("|", " ")
            .trim()
            .ifBlank { "Android Phone" }

    @Synchronized
    fun start(): String {
        val existing = serverUrl
        if (isRunning && existing != null) {
            return existing
        }
        stop()

        val ip = getLocalIpv4Address() ?: "127.0.0.1"
        val socket = try {
            ServerSocket(DEFAULT_HTTP_PORT)
        } catch (e: Exception) {
            ServerSocket(0)
        }
        serverSocket = socket
        val port = socket.localPort
        val url = "http://$ip:$port"
        serverUrl = url

        onStatusUpdate("Đang làm Server nhạc tại $url — Tivi cùng Wi-Fi có thể quét trực tiếp")

        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch { handleClient(client) }
                } catch (e: Exception) {
                    if (!socket.isClosed) {
                        Log.e("MobileHttpMusicServer", "Accept error", e)
                    }
                }
            }
        }

        broadcastJob = scope.launch {
            while (isActive && !socket.isClosed) {
                sendDiscoveryBeacon(url, port, getTracks().size)
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
        onStatusUpdate("Server nhạc Wi-Fi đang tắt")
    }

    private fun sendDiscoveryBeacon(url: String, port: Int, tracksCount: Int) {
        try {
            val payload = "TVMUSIC_DISCOVER|1|$url|$deviceDisplayName|$tracksCount|PHONE_SERVER|$port"
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            DatagramSocket().use { udp ->
                udp.broadcast = true
                udp.send(
                    DatagramPacket(
                        bytes,
                        bytes.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_UDP_PORT
                    )
                )
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
                        headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] =
                            line.substring(colon + 1).trim()
                    }
                }

                when {
                    method == "OPTIONS" -> {
                        sendResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                    }

                    method == "GET" && (path == "/" || path == "/index.html") -> {
                        val html = buildBrowserPreviewHtml(getTracks().size, serverUrl ?: "")
                        sendResponse(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))
                    }

                    method == "GET" && path == "/api/server-info" -> {
                        val port = serverSocket?.localPort ?: DEFAULT_HTTP_PORT
                        val json = """{"app":"TVMusic","deviceName":"${escapeJson(deviceDisplayName)}","role":"PHONE_SERVER","tracksCount":${getTracks().size},"port":$port,"url":"${escapeJson(serverUrl ?: "")}"}"""
                        sendResponse(output, 200, "OK", "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
                    }

                    method == "GET" && path == "/api/tracks" -> {
                        val tracks = getTracks()
                        onClientStreamed("Tivi vừa quét ${tracks.size} bài hát từ điện thoại")
                        val json = buildTracksJson(tracks)
                        sendResponse(output, 200, "OK", "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
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
                            sendResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Track not found".toByteArray())
                        } else {
                            if (method == "GET") {
                                onClientStreamed("Tivi đang phát trực tiếp: ${track.title}")
                            }
                            socket.soTimeout = 0
                            handleAudioStream(output, track, headers["range"], method == "HEAD")
                        }
                    }

                    else -> {
                        sendResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found".toByteArray())
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun handleCoverRequest(output: OutputStream, track: MobileAudioTrack?) {
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
        sendResponse(output, 404, "Not Found", "text/plain", ByteArray(0))
    }

    private fun handleAudioStream(
        output: OutputStream,
        track: MobileAudioTrack,
        rangeHeader: String?,
        headOnly: Boolean
    ) {
        val mime = guessAudioMime(track.format)
        val file = if (track.filePath.isNotBlank()) File(track.filePath) else null
        val openedRaf = if (file != null && file.exists() && file.canRead() && file.length() > 0L) {
            try {
                RandomAccessFile(file, "r")
            } catch (ignored: Exception) {
                null
            }
        } else {
            null
        }

        if (openedRaf != null) {
            openedRaf.use { raf ->
                val totalLength = raf.length()
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
                output.flush()
            }
            return
        }

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

        sendResponse(output, 404, "Not Found", "text/plain", ByteArray(0))
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
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                val len = sb.length
                if (len > 0 && sb[len - 1] == '\r') sb.deleteCharAt(len - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
        }
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
            extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    private fun buildTracksJson(tracks: List<MobileAudioTrack>): String {
        val sb = StringBuilder()
        sb.append("{\"coverStyle\":\"SPINNING_DISC\",\"tracks\":[")
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

    private fun buildBrowserPreviewHtml(tracksCount: Int, url: String): String = """
        <!DOCTYPE html>
        <html lang="vi">
        <head><meta charset="utf-8" /><meta name="viewport" content="width=device-width,initial-scale=1" />
        <title>Mobile Server WiFi</title>
        <style>body{margin:0;padding:24px;font-family:sans-serif;background:#0f172a;color:#f8fafc;}
        .box{max-width:440px;margin:0 auto;background:#1e293b;padding:22px;border-radius:16px;border:1px solid #334155;}
        h1{margin:0 0 8px;font-size:20px;color:#38bdf8;}p{color:#cbd5e1;font-size:14px;line-height:1.5;}</style></head>
        <body><div class="box"><h1>Mobile Server WiFi</h1>
        <p>Thiết bị <b>${escapeJson(deviceDisplayName)}</b> đang chia sẻ <b>$tracksCount bài hát</b> tại <b>$url</b>.</p>
        <p>Mở ứng dụng <b>TV Music</b> trên Tivi -> tab <b>Phát qua Wi-Fi</b> -> chọn <b>Lấy nhạc trực tiếp từ ĐT</b> để phát ngay.</p>
        </div></body></html>
    """.trimIndent()

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

        fun parseQueryParams(queryString: String): Map<String, String> {
            if (queryString.isBlank()) return emptyMap()
            val result = mutableMapOf<String, String>()
            for (pair in queryString.split("&")) {
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    result[tryDecode(pair.substring(0, eq))] = tryDecode(pair.substring(eq + 1))
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
                (totalLength - suffix).coerceAtLeast(0L)..(totalLength - 1L)
            } else {
                val start = startPart.toLongOrNull() ?: return null
                if (start < 0L || start >= totalLength) return null
                val end = if (endPart.isEmpty()) totalLength - 1L else (endPart.toLongOrNull() ?: return null).coerceAtMost(totalLength - 1L)
                if (end < start) return null
                start..end
            }
        }

        fun guessAudioMime(format: String): String {
            return when (format.uppercase(Locale.ROOT)) {
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
                    else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
                }
            }
            return sb.toString()
        }
    }
}
