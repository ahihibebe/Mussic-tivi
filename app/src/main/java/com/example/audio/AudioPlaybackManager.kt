package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.model.AudioTrack
import com.example.model.EqualizerPreset
import com.example.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlaybackManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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

    // Visualizer simulation flow (0f..1f for 16 frequency bands)
    private val _visualizerBands = MutableStateFlow(List(16) { 0.1f })
    val visualizerBands: StateFlow<List<Float>> = _visualizerBands.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var visualizerJob: Job? = null

    init {
        startProgressTracker()
        startVisualizerGenerator()
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
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                if (track.contentUri != null) {
                    setDataSource(context, Uri.parse(track.contentUri))
                } else {
                    setDataSource(track.filePath)
                }

                setOnPreparedListener { mp ->
                    val dur = mp.duration.toLong()
                    _durationMs.value = if (dur > 0) dur else track.durationMs
                    mp.start()
                    _isPlaying.value = true
                    applyPlaybackSpeed()
                }

                setOnCompletionListener {
                    onTrackCompleted()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlaybackManager", "MediaPlayer error: what=$what, extra=$extra")
                    _isPlaying.value = false
                    true
                }

                prepareAsync()
            }

            _currentTrack.value = track
            _currentPositionMs.value = 0L

        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Error preparing track: ${track.filePath}", e)
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp != null) {
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                requestAudioFocus()
                mp.start()
                _isPlaying.value = true
            }
        } else {
            // If no track loaded, play first in playlist if available
            val tracks = _playlist.value
            if (tracks.isNotEmpty()) {
                val idx = _currentIndex.value.coerceIn(0, tracks.lastIndex)
                playTrack(tracks[idx], tracks)
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                requestAudioFocus()
                it.start()
                _isPlaying.value = true
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        mediaPlayer?.seekTo(target.toInt())
        _currentPositionMs.value = target
    }

    fun seekForward(deltaMs: Long = 10000L) {
        val newPos = (_currentPositionMs.value + deltaMs).coerceAtMost(_durationMs.value)
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

        // If playing for > 3 seconds, restart current track first
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
                seekTo(0L)
                mediaPlayer?.start()
                _isPlaying.value = true
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
        applyPlaybackSpeed()
    }

    private fun applyPlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                try {
                    val params = mp.playbackParams
                    params.speed = _playbackSpeed.value
                    mp.playbackParams = params
                } catch (e: Exception) {
                    Log.e("AudioPlaybackManager", "Error setting playback speed", e)
                }
            }
        }
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
                delay(200L)
                mediaPlayer?.let { mp ->
                    if (_isPlaying.value && mp.isPlaying) {
                        try {
                            _currentPositionMs.value = mp.currentPosition.toLong()
                        } catch (ignored: Exception) {}
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

                        // Apply EQ preset factor
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

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS
                    ) {
                        pause()
                    }
                }
                .build()
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS
                    ) {
                        pause()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        visualizerJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
