package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlaybackManager
import com.example.data.MusicRepository
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import com.example.model.EqualizerPreset
import com.example.model.RepeatMode
import com.example.model.SortOption
import com.example.model.TvNavTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
        playbackManager.release()
    }
}
