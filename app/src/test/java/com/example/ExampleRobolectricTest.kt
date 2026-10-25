package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AudioTrack
import com.example.model.EqualizerPreset
import com.example.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
