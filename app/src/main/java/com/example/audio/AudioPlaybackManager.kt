package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioTrack
import com.example.model.EqualizerPreset
import com.example.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlaybackManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("tv_music_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var exoPlayer: ExoPlayer? = null

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(EqualizerPreset.NORMAL)
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _sleepTimerSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerSeconds: StateFlow<Int?> = _sleepTimerSeconds.asStateFlow()

    private val _playlist = MutableStateFlow<List<AudioTrack>>(emptyList())
    val playlist: StateFlow<List<AudioTrack>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _antiHiss24BitEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_ANTI_HISS_24BIT, true).also {
            TvExoPlayerCore.pcmProcessor.antiHissEnabled = it
        }
    )
    val antiHiss24BitEnabled: StateFlow<Boolean> = _antiHiss24BitEnabled.asStateFlow()

    private val _backgroundPlaybackEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, true).also {
            TvExoPlayerCore.backgroundPlaybackEnabled = it
        }
    )
    val backgroundPlaybackEnabled: StateFlow<Boolean> = _backgroundPlaybackEnabled.asStateFlow()

    private val _keepCpuAwakeInBackground = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_CPU_AWAKE, true).also {
            TvExoPlayerCore.keepCpuAwakeInBackground = it
        }
    )
    val keepCpuAwakeInBackground: StateFlow<Boolean> = _keepCpuAwakeInBackground.asStateFlow()

    // Visualizer simulation flow (0f..1f for 16 frequency bands)
    private val _visualizerBands = MutableStateFlow(List(16) { 0.1f })
    val visualizerBands: StateFlow<List<Float>> = _visualizerBands.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var visualizerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying && _backgroundPlaybackEnabled.value && exoPlayer?.currentMediaItem != null) {
                MusicPlaybackService.ensureStarted(context)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            if (playbackState == Player.STATE_READY) {
                val dur = player.duration
                if (dur > 0L) {
                    _durationMs.value = dur
                } else {
                    _currentTrack.value?.durationMs?.let { if (it > 0L) _durationMs.value = it }
                }
            } else if (playbackState == Player.STATE_ENDED) {
                onTrackCompleted()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("AudioPlaybackManager", "ExoPlayer error: ${error.errorCodeName}", error)
            _isPlaying.value = false
            try {
                exoPlayer?.stop()
            } catch (_: Exception) {
            }
        }
    }

    init {
        startProgressTracker()
        startVisualizerGenerator()
    }

    private fun ensurePlayer(): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing
        val created = TvExoPlayerCore.getOrCreatePlayer(context).apply {
            addListener(playerListener)
        }
        exoPlayer = created
        return created
    }

    fun playTrack(track: AudioTrack, queue: List<AudioTrack> = emptyList()) {
        val newQueue = if (queue.isNotEmpty()) queue else listOf(track)
        _playlist.value = newQueue
        val foundIdx = newQueue.indexOfFirst { it.id == track.id }
        _currentIndex.value = if (foundIdx >= 0) foundIdx else 0
        prepareAndPlay(track)
    }

    private fun prepareAndPlay(track: AudioTrack) {
        try {
            val player = ensurePlayer()
            val mediaUri = resolveMediaUri(track) ?: run {
                Log.e("AudioPlaybackManager", "Empty media URI for track: ${track.title}")
                _isPlaying.value = false
                return
            }

            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
            if (!track.albumArtUri.isNullOrBlank()) {
                val artUri = if (track.albumArtUri.startsWith("/")) {
                    Uri.fromFile(File(track.albumArtUri))
                } else {
                    Uri.parse(track.albumArtUri)
                }
                metadataBuilder.setArtworkUri(artUri)
            }

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(mediaUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()

            _currentTrack.value = track
            _currentPositionMs.value = 0L
            _durationMs.value = track.durationMs.coerceAtLeast(0L)

            player.setMediaItem(mediaItem)
            if (_playbackSpeed.value != 1.0f) {
                player.setPlaybackSpeed(_playbackSpeed.value)
            }
            player.prepare()
            player.playWhenReady = true
            player.play()
            _isPlaying.value = true

            if (_backgroundPlaybackEnabled.value) {
                MusicPlaybackService.ensureStarted(context)
            }
        } catch (t: Throwable) {
            Log.e("AudioPlaybackManager", "Error preparing track: ${track.filePath}", t)
            _isPlaying.value = false
        }
    }

    private fun resolveMediaUri(track: AudioTrack): Uri? {
        val uriStr = track.contentUri
        if (!uriStr.isNullOrBlank()) {
            return if (uriStr.startsWith("/")) Uri.fromFile(File(uriStr)) else Uri.parse(uriStr)
        }
        val path = track.filePath
        if (path.isNotBlank()) {
            return if (path.startsWith("http://", ignoreCase = true) ||
                path.startsWith("https://", ignoreCase = true) ||
                path.startsWith("content://", ignoreCase = true)
            ) {
                Uri.parse(path)
            } else {
                Uri.fromFile(File(path))
            }
        }
        return null
    }

    fun togglePlayPause() {
        val player = exoPlayer
        if (player != null && player.mediaItemCount > 0) {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.play()
                _isPlaying.value = true
                if (_backgroundPlaybackEnabled.value) {
                    MusicPlaybackService.ensureStarted(context)
                }
            }
        } else {
            val tracks = _playlist.value
            if (tracks.isNotEmpty()) {
                val idx = _currentIndex.value.coerceIn(0, tracks.lastIndex)
                playTrack(tracks[idx], tracks)
            }
        }
    }

    fun pause() {
        exoPlayer?.let {
            if (it.isPlaying || it.playWhenReady) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    fun resume() {
        exoPlayer?.let {
            if (!it.isPlaying && it.mediaItemCount > 0) {
                it.play()
                _isPlaying.value = true
                if (_backgroundPlaybackEnabled.value) {
                    MusicPlaybackService.ensureStarted(context)
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val maxDur = _durationMs.value.coerceAtLeast(1L)
        val target = positionMs.coerceIn(0L, maxDur)
        exoPlayer?.seekTo(target)
        _currentPositionMs.value = target
    }

    fun seekForward(deltaMs: Long = 10000L) {
        val maxDur = _durationMs.value.coerceAtLeast(_currentPositionMs.value)
        val newPos = (_currentPositionMs.value + deltaMs).coerceAtMost(maxDur)
        seekTo(newPos)
    }

    fun seekRewind(deltaMs: Long = 10000L) {
        val newPos = (_currentPositionMs.value - deltaMs).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun skipNext() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        val nextIndex = if (_isShuffle.value && tracks.size > 1) {
            var rand = (0 until tracks.size).random()
            if (rand == _currentIndex.value) rand = (rand + 1) % tracks.size
            rand
        } else {
            (_currentIndex.value + 1) % tracks.size
        }

        _currentIndex.value = nextIndex
        prepareAndPlay(tracks[nextIndex])
    }

    fun skipPrevious() {
        val tracks = _playlist.value
        if (tracks.isEmpty()) return

        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = if (_currentIndex.value > 0) {
            _currentIndex.value - 1
        } else {
            tracks.size - 1
        }

        _currentIndex.value = prevIndex
        prepareAndPlay(tracks[prevIndex])
    }

    private fun onTrackCompleted() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                exoPlayer?.let {
                    it.seekTo(0L)
                    it.play()
                    _isPlaying.value = true
                    _currentPositionMs.value = 0L
                }
            }
            RepeatMode.ALL -> {
                skipNext()
            }
            RepeatMode.OFF -> {
                val tracks = _playlist.value
                if (_currentIndex.value < tracks.size - 1) {
                    skipNext()
                } else {
                    _isPlaying.value = false
                    seekTo(0L)
                }
            }
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        try {
            exoPlayer?.setPlaybackSpeed(speed)
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error setting playback speed", e)
        }
    }

    fun setAntiHiss24BitEnabled(enabled: Boolean) {
        _antiHiss24BitEnabled.value = enabled
        TvExoPlayerCore.pcmProcessor.antiHissEnabled = enabled
        prefs.edit().putBoolean(KEY_ANTI_HISS_24BIT, enabled).apply()
        // Re-prepare active track if currently loaded so audio sink reconfigures cleanly
        val active = _currentTrack.value
        val pos = _currentPositionMs.value
        val wasPlaying = _isPlaying.value
        if (active != null && exoPlayer != null) {
            prepareAndPlay(active)
            if (pos > 0L) seekTo(pos)
            if (!wasPlaying) pause()
        }
    }

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        _backgroundPlaybackEnabled.value = enabled
        TvExoPlayerCore.backgroundPlaybackEnabled = enabled
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply()
        if (enabled) {
            if (_isPlaying.value || _currentTrack.value != null) {
                MusicPlaybackService.ensureStarted(context)
            }
        } else {
            MusicPlaybackService.stopService(context)
        }
    }

    fun setKeepCpuAwakeInBackground(enabled: Boolean) {
        _keepCpuAwakeInBackground.value = enabled
        TvExoPlayerCore.keepCpuAwakeInBackground = enabled
        prefs.edit().putBoolean(KEY_KEEP_CPU_AWAKE, enabled).apply()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimerSeconds.value = null
            return
        }

        _sleepTimerSeconds.value = minutes * 60
        sleepTimerJob = scope.launch {
            while (isActive && (_sleepTimerSeconds.value ?: 0) > 0) {
                delay(1000L)
                val current = _sleepTimerSeconds.value ?: 0
                if (current <= 1) {
                    _sleepTimerSeconds.value = null
                    pause()
                    break
                } else {
                    _sleepTimerSeconds.value = current - 1
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(250L)
                exoPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                            val dur = player.duration
                            if (dur > 0L && _durationMs.value != dur) {
                                _durationMs.value = dur
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun startVisualizerGenerator() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var step = 0
            while (isActive) {
                delay(80L)
                if (_isPlaying.value) {
                    step++
                    val preset = _equalizerPreset.value
                    val bands = List(16) { index ->
                        val freq = (index + 1) * 0.4
                        val wave = kotlin.math.sin(step * 0.3 + freq).toFloat()
                        val noise = (Math.random().toFloat() * 0.3f)
                        val raw = (wave * 0.35f + 0.45f + noise).coerceIn(0.08f, 0.98f)

                        val eqFactor = if (index < 4) {
                            1f + (preset.bass * 0.4f)
                        } else if (index in 4..10) {
                            1f + (preset.mid * 0.3f)
                        } else {
                            1f + (preset.treble * 0.3f)
                        }
                        (raw * eqFactor).coerceIn(0.05f, 1.0f)
                    }
                    _visualizerBands.value = bands
                } else {
                    _visualizerBands.value = List(16) { 0.05f }
                }
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        visualizerJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        exoPlayer = null
        TvExoPlayerCore.releaseIfNotBackground()
    }

    companion object {
        private const val KEY_ANTI_HISS_24BIT = "anti_hiss_24bit_enabled"
        private const val KEY_BACKGROUND_PLAYBACK = "background_playback_enabled"
        private const val KEY_KEEP_CPU_AWAKE = "keep_cpu_awake_background"
    }
}
