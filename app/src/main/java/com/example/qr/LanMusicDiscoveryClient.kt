package com.example.qr

import com.example.model.AudioTrack
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DiscoveredPhoneServer(
    val url: String,
    val deviceName: String,
    val tracksCount: Int,
    val role: String = "PHONE_SERVER",
    val lastSeenMs: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = false)
data class RemoteServerInfoDto(
    val app: String? = null,
    val deviceName: String? = null,
    val role: String? = null,
    val tracksCount: Int = 0,
    val port: Int = LocalBackgroundServer.DEFAULT_HTTP_PORT,
    val url: String? = null
)

@JsonClass(generateAdapter = false)
data class RemoteTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "Nghệ sĩ chưa rõ",
    val album: String = "Điện thoại",
    val durationMs: Long = 0L,
    val format: String = "MP3",
    val bitrate: String = "Wi-Fi Stream",
    val hasCover: Boolean = false
)

@JsonClass(generateAdapter = false)
data class RemoteTracksResponseDto(
    val coverStyle: String? = null,
    val tracks: List<RemoteTrackDto> = emptyList()
)

data class RemoteLibraryResult(
    val server: DiscoveredPhoneServer,
    val tracks: List<AudioTrack>
)

object LanMusicDiscoveryClient {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val serverInfoAdapter by lazy { moshi.adapter(RemoteServerInfoDto::class.java) }
    private val tracksResponseAdapter by lazy { moshi.adapter(RemoteTracksResponseDto::class.java) }

    fun normalizeServerUrl(rawInput: String): String {
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
            "$withScheme:${LocalBackgroundServer.DEFAULT_HTTP_PORT}"
        }
    }

    fun parseDiscoveryPacket(message: String): DiscoveredPhoneServer? {
        if (!message.startsWith("TVMUSIC_DISCOVER|")) return null
        val parts = message.split("|")
        if (parts.size < 6) return null
        val url = normalizeServerUrl(parts[2])
        if (url.isBlank()) return null
        val deviceName = parts[3].ifBlank { "Điện thoại" }
        val tracksCount = parts[4].toIntOrNull() ?: 0
        val role = parts[5].ifBlank { "PHONE_SERVER" }
        return DiscoveredPhoneServer(
            url = url,
            deviceName = deviceName,
            tracksCount = tracksCount,
            role = role
        )
    }

    fun parseTracksPayload(
        json: String,
        serverUrl: String,
        serverName: String
    ): List<AudioTrack> {
        val normalized = normalizeServerUrl(serverUrl)
        val parsed = tracksResponseAdapter.fromJson(json) ?: return emptyList()
        return parsed.tracks.mapNotNull { dto ->
            if (dto.id.isBlank()) return@mapNotNull null
            val encodedId = URLEncoder.encode(dto.id, "UTF-8")
            val streamUrl = "$normalized/api/stream?id=$encodedId"
            val coverUrl = if (dto.hasCover) "$normalized/api/cover?id=$encodedId" else null
            AudioTrack(
                id = "remote_${normalized.hashCode()}_${dto.id}",
                title = dto.title.ifBlank { "Bài hát từ điện thoại" },
                artist = dto.artist.ifBlank { "Nghệ sĩ chưa rõ" },
                album = dto.album.ifBlank { serverName },
                durationMs = dto.durationMs.coerceAtLeast(0L),
                filePath = streamUrl,
                contentUri = streamUrl,
                format = dto.format.ifBlank { "MP3" },
                bitrate = dto.bitrate.ifBlank { "Wi-Fi Stream" },
                sampleRate = "Wi-Fi Direct",
                fileSizeBytes = 0L,
                parentDirectory = "Wi-Fi: $serverName",
                albumArtUri = coverUrl
            )
        }
    }

    suspend fun listenUdpBeacons(
        ownIp: String?,
        onServerDiscovered: (DiscoveredPhoneServer) -> Unit
    ) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(LocalBackgroundServer.DISCOVERY_UDP_PORT).apply {
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
                    val parsed = parseDiscoveryPacket(msg)
                    if (parsed != null && (ownIp == null || !parsed.url.contains("://$ownIp:"))) {
                        onServerDiscovered(parsed)
                    }
                } catch (ignored: Exception) {
                    // Timeout every 2s allows coroutine cancellation check
                }
            }
        } catch (ignored: Exception) {
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    suspend fun scanSubnetForPhoneServers(
        ownIp: String? = LocalBackgroundServer.getLocalIpv4Address(),
        port: Int = LocalBackgroundServer.DEFAULT_HTTP_PORT
    ): List<DiscoveredPhoneServer> = withContext(Dispatchers.IO) {
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
                        val candidateUrl = "http://$prefix$host:$port"
                        probeServerInfo(candidateUrl, connectTimeoutMs = 320, readTimeoutMs = 450)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun probeServerInfo(
        rawUrl: String,
        connectTimeoutMs: Int = 1500,
        readTimeoutMs: Int = 2000
    ): DiscoveredPhoneServer? = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawUrl)
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
            val dto = serverInfoAdapter.fromJson(body) ?: return@withContext null
            if (dto.app != "TVMusic") return@withContext null
            DiscoveredPhoneServer(
                url = baseUrl,
                deviceName = dto.deviceName?.ifBlank { null } ?: baseUrl.substringAfter("://"),
                tracksCount = dto.tracksCount,
                role = dto.role ?: "PHONE_SERVER"
            )
        } catch (ignored: Exception) {
            null
        }
    }

    suspend fun fetchRemoteLibrary(rawUrl: String): RemoteLibraryResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeServerUrl(rawUrl)
        require(baseUrl.isNotBlank()) { "Địa chỉ IP không hợp lệ" }

        val serverInfo = probeServerInfo(baseUrl, connectTimeoutMs = 2500, readTimeoutMs = 3000)
        val serverName = serverInfo?.deviceName ?: baseUrl.substringAfter("://").substringBefore(":")

        val conn = (URL("$baseUrl/api/tracks").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 10000
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("Máy chủ phản hồi lỗi HTTP $code")
        }
        val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        conn.disconnect()

        val tracks = parseTracksPayload(body, baseUrl, serverName)
        val resolvedServer = DiscoveredPhoneServer(
            url = baseUrl,
            deviceName = serverName,
            tracksCount = tracks.size,
            role = serverInfo?.role ?: "PHONE_SERVER"
        )
        RemoteLibraryResult(server = resolvedServer, tracks = tracks)
    }
}
