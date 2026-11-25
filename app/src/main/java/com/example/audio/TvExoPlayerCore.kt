package com.example.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

object TvExoPlayerCore {

    val pcmProcessor = CleanHiResPcmAudioProcessor()

    @Volatile
    var backgroundPlaybackEnabled: Boolean = true

    @Volatile
    var keepCpuAwakeInBackground: Boolean = true

    @Volatile
    private var sharedPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        sharedPlayer?.let { return it }

        val appContext = context.applicationContext
        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(pcmProcessor))
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(false)
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(appContext, renderersFactory)
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Disable hardware audio offload so 24-bit/48kHz+ streams always decode through our
        // clean software PCM processor instead of noisy TV DSP offload paths.
        val offloadPrefs = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(offloadPrefs)
            .build()

        sharedPlayer = player
        return player
    }

    fun getExistingPlayer(): ExoPlayer? = sharedPlayer

    fun releaseIfNotBackground() {
        val existing = sharedPlayer ?: return
        if (backgroundPlaybackEnabled && (existing.isPlaying || existing.playWhenReady || existing.currentMediaItem != null)) {
            return
        }
        sharedPlayer = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            existing.release()
        } else {
            Handler(Looper.getMainLooper()).post { existing.release() }
        }
    }

    fun forceRelease() {
        val player = sharedPlayer ?: return
        sharedPlayer = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            player.release()
        } else {
            Handler(Looper.getMainLooper()).post { player.release() }
        }
    }
}
