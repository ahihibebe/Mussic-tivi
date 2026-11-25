package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.AppUiStyle
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.model.SortOption
import com.example.model.WifiMusicRole
import com.example.scanner.StorageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    private val _scanStageText = MutableStateFlow("")
    val scanStageText: StateFlow<String> = _scanStageText.asStateFlow()

    private val _scanNewCount = MutableStateFlow(0)
    val scanNewCount: StateFlow<Int> = _scanNewCount.asStateFlow()

    private val _scanLatestTitle = MutableStateFlow<String?>(null)
    val scanLatestTitle: StateFlow<String?> = _scanLatestTitle.asStateFlow()

    private val _lastScanSummary = MutableStateFlow<String?>(null)
    val lastScanSummary: StateFlow<String?> = _lastScanSummary.asStateFlow()

    // Appearance & Background Preferences
    private val _uiStyle = MutableStateFlow(loadUiStyle())
    val uiStyle: StateFlow<AppUiStyle> = _uiStyle.asStateFlow()

    private val _customBackgroundPath = MutableStateFlow(loadCustomBackgroundPath())
    val customBackgroundPath: StateFlow<String?> = _customBackgroundPath.asStateFlow()

    private val _backgroundOpacity = MutableStateFlow(loadBackgroundOpacity())
    val backgroundOpacity: StateFlow<Float> = _backgroundOpacity.asStateFlow()

    private val _glassBlurRadius = MutableStateFlow(loadGlassBlurRadius())
    val glassBlurRadius: StateFlow<Float> = _glassBlurRadius.asStateFlow()

    private val _coverArtStyle = MutableStateFlow(loadCoverArtStyle())
    val coverArtStyle: StateFlow<CoverArtStyle> = _coverArtStyle.asStateFlow()

    private val _wifiMusicRole = MutableStateFlow(loadWifiMusicRole())
    val wifiMusicRole: StateFlow<WifiMusicRole> = _wifiMusicRole.asStateFlow()

    suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        val previousIds = _allTracks.value.map { it.id }.toSet()
        _isScanning.value = true
        _scanStageText.value = "Đang khởi tạo quét kho nhạc..."
        _scanNewCount.value = 0
        _scanLatestTitle.value = null
        _lastScanSummary.value = null

        try {
            val favs = _favoriteIds.value
            val result = scanner.scanAll(
                extraTreeUris = loadSavedTreeUris(),
                extraFileUris = loadSavedFileUris(),
                extraLocalPaths = loadLocalFolderPaths(),
                onProgress = { progress ->
                    val updated = progress.tracks.map { it.copy(isFavorite = favs.contains(it.id)) }
                    _allTracks.value = updated
                    _allFolders.value = progress.folders
                    _scanStageText.value = progress.stageLabel
                    _scanNewCount.value = updated.count { !previousIds.contains(it.id) }
                    progress.latestTrackTitle?.let { _scanLatestTitle.value = it }
                }
            )

            val updatedTracks = result.tracks.map { track ->
                track.copy(isFavorite = favs.contains(track.id))
            }

            _allTracks.value = updatedTracks
            _allFolders.value = result.folders
            val newlyAdded = updatedTracks.count { !previousIds.contains(it.id) }
            _scanNewCount.value = newlyAdded
            _lastScanSummary.value = if (newlyAdded > 0 && previousIds.isNotEmpty()) {
                "Quét xong: ${updatedTracks.size} bài hát (+$newlyAdded bài mới)"
            } else {
                "Quét xong: ${updatedTracks.size} bài hát (${result.folders.size} thư mục)"
            }
        } finally {
            _isScanning.value = false
            _scanLatestTitle.value = null
        }
    }

    fun addSafFolder(uri: Uri) {
        val current = prefs.getStringSet("saf_tree_uris", emptySet()).orEmpty().toMutableSet()
        current.add(uri.toString())
        prefs.edit().putStringSet("saf_tree_uris", current).apply()
    }

    fun addSafFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val current = prefs.getStringSet("saf_file_uris", emptySet()).orEmpty().toMutableSet()
        uris.forEach { current.add(it.toString()) }
        prefs.edit().putStringSet("saf_file_uris", current).apply()
    }

    private fun loadSavedTreeUris(): Set<Uri> {
        return prefs.getStringSet("saf_tree_uris", emptySet())
            .orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .toSet()
    }

    private fun loadSavedFileUris(): Set<Uri> {
        return prefs.getStringSet("saf_file_uris", emptySet())
            .orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .toSet()
    }

    fun addLocalFolderPath(path: String) {
        val current = prefs.getStringSet("local_folder_paths", emptySet()).orEmpty().toMutableSet()
        current.add(path)
        prefs.edit().putStringSet("local_folder_paths", current).apply()
    }

    private fun loadLocalFolderPaths(): Set<String> {
        return prefs.getStringSet("local_folder_paths", emptySet()).orEmpty()
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
        return _allTracks.value.filter {
            it.parentDirectory.ifEmpty { "Bộ nhớ trong" } == folderPath
        }
    }

    fun getFavoriteTracks(): List<AudioTrack> {
        val favs = _favoriteIds.value
        return _allTracks.value.filter { favs.contains(it.id) }
    }

    fun getWifiMusicDirPath(): String {
        return File(context.filesDir, "wifi_music").apply { mkdirs() }.absolutePath
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

    fun setUiStyle(style: AppUiStyle) {
        _uiStyle.value = style
        prefs.edit().putString("ui_style", style.name).apply()
    }

    fun setCustomBackgroundPath(path: String?) {
        _customBackgroundPath.value = path
        if (path == null) {
            prefs.edit().remove("custom_bg_path").apply()
        } else {
            prefs.edit().putString("custom_bg_path", path).apply()
        }
    }

    suspend fun saveCustomBackgroundFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            val maxDim = 2560
            while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: return@withContext null

            context.filesDir.listFiles { f ->
                f.name.startsWith("custom_bg_") && f.name.endsWith(".jpg")
            }?.forEach { it.delete() }

            val outFile = File(context.filesDir, "custom_bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            bitmap.recycle()
            setCustomBackgroundPath(outFile.absolutePath)
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun clearCustomBackground() {
        context.filesDir.listFiles { f ->
            f.name.startsWith("custom_bg_") && f.name.endsWith(".jpg")
        }?.forEach { it.delete() }
        setCustomBackgroundPath(null)
    }

    fun setBackgroundOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        _backgroundOpacity.value = clamped
        prefs.edit().putFloat("bg_opacity", clamped).apply()
    }

    fun setGlassBlurRadius(blur: Float) {
        val clamped = blur.coerceIn(0f, 24f)
        _glassBlurRadius.value = clamped
        prefs.edit().putFloat("glass_blur", clamped).apply()
    }

    fun setCoverArtStyle(style: CoverArtStyle) {
        _coverArtStyle.value = style
        prefs.edit().putString("cover_art_style", style.name).apply()
    }

    fun setWifiMusicRole(role: WifiMusicRole) {
        _wifiMusicRole.value = role
        prefs.edit().putString("wifi_music_role", role.name).apply()
    }

    fun getLastPhoneServerUrl(): String {
        return prefs.getString("last_phone_server_url", "") ?: ""
    }

    fun setLastPhoneServerUrl(url: String) {
        prefs.edit().putString("last_phone_server_url", url).apply()
    }

    private fun loadFavorites(): Set<String> {
        val set = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        return set.toSet()
    }

    private fun saveFavorites(favs: Set<String>) {
        prefs.edit().putStringSet("favorite_ids", favs).apply()
    }

    private fun loadUiStyle(): AppUiStyle {
        val name = prefs.getString("ui_style", AppUiStyle.CLASSIC_LIGHT.name)
        return AppUiStyle.entries.find { it.name == name } ?: AppUiStyle.CLASSIC_LIGHT
    }

    private fun loadCustomBackgroundPath(): String? {
        val path = prefs.getString("custom_bg_path", null)
        return if (!path.isNullOrBlank() && File(path).exists()) path else null
    }

    private fun loadBackgroundOpacity(): Float {
        return prefs.getFloat("bg_opacity", 0.55f).coerceIn(0f, 1f)
    }

    private fun loadGlassBlurRadius(): Float {
        return prefs.getFloat("glass_blur", 14f).coerceIn(0f, 24f)
    }

    private fun loadCoverArtStyle(): CoverArtStyle {
        val name = prefs.getString("cover_art_style", CoverArtStyle.SPINNING_DISC.name)
        return CoverArtStyle.entries.find { it.name == name } ?: CoverArtStyle.SPINNING_DISC
    }

    private fun loadWifiMusicRole(): WifiMusicRole {
        val name = prefs.getString("wifi_music_role", WifiMusicRole.TV_CLIENT_PULL_PHONE.name)
        return WifiMusicRole.entries.find { it.name == name } ?: WifiMusicRole.TV_CLIENT_PULL_PHONE
    }
}
