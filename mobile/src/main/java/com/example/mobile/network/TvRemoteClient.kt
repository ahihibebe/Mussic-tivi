package com.example.mobile.network

import com.example.mobile.model.MobileAudioTrack
import com.example.mobile.server.MobileHttpMusicServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DiscoveredTvDevice(
    val url: String,
    val deviceName: String,
    val nowPlayingTitle: String = "",
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class TvRemoteStatus(
    val connected: Boolean = false,
    val tvUrl: String = "",
    val deviceName: String = "Tivi",
    val trackId: String? = null,
    val title: String = "Chưa phát bài nào",
    val artist: String = "TV Music",
    val format: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: String = "ALL"
)

object TvRemoteClient {

    fun normalizeTvUrl(rawInput: String): String {
        val trimmed = rawInput.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return ""
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val hostPart = withScheme.substringAfter("://").substringBefore("/")
        return if (hostPart.contains(":")) {
            withScheme
        } else {
            "$withScheme:${MobileHttpMusicServer.DEFAULT_HTTP_PORT}"
        }
    }

    fun parseTvDiscoveryPacket(message: String): DiscoveredTvDevice? {
        if (!message.startsWith("TVMUSIC_DISCOVER|")) return null
        val parts = message.split("|")
        if (parts.size < 6) return null
        val role = parts[5].trim()
        if (role != "TV_PLAYER" && role != "TV_HUB") return null
        val url = normalizeTvUrl(parts[2])
        if (url.isBlank()) return null
        val deviceName = parts[3].ifBlank { "Android TV" }
        return DiscoveredTvDevice(
            url = url,
            deviceName = deviceName
        )
    }

    suspend fun listenTvUdpBeacons(
        ownIp: String?,
        onTvDiscovered: (DiscoveredTvDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(MobileHttpMusicServer.DISCOVERY_UDP_PORT).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 2000
            }
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val senderIp = packet.address?.hostAddress
                    if (ownIp != null && senderIp == ownIp) continue
                    val msg = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    val parsed = parseTvDiscoveryPacket(msg)
                    if (parsed != null && (ownIp == null || !parsed.url.contains("://$ownIp:"))) {
                        onTvDiscovered(parsed)
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun scanSubnetForTvs(
        ownIp: String? = MobileHttpMusicServer.getLocalIpv4Address(),
        port: Int = MobileHttpMusicServer.DEFAULT_HTTP_PORT
    ): List<DiscoveredTvDevice> = withContext(Dispatchers.IO) {
        val ip = ownIp ?: return@withContext emptyList()
        val parts = ip.split(".")
        if (parts.size != 4) return@withContext emptyList()
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
        val ownOctet = parts[3].toIntOrNull()
        val semaphore = Semaphore(32)

        coroutineScope {
            (1..254).map { host ->
                async {
                    if (host == ownOctet) return@async null
                    semaphore.withPermit {
                        probeTvServer("http://$prefix$host:$port", connectTimeoutMs = 300, readTimeoutMs = 450)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun probeTvServer(
        rawUrl: String,
        connectTimeoutMs: Int = 1500,
        readTimeoutMs: Int = 2000
    ): DiscoveredTvDevice? = withContext(Dispatchers.IO) {
        val baseUrl = normalizeTvUrl(rawUrl)
        if (baseUrl.isBlank()) return@withContext null
        try {
            val conn = (URL("$baseUrl/api/server-info").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val obj = JSONObject(body)
            if (obj.optString("app") != "TVMusic") return@withContext null
            val role = obj.optString("role", "")
            if (role != "TV_PLAYER" && role != "TV_HUB") return@withContext null

            val name = obj.optString("deviceName", "").ifBlank { baseUrl.substringAfter("://") }
            val nowPlaying = obj.optString("nowPlayingTitle", "")
            DiscoveredTvDevice(
                url = baseUrl,
                deviceName = name,
                nowPlayingTitle = nowPlaying
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchTvStatus(tvUrl: String): TvRemoteStatus? = withContext(Dispatchers.IO) {
        val baseUrl = normalizeTvUrl(tvUrl)
        if (baseUrl.isBlank()) return@withContext null
        try {
            val conn = (URL("$baseUrl/api/tv/status").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2000
                readTimeout = 2500
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val root = JSONObject(body)
            val statusObj = root.optJSONObject("tvStatus") ?: return@withContext null
            TvRemoteStatus(
                connected = true,
                tvUrl = baseUrl,
                deviceName = statusObj.optString("deviceName", "").ifBlank {
                    baseUrl.substringAfter("://").substringBefore(":")
                },
                trackId = statusObj.optString("trackId", "").takeIf { it.isNotBlank() && it != "null" },
                title = statusObj.optString("title", "Chưa phát bài nào"),
                artist = statusObj.optString("artist", "TV Music"),
                format = statusObj.optString("format", ""),
                isPlaying = statusObj.optBoolean("isPlaying", false),
                positionMs = statusObj.optLong("positionMs", 0L),
                durationMs = statusObj.optLong("durationMs", 0L),
                isShuffle = statusObj.optBoolean("isShuffle", false),
                repeatMode = statusObj.optString("repeatMode", "ALL")
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun playTrackOnTv(
        tvUrl: String,
        phoneServerUrl: String,
        track: MobileAudioTrack,
        queueTracks: List<MobileAudioTrack>
    ): Result<String> = withContext(Dispatchers.IO) {
        val normalizedTv = normalizeTvUrl(tvUrl)
        val normalizedPhone = phoneServerUrl.trimEnd('/')
        if (normalizedTv.isBlank() || normalizedPhone.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Thiếu địa chỉ TV hoặc Server điện thoại"))
        }

        try {
            fun buildTrackJson(t: MobileAudioTrack): JSONObject {
                val encodedId = URLEncoder.encode(t.id, "UTF-8")
                return JSONObject().apply {
                    put("id", "cast_${normalizedPhone.hashCode()}_${t.id}")
                    put("streamUrl", "$normalizedPhone/api/stream?id=$encodedId")
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("format", t.format)
                    put("bitrate", t.bitrate)
                    put("durationMs", t.durationMs)
                    if (!t.albumArtUri.isNullOrBlank()) {
                        put("coverUrl", "$normalizedPhone/api/cover?id=$encodedId")
                    }
                }
            }

            // Include up to 50 tracks around the selected track for seamless Next/Prev on TV
            val selectedIndex = queueTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val start = (selectedIndex - 10).coerceAtLeast(0)
            val end = (start + 50).coerceAtMost(queueTracks.size)
            val queueSlice = if (queueTracks.isNotEmpty()) queueTracks.subList(start, end) else listOf(track)

            val payload = buildTrackJson(track).apply {
                val queueArr = JSONArray()
                queueSlice.forEach { queueArr.put(buildTrackJson(it)) }
                put("queue", queueArr)
            }

            val conn = (URL("$normalizedTv/api/tv/play-url").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 3000
                readTimeout = 4000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }
            val code = conn.responseCode
            val msg = runCatching {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            conn.disconnect()

            if (code in 200..299) {
                Result.success("Đang phát trên TV: ${track.title}")
            } else {
                Result.failure(IllegalStateException("Tivi báo lỗi HTTP $code: $msg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendTvControl(
        tvUrl: String,
        action: String,
        positionMs: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val normalizedTv = normalizeTvUrl(tvUrl)
        if (normalizedTv.isBlank()) return@withContext false
        try {
            val query = buildString {
                append("action=").append(URLEncoder.encode(action, "UTF-8"))
                if (positionMs != null) {
                    append("&positionMs=").append(positionMs)
                }
            }
            val conn = (URL("$normalizedTv/api/tv/control?$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 2000
                readTimeout = 2500
            }
            conn.outputStream.use { }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendWebRtcOfferToTv(
        tvUrl: String,
        offerToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val normalizedTv = normalizeTvUrl(tvUrl)
        if (normalizedTv.isBlank() || offerToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Thiếu địa chỉ TV hoặc mã Offer"))
        }
        try {
            val payload = JSONObject().put("offer", offerToken)
            val conn = (URL("$normalizedTv/api/webrtc/offer").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }
            val code = conn.responseCode
            val body = runCatching {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            conn.disconnect()
            if (code in 200..299) {
                val ans = JSONObject(body).optString("answer", "")
                if (ans.isNotBlank()) {
                    Result.success(ans)
                } else {
                    Result.failure(IllegalStateException("Tivi không trả về mã Answer"))
                }
            } else {
                Result.failure(IllegalStateException("Tivi trả lỗi HTTP $code: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
