package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import com.example.model.SortOption
import com.example.scanner.StorageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    private val scanner = StorageScanner(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("tv_music_prefs", Context.MODE_PRIVATE)

    private val _allTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val allTracks: StateFlow<List<AudioTrack>> = _allTracks.asStateFlow()

    private val _allFolders = MutableStateFlow<List<AudioFolder>>(emptyList())
    val allFolders: StateFlow<List<AudioFolder>> = _allFolders.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(loadFavorites())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _recentTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val recentTracks: StateFlow<List<AudioTrack>> = _recentTracks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            val result = scanner.scanAll(includeDemo = true)
            val favs = _favoriteIds.value

            val updatedTracks = result.tracks.map { track ->
                track.copy(isFavorite = favs.contains(track.id))
            }

            _allTracks.value = updatedTracks
            _allFolders.value = result.folders
        } finally {
            _isScanning.value = false
        }
    }

    fun toggleFavorite(trackId: String) {
        val currentFavs = _favoriteIds.value.toMutableSet()
        if (currentFavs.contains(trackId)) {
            currentFavs.remove(trackId)
        } else {
            currentFavs.add(trackId)
        }
        _favoriteIds.value = currentFavs
        saveFavorites(currentFavs)

        // Update in-memory track list
        _allTracks.value = _allTracks.value.map {
            if (it.id == trackId) it.copy(isFavorite = currentFavs.contains(trackId)) else it
        }
    }

    fun addToRecent(track: AudioTrack) {
        val list = _recentTracks.value.toMutableList()
        list.removeAll { it.id == track.id }
        list.add(0, track)
        if (list.size > 20) {
            _recentTracks.value = list.subList(0, 20)
        } else {
            _recentTracks.value = list
        }
    }

    fun getTracksInFolder(folderPath: String): List<AudioTrack> {
        return _allTracks.value.filter { it.parentDirectory == folderPath }
    }

    fun getFavoriteTracks(): List<AudioTrack> {
        val favs = _favoriteIds.value
        return _allTracks.value.filter { favs.contains(it.id) }
    }

    fun filterAndSort(
        query: String = "",
        formatFilter: String? = null,
        sortOption: SortOption = SortOption.TITLE
    ): List<AudioTrack> {
        var list = _allTracks.value

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q) ||
                it.format.lowercase().contains(q)
            }
        }

        if (!formatFilter.isNullOrBlank() && formatFilter != "ALL") {
            list = list.filter { it.format.equals(formatFilter, ignoreCase = true) }
        }

        return when (sortOption) {
            SortOption.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> list.sortedBy { it.artist.lowercase() }
            SortOption.DURATION -> list.sortedByDescending { it.durationMs }
            SortOption.FORMAT -> list.sortedBy { it.format }
        }
    }

    private fun loadFavorites(): Set<String> {
        val set = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        return set.toSet()
    }

    private fun saveFavorites(favs: Set<String>) {
        prefs.edit().putStringSet("favorite_ids", favs).apply()
    }
}
