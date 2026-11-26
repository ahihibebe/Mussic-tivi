package com.example.mobile

import com.example.mobile.model.MobileAudioTrack
import com.example.mobile.network.TvRemoteClient
import com.example.mobile.server.MobileHttpMusicServer
import com.example.mobile.webrtc.MobileWebRtcHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun tvRemoteClient_parsesTvBeaconsAndNormalizesUrls() {
        assertEquals("http://192.168.1.50:8765", TvRemoteClient.normalizeTvUrl("192.168.1.50"))
        assertNull(
            TvRemoteClient.parseTvDiscoveryPacket(
                "TVMUSIC_DISCOVER|1|http://192.168.1.10:8765|Phone|10|PHONE_SERVER|8765"
            )
        )
        val tv = TvRemoteClient.parseTvDiscoveryPacket(
            "TVMUSIC_DISCOVER|1|http://192.168.1.50:8765|Sony Bravia|24|TV_PLAYER|8765"
        )
        assertNotNull(tv)
        assertEquals("Sony Bravia", tv!!.deviceName)
        assertEquals("http://192.168.1.50:8765", tv.url)
    }

    @Test
    fun mobileWebRtcHost_usesGoogleStunAndParsesSrflxAndBase64Url() {
        assertEquals("stun:stun.l.google.com:19302", MobileWebRtcHost.PRIMARY_GOOGLE_STUN)
        assertTrue(MobileWebRtcHost.GOOGLE_STUN_SERVERS.size >= 5)
        assertEquals(
            "198.51.100.77:61234",
            MobileWebRtcHost.extractSrflxEndpoint(
                "candidate:99 1 udp 1686052607 198.51.100.77 61234 typ srflx raddr 10.0.0.5 rport 61234"
            )
        )
        assertNull(
            MobileWebRtcHost.extractSrflxEndpoint(
                "candidate:1 1 udp 2122260223 10.0.0.5 61234 typ host"
            )
        )
        val raw = "TVRTC1-payload-test-123".toByteArray()
        val encoded = MobileWebRtcHost.encodeBase64UrlNoPad(raw)
        assertFalse(encoded.contains("="))
        assertEquals(String(raw), String(MobileWebRtcHost.decodeBase64UrlNoPad(encoded)))
    }
}
