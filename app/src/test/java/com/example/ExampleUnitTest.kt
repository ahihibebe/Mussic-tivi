package com.example

import com.example.model.AppUiStyle
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.model.TvNavTab
import com.example.qr.LocalBackgroundServer
import com.example.qr.TvPlaybackSnapshot
import com.example.ui.theme.buildTvStyleTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
