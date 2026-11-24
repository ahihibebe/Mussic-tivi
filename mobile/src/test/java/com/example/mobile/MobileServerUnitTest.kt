package com.example.mobile

import com.example.mobile.model.MobileAudioTrack
import com.example.mobile.server.MobileHttpMusicServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileServerUnitTest {

    @Test
    fun formattedDuration_formatsMinutesAndSeconds() {
        val track = MobileAudioTrack(
            id = "1",
            title = "Test",
            durationMs = 195_000L,
            filePath = "/tmp/test.flac",
            format = "FLAC"
        )
        assertEquals("03:15", track.formattedDuration)
    }

    @Test
    fun parseByteRange_handlesValidAndInvalidRanges() {
        assertEquals(0L..999L, MobileHttpMusicServer.parseByteRange("bytes=0-", 1000L))
        assertEquals(100L..499L, MobileHttpMusicServer.parseByteRange("bytes=100-499", 1000L))
        assertEquals(800L..999L, MobileHttpMusicServer.parseByteRange("bytes=-200", 1000L))
        assertNull(MobileHttpMusicServer.parseByteRange("bytes=1200-1500", 1000L))
        assertNull(MobileHttpMusicServer.parseByteRange(null, 1000L))
    }

    @Test
    fun parseQueryParams_decodesIdProperly() {
        val params = MobileHttpMusicServer.parseQueryParams("id=ms_42&name=Nhac%20Hay")
        assertEquals("ms_42", params["id"])
        assertEquals("Nhac Hay", params["name"])
    }

    @Test
    fun guessAudioMime_returnsExpectedMimeTypes() {
        assertEquals("audio/flac", MobileHttpMusicServer.guessAudioMime("FLAC"))
        assertEquals("audio/wav", MobileHttpMusicServer.guessAudioMime("WAV"))
        assertEquals("audio/mp4", MobileHttpMusicServer.guessAudioMime("M4A"))
        assertEquals("audio/mpeg", MobileHttpMusicServer.guessAudioMime("MP3"))
    }

    @Test
    fun escapeJson_escapesQuotesAndBackslashes() {
        val escaped = MobileHttpMusicServer.escapeJson("""Song "Remix"\Live""")
        assertEquals("""Song \"Remix\"\\Live""", escaped)
    }
}
