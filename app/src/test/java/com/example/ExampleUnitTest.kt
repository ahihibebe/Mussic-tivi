package com.example

import com.example.model.AppUiStyle
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.ui.theme.buildTvStyleTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
