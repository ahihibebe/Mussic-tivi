package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MusicRepository
import com.example.model.AppUiStyle
import com.example.model.AudioTrack
import com.example.model.EqualizerPreset
import com.example.model.RepeatMode
import com.example.qr.QrCodeGenerator
import com.example.ui.theme.buildTvStyleTokens
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TV Music", appName)
  }

  @Test
  fun `audio track model formatting test`() {
    val track = AudioTrack(
      id = "test_1",
      title = "Test Song",
      artist = "Test Artist",
      album = "Test Album",
      durationMs = 185000L, // 3:05
      filePath = "/storage/emulated/0/Music/song.flac",
      format = "FLAC",
      bitrate = "Lossless",
      sampleRate = "96.0 kHz",
      fileSizeBytes = 25 * 1024 * 1024L
    )

    assertEquals("03:05", track.formattedDuration)
    assertEquals("25.0 MB", track.formattedSize)
    assertEquals(RepeatMode.ALL.label, "Lặp tất cả")
    assertNotNull(EqualizerPreset.BASS_BOOST)
  }

  @Test
  fun `apple tv glass and light style tokens test`() {
    val light = buildTvStyleTokens(AppUiStyle.CLASSIC_LIGHT, hasCustomBackground = false)
    val lightWithBg = buildTvStyleTokens(AppUiStyle.CLASSIC_LIGHT, hasCustomBackground = true)
    val glass = buildTvStyleTokens(AppUiStyle.APPLE_TV_GLASS, hasCustomBackground = false)

    assertFalse(light.isGlassStyle)
    assertTrue(lightWithBg.cardSurface.alpha < 1.0f)
    assertTrue(glass.isGlassStyle)
    assertTrue(glass.cardSurface.alpha < 0.7f)
  }

  @Test
  fun `repository style and background opacity persistence test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = MusicRepository(context)

    repo.setUiStyle(AppUiStyle.APPLE_TV_GLASS)
    assertEquals(AppUiStyle.APPLE_TV_GLASS, repo.uiStyle.value)

    repo.setBackgroundOpacity(1.5f)
    assertEquals(1.0f, repo.backgroundOpacity.value, 0.001f)

    repo.setBackgroundOpacity(-0.3f)
    assertEquals(0.0f, repo.backgroundOpacity.value, 0.001f)

    repo.setBackgroundOpacity(0.45f)
    assertEquals(0.45f, repo.backgroundOpacity.value, 0.001f)
  }

  @Test
  fun `qr code bitmap generator creates valid bitmap`() = runBlocking {
    val qr = QrCodeGenerator.generateQrBitmap("http://192.168.1.50:8765", 256)
    assertNotNull(qr)
    assertEquals(256, qr!!.width)
    assertEquals(256, qr.height)
  }
}
