package com.example

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.example.audio.CleanHiResPcmAudioProcessor
import com.example.model.AppUiStyle
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.model.TvNavTab
import com.example.model.WifiMusicRole
import com.example.qr.LanMusicDiscoveryClient
import com.example.qr.LocalBackgroundServer
import com.example.qr.TvPlaybackSnapshot
import com.example.ui.theme.buildTvStyleTokens
import com.example.webrtc.TvWebRtcManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun appleTvGlassAndLightTokensAreConfiguredCorrectly() {
    val light = buildTvStyleTokens(AppUiStyle.CLASSIC_LIGHT, hasCustomBackground = false)
    val lightWithBg = buildTvStyleTokens(AppUiStyle.CLASSIC_LIGHT, hasCustomBackground = true)
    val glass = buildTvStyleTokens(AppUiStyle.APPLE_TV_GLASS, hasCustomBackground = false)

    assertFalse(light.isGlassStyle)
    assertTrue(lightWithBg.cardSurface.alpha < 1.0f)
    assertTrue(glass.isGlassStyle)
    assertTrue(glass.cardSurface.alpha < 0.7f)
  }

  @Test
  fun coverArtStylesContainRoundedSquareAndSpinningDisc() {
    val styles = CoverArtStyle.entries
    assertEquals(2, styles.size)
    assertTrue(styles.contains(CoverArtStyle.ROUNDED_SQUARE))
    assertTrue(styles.contains(CoverArtStyle.SPINNING_DISC))
    assertTrue(CoverArtStyle.ROUNDED_SQUARE.title.contains("vuông", ignoreCase = true))
    assertTrue(CoverArtStyle.SPINNING_DISC.title.contains("xoay", ignoreCase = true))
  }

  @Test
  fun audioTrackFormatsSizeDurationAndCoverUriCorrectly() {
    val track = AudioTrack(
      id = "unit_1",
      title = "Lossless Demo",
      artist = "Studio",
      album = "Master",
      durationMs = 125000L,
      filePath = "/music/demo.flac",
      format = "FLAC",
      bitrate = "Lossless",
      sampleRate = "96.0 kHz",
      fileSizeBytes = 10 * 1024 * 1024L,
      albumArtUri = "/cache/album_art/cover.jpg"
    )

    assertEquals("02:05", track.formattedDuration)
    assertEquals("10.0 MB", track.formattedSize)
    assertEquals("/cache/album_art/cover.jpg", track.albumArtUri)
  }

  @Test
  fun byteRangeParserHandlesValidAndInvalidRanges() {
    assertEquals(0L..999L, LocalBackgroundServer.parseByteRange("bytes=0-", 1000L))
    assertEquals(100L..499L, LocalBackgroundServer.parseByteRange("bytes=100-499", 1000L))
    assertEquals(100L..999L, LocalBackgroundServer.parseByteRange("bytes=100-5000", 1000L))
    assertEquals(800L..999L, LocalBackgroundServer.parseByteRange("bytes=-200", 1000L))
    assertNull(LocalBackgroundServer.parseByteRange("bytes=1000-", 1000L))
    assertNull(LocalBackgroundServer.parseByteRange("bytes=500-200", 1000L))
    assertNull(LocalBackgroundServer.parseByteRange(null, 1000L))
  }

  @Test
  fun queryParamsAndJsonBuilderWorkForWifiMusicApi() {
    assertTrue(TvNavTab.entries.contains(TvNavTab.WIFI_PHONE))
    val params = LocalBackgroundServer.parseQueryParams("filename=B%C3%A0i+h%C3%A1t+01.flac&autoplay=1")
    assertEquals("Bài hát 01.flac", params["filename"])
    assertEquals("1", params["autoplay"])

    val sanitized = LocalBackgroundServer.sanitizeAudioFilename("../path/Nhạc \"Hay\".mp3")
    assertFalse(sanitized.contains("/"))
    assertTrue(sanitized.endsWith(".mp3"))

    val track = AudioTrack(
      id = "ms_42",
      title = "Track \"Quote\"",
      artist = "Artist\\Test",
      album = "Album",
      durationMs = 60000L,
      filePath = "/music/song.flac",
      format = "FLAC",
      albumArtUri = "/cache/art.jpg"
    )
    val json = LocalBackgroundServer.buildTracksJson(
      tracks = listOf(track),
      coverArtStyle = CoverArtStyle.ROUNDED_SQUARE,
      tvStatus = TvPlaybackSnapshot(trackId = "ms_42", title = "Track \"Quote\"", isPlaying = true)
    )
    assertTrue(json.contains("\"coverStyle\":\"ROUNDED_SQUARE\""))
    assertTrue(json.contains("\"id\":\"ms_42\""))
    assertTrue(json.contains("\\\"Quote\\\""))
    assertTrue(json.contains("\"hasCover\":true"))
  }

  @Test
  fun phoneServerDiscoveryAndRemoteTrackParsingWorkWithoutFileUpload() {
    assertEquals(3, WifiMusicRole.entries.size)
    assertEquals("http://192.168.1.25:8765", LanMusicDiscoveryClient.normalizeServerUrl("192.168.1.25"))
    assertEquals("http://192.168.1.25:9000", LanMusicDiscoveryClient.normalizeServerUrl("http://192.168.1.25:9000/"))

    val discovered = LanMusicDiscoveryClient.parseDiscoveryPacket(
      "TVMUSIC_DISCOVER|1|http://192.168.1.25:8765|Galaxy S24|88|PHONE_SERVER|8765"
    )
    assertNotNull(discovered)
    assertEquals("Galaxy S24", discovered!!.deviceName)
    assertEquals(88, discovered.tracksCount)
    assertEquals("http://192.168.1.25:8765", discovered.url)

    val sourceTrack = AudioTrack(
      id = "ms_99",
      title = "Acoustic Live",
      artist = "Phone Artist",
      album = "Phone Album",
      durationMs = 210000L,
      filePath = "/storage/emulated/0/Music/live.flac",
      format = "FLAC",
      bitrate = "Lossless",
      albumArtUri = "/cache/art.jpg"
    )
    val payload = LocalBackgroundServer.buildTracksJson(
      tracks = listOf(sourceTrack),
      coverArtStyle = CoverArtStyle.SPINNING_DISC,
      tvStatus = TvPlaybackSnapshot()
    )
    val remoteTracks = LanMusicDiscoveryClient.parseTracksPayload(
      json = payload,
      serverUrl = "192.168.1.25",
      serverName = "Galaxy S24"
    )
    assertEquals(1, remoteTracks.size)
    val remote = remoteTracks.first()
    assertEquals("Acoustic Live", remote.title)
    assertEquals("http://192.168.1.25:8765/api/stream?id=ms_99", remote.contentUri)
    assertEquals("http://192.168.1.25:8765/api/cover?id=ms_99", remote.albumArtUri)
  }

  @Test
  fun cleanHiResPcmProcessorConverts24Bit48KhzTo16BitCleanly() {
    val processor = CleanHiResPcmAudioProcessor()
    val inFormat = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_24BIT)
    val outFormat = processor.configure(inFormat)
    processor.flush()

    assertTrue(processor.isActive)
    assertEquals(48000, outFormat.sampleRate)
    assertEquals(2, outFormat.channelCount)
    assertEquals(C.ENCODING_PCM_16BIT, outFormat.encoding)

    // Two 24-bit little-endian samples: +max (0x7FFFFF) and -half (0xC00000)
    val input = ByteBuffer.allocateDirect(6).order(ByteOrder.nativeOrder()).apply {
      put(0xFF.toByte()); put(0xFF.toByte()); put(0x7F.toByte())
      put(0x00.toByte()); put(0x00.toByte()); put(0xC0.toByte())
      flip()
    }
    processor.queueInput(input)
    val output = processor.output
    assertEquals(4, output.remaining())
    assertEquals(32767.toShort(), output.short)
    assertEquals((-16384).toShort(), output.short)

    // Unaligned chunk boundary: 4 bytes first (1 sample + 1 leftover byte), then 2 bytes
    val chunk1 = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).apply {
      put(0x00.toByte()); put(0x00.toByte()); put(0x40.toByte())
      put(0xFF.toByte())
      flip()
    }
    processor.queueInput(chunk1)
    assertEquals(0, chunk1.remaining())
    val out1 = processor.output
    assertEquals(2, out1.remaining())
    assertEquals(16384.toShort(), out1.short)

    val chunk2 = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder()).apply {
      put(0xFF.toByte()); put(0x7F.toByte())
      flip()
    }
    processor.queueInput(chunk2)
    assertEquals(0, chunk2.remaining())
    val out2 = processor.output
    assertEquals(2, out2.remaining())
    assertEquals(32767.toShort(), out2.short)

    assertTrue(TvNavTab.entries.contains(TvNavTab.BACKGROUND_PLAY))
  }

  @Test
  fun tvWebRtcGoogleStunAndSrflxCandidateParsingWork() {
    assertEquals("stun:stun.l.google.com:19302", TvWebRtcManager.PRIMARY_GOOGLE_STUN)
    assertTrue(TvWebRtcManager.GOOGLE_STUN_SERVERS.contains("stun:stun.l.google.com:19302"))
    assertTrue(TvWebRtcManager.GOOGLE_STUN_SERVERS.contains("stun:stun1.l.google.com:19302"))

    val srflxCandidate =
      "candidate:842163049 1 udp 1686052607 203.0.113.45 54321 typ srflx raddr 192.168.1.10 rport 54321 generation 0"
    assertEquals("203.0.113.45:54321", TvWebRtcManager.extractSrflxEndpoint(srflxCandidate))

    val hostCandidate =
      "candidate:12345 1 udp 2122260223 192.168.1.10 50000 typ host generation 0"
    assertNull(TvWebRtcManager.extractSrflxEndpoint(hostCandidate))

    val sample = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\na=ice-ufrag:abcd".toByteArray()
    val encoded = TvWebRtcManager.encodeBase64UrlNoPad(sample)
    assertFalse(encoded.contains("="))
    assertFalse(encoded.contains("+"))
    assertFalse(encoded.contains("/"))
    assertEquals(String(sample), String(TvWebRtcManager.decodeBase64UrlNoPad(encoded)))
  }
}
