package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlaybackManager
import com.example.data.MusicRepository
import com.example.model.AppUiStyle
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.model.EqualizerPreset
import com.example.model.SortOption
import com.example.model.TvNavTab
import com.example.qr.LocalBackgroundServer
import com.example.qr.TvPlaybackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val playbackManager = AudioPlaybackManager(application)

    // Navigation & Screen State
    private val _currentTab = MutableStateFlow(TvNavTab.FOLDERS)
    val currentTab: StateFlow<TvNavTab> = _currentTab.asStateFlow()

    private val _browsingFolder = MutableStateFlow<AudioFolder?>(null)
    val browsingFolder: StateFlow<AudioFolder?> = _browsingFolder.asStateFlow()

    // Filter & Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFormat = MutableStateFlow("ALL")
    val selectedFormat: StateFlow<String> = _selectedFormat.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.TITLE)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // Appearance & QR Upload Dialog State
    val uiStyle = repository.uiStyle
    val customBackgroundPath = repository.customBackgroundPath
    val backgroundOpacity = repository.backgroundOpacity
    val glassBlurRadius = repository.glassBlurRadius
    val coverArtStyle = repository.coverArtStyle

    private val _isQrDialogVisible = MutableStateFlow(false)
    val isQrDialogVisible: StateFlow<Boolean> = _isQrDialogVisible.asStateFlow()

    private val _qrServerUrl = MutableStateFlow<String?>(null)
    val qrServerUrl: StateFlow<String?> = _qrServerUrl.asStateFlow()

    private val _qrUploadStatus = MutableStateFlow("Đang chờ kết nối từ điện thoại...")
    val qrUploadStatus: StateFlow<String> = _qrUploadStatus.asStateFlow()

    // Wi-Fi Phone Streaming & Remote Hub State
    private var isWifiMusicPinned = false

    private val _isWifiServerRunning = MutableStateFlow(false)
    val isWifiServerRunning: StateFlow<Boolean> = _isWifiServerRunning.asStateFlow()

    private val _wifiServerUrl = MutableStateFlow<String?>(null)
    val wifiServerUrl: StateFlow<String?> = _wifiServerUrl.asStateFlow()

    private val _wifiActivityStatus = MutableStateFlow("Bật máy chủ để phát nhạc trên điện thoại cùng Wi-Fi")
    val wifiActivityStatus: StateFlow<String> = _wifiActivityStatus.asStateFlow()

    // Repository flows
    val allTracks = repository.allTracks
    val allFolders = repository.allFolders
    val favoriteIds = repository.favoriteIds
    val recentTracks = repository.recentTracks
    val isScanning = repository.isScanning

    // Playback flows
    val currentTrack = playbackManager.currentTrack
    val isPlaying = playbackManager.isPlaying
    val currentPositionMs = playbackManager.currentPositionMs
    val durationMs = playbackManager.durationMs
    val repeatMode = playbackManager.repeatMode
    val isShuffle = playbackManager.isShuffle
    val equalizerPreset = playbackManager.equalizerPreset
    val sleepTimerSeconds = playbackManager.sleepTimerSeconds
    val visualizerBands = playbackManager.visualizerBands
    val playlist = playbackManager.playlist

    private val localServer = LocalBackgroundServer(
        context = application,
        getTracks = { allTracks.value },
        getCoverArtStyle = { coverArtStyle.value },
        getTvStatus = {
            val t = currentTrack.value
            TvPlaybackSnapshot(
                trackId = t?.id,
                title = t?.title ?: "Chưa phát bài nào",
                artist = t?.artist ?: "TV Music",
                album = t?.album ?: "",
                format = t?.format ?: "",
                isPlaying = isPlaying.value,
                positionMs = currentPositionMs.value,
                durationMs = durationMs.value
            )
        },
        onPlayTrackOnTv = { trackId ->
            viewModelScope.launch(Dispatchers.Main) {
                val list = allTracks.value
                val track = list.firstOrNull { it.id == trackId }
                if (track != null) {
                    playTrack(track, list)
                }
            }
        },
        onTvControl = { action ->
            viewModelScope.launch(Dispatchers.Main) {
                when (action) {
                    "toggle" -> togglePlayPause()
                    "next" -> skipNext()
                    "prev" -> skipPrevious()
                }
            }
        },
        onAudioUploaded = { savedFile, autoplay ->
            viewModelScope.launch {
                repository.refreshLibrary()
                if (autoplay) {
                    val list = allTracks.value
                    val uploaded = list.firstOrNull { it.filePath == savedFile.absolutePath }
                    if (uploaded != null) {
                        playTrack(uploaded, list)
                    }
                }
            }
        },
        onImageUploaded = { savedPath ->
            repository.setCustomBackgroundPath(savedPath)
            _qrUploadStatus.value = "Đã cập nhật ảnh nền mới từ điện thoại!"
        },
        onStatusUpdate = { status ->
            _qrUploadStatus.value = status
        },
        onClientActivity = { activity ->
            _wifiActivityStatus.value = activity
        }
    )

    // Tracks received over Wi-Fi
    val wifiTracks: StateFlow<List<AudioTrack>> = allTracks.map { tracks ->
        val wifiPath = repository.getWifiMusicDirPath()
        tracks.filter { it.parentDirectory == wifiPath }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Filtered tracks for the All Songs screen
    val filteredTracks: StateFlow<List<AudioTrack>> = combine(
        allTracks,
        _searchQuery,
        _selectedFormat,
        _sortOption
    ) { _, query, format, sort ->
        repository.filterAndSort(query, format, sort)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Tracks in current folder
    val currentFolderTracks: StateFlow<List<AudioTrack>> = combine(
        allTracks,
        _browsingFolder
    ) { _, folder ->
        if (folder != null) {
            repository.getTracksInFolder(folder.path)
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Favorite tracks
    val favoriteTracks: StateFlow<List<AudioTrack>> = combine(
        allTracks,
        favoriteIds
    ) { _, _ ->
        repository.getFavoriteTracks()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        scanLibrary()
    }

    fun scanLibrary() {
        viewModelScope.launch {
            repository.refreshLibrary()
        }
    }

    fun selectTab(tab: TvNavTab) {
        _currentTab.value = tab
        if (tab == TvNavTab.WIFI_PHONE && !_isWifiServerRunning.value) {
            startWifiMusicServer()
        }
    }

    fun openFolder(folder: AudioFolder) {
        _browsingFolder.value = folder
    }

    fun closeFolder() {
        _browsingFolder.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFormat(format: String) {
        _selectedFormat.value = format
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun setUiStyle(style: AppUiStyle) {
        repository.setUiStyle(style)
    }

    fun toggleUiStyle() {
        val next = if (uiStyle.value == AppUiStyle.CLASSIC_LIGHT) {
            AppUiStyle.APPLE_TV_GLASS
        } else {
            AppUiStyle.CLASSIC_LIGHT
        }
        repository.setUiStyle(next)
    }

    fun setBackgroundOpacity(opacity: Float) {
        repository.setBackgroundOpacity(opacity)
    }

    fun setGlassBlurRadius(blur: Float) {
        repository.setGlassBlurRadius(blur)
    }

    fun setCoverArtStyle(style: CoverArtStyle) {
        repository.setCoverArtStyle(style)
    }

    fun toggleCoverArtStyle() {
        val next = if (coverArtStyle.value == CoverArtStyle.SPINNING_DISC) {
            CoverArtStyle.ROUNDED_SQUARE
        } else {
            CoverArtStyle.SPINNING_DISC
        }
        repository.setCoverArtStyle(next)
    }

    fun setBackgroundFromUri(uri: Uri) {
        viewModelScope.launch {
            val saved = repository.saveCustomBackgroundFromUri(uri)
            if (saved != null) {
                _qrUploadStatus.value = "Đã chọn ảnh nền từ thiết bị!"
            }
        }
    }

    fun clearCustomBackground() {
        repository.clearCustomBackground()
    }

    fun startWifiMusicServer() {
        isWifiMusicPinned = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = localServer.start()
                _wifiServerUrl.value = "$baseUrl/?tab=play"
                _isWifiServerRunning.value = true
                _wifiActivityStatus.value = "Sẵn sàng — Quét QR bằng điện thoại cùng Wi-Fi để nghe nhạc"
            } catch (e: Exception) {
                isWifiMusicPinned = false
                _isWifiServerRunning.value = false
                _wifiServerUrl.value = null
                _wifiActivityStatus.value = "Không thể mở máy chủ Wi-Fi: ${e.message}"
            }
        }
    }

    fun stopWifiMusicServer() {
        isWifiMusicPinned = false
        _isWifiServerRunning.value = false
        _wifiServerUrl.value = null
        _wifiActivityStatus.value = "Máy chủ phát nhạc qua Wi-Fi đã tắt"
        if (!_isQrDialogVisible.value) {
            viewModelScope.launch(Dispatchers.IO) {
                localServer.stop()
            }
        }
    }

    fun toggleWifiMusicServer() {
        if (_isWifiServerRunning.value) {
            stopWifiMusicServer()
        } else {
            startWifiMusicServer()
        }
    }

    fun openQrBackgroundDialog() {
        _isQrDialogVisible.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = localServer.start()
                _qrServerUrl.value = "$baseUrl/?tab=bg"
                _qrUploadStatus.value = "Quét mã QR bằng điện thoại (cùng Wi-Fi) để gửi ảnh"
            } catch (e: Exception) {
                _qrUploadStatus.value = "Không thể mở máy chủ cục bộ: ${e.message}"
            }
        }
    }

    fun closeQrBackgroundDialog() {
        _isQrDialogVisible.value = false
        if (!isWifiMusicPinned) {
            viewModelScope.launch(Dispatchers.IO) {
                localServer.stop()
            }
        }
    }

    fun playTrack(track: AudioTrack, queue: List<AudioTrack>) {
        repository.addToRecent(track)
        playbackManager.playTrack(track, queue)
    }

    fun playFolder(folder: AudioFolder, shuffle: Boolean = false) {
        val tracks = repository.getTracksInFolder(folder.path)
        if (tracks.isNotEmpty()) {
            val playQueue = if (shuffle) tracks.shuffled() else tracks
            playTrack(playQueue.first(), playQueue)
            if (shuffle && !isShuffle.value) {
                playbackManager.toggleShuffle()
            }
        }
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun skipNext() = playbackManager.skipNext()
    fun skipPrevious() = playbackManager.skipPrevious()
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
    fun seekForward() = playbackManager.seekForward(10000L)
    fun seekRewind() = playbackManager.seekRewind(10000L)
    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun cycleRepeatMode() = playbackManager.cycleRepeatMode()
    fun setEqualizer(preset: EqualizerPreset) = playbackManager.setEqualizerPreset(preset)
    fun setSleepTimer(minutes: Int?) = playbackManager.setSleepTimer(minutes)
    fun toggleFavorite(trackId: String) = repository.toggleFavorite(trackId)

    override fun onCleared() {
        super.onCleared()
        localServer.stop()
        playbackManager.release()
    }
}
