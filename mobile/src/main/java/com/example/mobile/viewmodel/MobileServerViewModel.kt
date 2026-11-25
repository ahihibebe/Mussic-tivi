package com.example.mobile.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mobile.model.MobileAudioTrack
import com.example.mobile.network.DiscoveredTvDevice
import com.example.mobile.network.TvRemoteClient
import com.example.mobile.network.TvRemoteStatus
import com.example.mobile.scanner.PhoneMusicScanner
import com.example.mobile.server.MobileHttpMusicServer
import com.example.mobile.server.QrGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SelectedFolderItem(
    val uriString: String,
    val displayName: String
)

data class MobileServerUiState(
    val tracks: List<MobileAudioTrack> = emptyList(),
    val filteredTracks: List<MobileAudioTrack> = emptyList(),
    val searchQuery: String = "",
    val selectedFormat: String = "ALL",
    val formatCounts: Map<String, Int> = emptyMap(),
    val isScanning: Boolean = false,
    val scanStageText: String = "",
    val scanNewCount: Int = 0,
    val scanLatestTitle: String? = null,
    val lastScanSummary: String? = null,
    val hasScannedOnce: Boolean = false,
    val isServerRunning: Boolean = false,
    val serverUrl: String? = null,
    val qrBitmap: ImageBitmap? = null,
    val statusText: String = "Nhấn \"Đặt máy này làm Server nhạc\" để Tivi quét và phát trực tiếp",
    val activityLog: List<String> = emptyList(),
    val hasAudioPermission: Boolean = false,
    val savedFolderCount: Int = 0,
    val selectedFolders: List<SelectedFolderItem> = emptyList(),
    val excludedTrackCount: Int = 0,
    val previewTrackId: String? = null,
    val isPreviewPlaying: Boolean = false,
    val deviceName: String = "Android",
    // TV Direct Cast & Remote Control Panel state
    val discoveredTvs: List<DiscoveredTvDevice> = emptyList(),
    val isScanningTvs: Boolean = false,
    val manualTvIp: String = "",
    val tvStatus: TvRemoteStatus = TvRemoteStatus(),
    val tvConnectionMessage: String? = null,
    val castingTrackId: String? = null
)

class MobileServerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheFile = File(application.filesDir, CACHE_FILE_NAME)
    private val scanner = PhoneMusicScanner(application)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var scanJob: Job? = null
    private var tvUdpJob: Job? = null
    private var tvPollJob: Job? = null

    private val _tracks = MutableStateFlow<List<MobileAudioTrack>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedFormat = MutableStateFlow("ALL")
    private val _isScanning = MutableStateFlow(false)
    private val _scanStageText = MutableStateFlow("")
    private val _scanNewCount = MutableStateFlow(0)
    private val _scanLatestTitle = MutableStateFlow<String?>(null)
    private val _lastScanSummary = MutableStateFlow<String?>(null)
    private val _hasScannedOnce = MutableStateFlow(prefs.getBoolean(KEY_HAS_SCANNED_ONCE, false))
    private val _isServerRunning = MutableStateFlow(false)
    private val _serverUrl = MutableStateFlow<String?>(null)
    private val _qrBitmap = MutableStateFlow<ImageBitmap?>(null)
    private val _statusText = MutableStateFlow("Chạm bài hát để phát ngay lên Tivi hoặc bật Server Wi-Fi bên dưới")
    private val _activityLog = MutableStateFlow<List<String>>(emptyList())
    private val _hasAudioPermission = MutableStateFlow(checkAudioPermission())
    private val _savedFolderCount = MutableStateFlow(loadSavedTreeUris().size + loadSavedFileUris().size)
    private val _selectedFolders = MutableStateFlow(buildSelectedFolderItems())
    private val _excludedTrackCount = MutableStateFlow(loadExcludedTrackIds().size)
    private val _previewTrackId = MutableStateFlow<String?>(null)
    private val _isPreviewPlaying = MutableStateFlow(false)

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTvDevice>>(emptyList())
    private val _isScanningTvs = MutableStateFlow(false)
    private val _manualTvIp = MutableStateFlow(prefs.getString(KEY_LAST_TV_URL, "") ?: "")
    private val _tvStatus = MutableStateFlow(TvRemoteStatus())
    private val _tvConnectionMessage = MutableStateFlow<String?>(null)
    private val _castingTrackId = MutableStateFlow<String?>(null)

    private val httpServer = MobileHttpMusicServer(
        context = application,
        getTracks = { _tracks.value },
        onStatusUpdate = { msg -> _statusText.value = msg },
        onClientStreamed = { logMsg -> appendActivityLog(logMsg) }
    )

    private data class ScanVisualMeta(
        val isScanning: Boolean,
        val stageText: String,
        val newCount: Int,
        val latestTitle: String?,
        val summary: String?,
        val hasScannedOnce: Boolean
    )

    private data class FolderLibraryMeta(
        val folderCount: Int,
        val selectedFolders: List<SelectedFolderItem>,
        val excludedTrackCount: Int
    )

    private data class TvRemoteMeta(
        val discoveredTvs: List<DiscoveredTvDevice>,
        val isScanningTvs: Boolean,
        val manualTvIp: String,
        val tvStatus: TvRemoteStatus,
        val tvMessage: String?,
        val castingTrackId: String?
    )

    private data class ServerMeta(
        val isRunning: Boolean,
        val serverUrl: String?,
        val qrBitmap: ImageBitmap?,
        val statusText: String,
        val activityLog: List<String>
    )

    private val scanMetaFlow = combine(
        _isScanning,
        _scanStageText,
        _scanNewCount,
        _scanLatestTitle,
        _lastScanSummary,
        _hasScannedOnce
    ) { arr ->
        ScanVisualMeta(
            isScanning = arr[0] as Boolean,
            stageText = arr[1] as String,
            newCount = arr[2] as Int,
            latestTitle = arr[3] as String?,
            summary = arr[4] as String?,
            hasScannedOnce = arr[5] as Boolean
        )
    }

    private val folderMetaFlow = combine(
        _savedFolderCount,
        _selectedFolders,
        _excludedTrackCount
    ) { folderCount, folders, excludedCount ->
        FolderLibraryMeta(folderCount, folders, excludedCount)
    }

    private val serverMetaFlow = combine(
        _isServerRunning,
        _serverUrl,
        _qrBitmap,
        _statusText,
        _activityLog
    ) { isRunning, url, qr, status, logs ->
        ServerMeta(isRunning, url, qr, status, logs)
    }

    private val tvRemoteMetaFlow = combine(
        _discoveredTvs,
        _isScanningTvs,
        _manualTvIp,
        _tvStatus,
        _tvConnectionMessage,
        _castingTrackId
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        TvRemoteMeta(
            discoveredTvs = arr[0] as List<DiscoveredTvDevice>,
            isScanningTvs = arr[1] as Boolean,
            manualTvIp = arr[2] as String,
            tvStatus = arr[3] as TvRemoteStatus,
            tvMessage = arr[4] as String?,
            castingTrackId = arr[5] as String?
        )
    }

    val uiState: StateFlow<MobileServerUiState> = combine(
        _tracks,
        _searchQuery,
        _selectedFormat,
        scanMetaFlow,
        serverMetaFlow,
        _hasAudioPermission,
        folderMetaFlow,
        _previewTrackId,
        _isPreviewPlaying,
        tvRemoteMetaFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tracks = values[0] as List<MobileAudioTrack>
        val query = values[1] as String
        val formatFilter = values[2] as String
        val scanMeta = values[3] as ScanVisualMeta
        val serverMeta = values[4] as ServerMeta
        val hasPerm = values[5] as Boolean
        val folderMeta = values[6] as FolderLibraryMeta
        val previewId = values[7] as String?
        val isPreview = values[8] as Boolean
        val tvMeta = values[9] as TvRemoteMeta

        val counts = linkedMapOf<String, Int>()
        counts["ALL"] = tracks.size
        tracks.forEach { t ->
            val fmt = t.format.uppercase(Locale.ROOT).ifBlank { "MP3" }
            counts[fmt] = (counts[fmt] ?: 0) + 1
        }

        val formatFiltered = if (formatFilter == "ALL") {
            tracks
        } else {
            tracks.filter { it.format.equals(formatFilter, ignoreCase = true) }
        }

        val filtered = if (query.isBlank()) {
            formatFiltered
        } else {
            val q = query.trim().lowercase(Locale.ROOT)
            formatFiltered.filter {
                it.title.lowercase(Locale.ROOT).contains(q) ||
                    it.artist.lowercase(Locale.ROOT).contains(q) ||
                    it.album.lowercase(Locale.ROOT).contains(q) ||
                    it.format.lowercase(Locale.ROOT).contains(q)
            }
        }

        MobileServerUiState(
            tracks = tracks,
            filteredTracks = filtered,
            searchQuery = query,
            selectedFormat = formatFilter,
            formatCounts = counts,
            isScanning = scanMeta.isScanning,
            scanStageText = scanMeta.stageText,
            scanNewCount = scanMeta.newCount,
            scanLatestTitle = scanMeta.latestTitle,
            lastScanSummary = scanMeta.summary,
            hasScannedOnce = scanMeta.hasScannedOnce,
            isServerRunning = serverMeta.isRunning,
            serverUrl = serverMeta.serverUrl,
            qrBitmap = serverMeta.qrBitmap,
            statusText = serverMeta.statusText,
            activityLog = serverMeta.activityLog,
            hasAudioPermission = hasPerm,
            savedFolderCount = folderMeta.folderCount,
            selectedFolders = folderMeta.selectedFolders,
            excludedTrackCount = folderMeta.excludedTrackCount,
            previewTrackId = previewId,
            isPreviewPlaying = isPreview,
            deviceName = httpServer.deviceDisplayName,
            discoveredTvs = tvMeta.discoveredTvs,
            isScanningTvs = tvMeta.isScanningTvs,
            manualTvIp = tvMeta.manualTvIp,
            tvStatus = tvMeta.tvStatus,
            tvConnectionMessage = tvMeta.tvMessage,
            castingTrackId = tvMeta.castingTrackId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MobileServerUiState(
            hasAudioPermission = _hasAudioPermission.value,
            hasScannedOnce = _hasScannedOnce.value,
            savedFolderCount = _savedFolderCount.value,
            selectedFolders = _selectedFolders.value,
            excludedTrackCount = _excludedTrackCount.value,
            manualTvIp = _manualTvIp.value,
            deviceName = httpServer.deviceDisplayName
        )
    )

    init {
        loadCachedTracksAsync()
        startTvUdpListener()
        val lastTv = _manualTvIp.value.trim()
        if (lastTv.isNotBlank()) {
            viewModelScope.launch {
                val info = TvRemoteClient.probeTvServer(lastTv, connectTimeoutMs = 900, readTimeoutMs = 1200)
                if (info != null) {
                    mergeDiscoveredTv(info)
                    connectToTv(info.url)
                }
            }
        }
    }

    private fun loadCachedTracksAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = readCachedTracksFromDisk()
            if (loaded.isNotEmpty()) {
                _tracks.value = loaded
                _lastScanSummary.value = "Đã lưu ${loaded.size} bài hát — nhấn Quét lại khi có nhạc mới"
            }
        }
    }

    fun onPermissionUpdated(granted: Boolean) {
        val current = granted || checkAudioPermission()
        _hasAudioPermission.value = current
        // Intentionally NEVER auto-scan here: scanning runs ONLY when user taps Quét nhạc
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFormat(format: String) {
        _selectedFormat.value = format
    }

    fun scanMusic(forceRestart: Boolean = false) {
        if (_isScanning.value) {
            if (!forceRestart) return
            scanJob?.cancel()
        }

        var jobRef: Job? = null
        jobRef = viewModelScope.launch {
            val previousIds = _tracks.value.map { it.id }.toSet()
            _isScanning.value = true
            _scanStageText.value = "Đang khởi tạo quét nhanh..."
            _scanNewCount.value = 0
            _scanLatestTitle.value = null
            _lastScanSummary.value = null

            try {
                val treeUris = loadSavedTreeUris()
                val fileUris = loadSavedFileUris()
                val excludedKeys = loadExcludedKeys()
                _savedFolderCount.value = treeUris.size + fileUris.size
                _selectedFolders.value = buildSelectedFolderItems()
                _excludedTrackCount.value = loadExcludedTrackIds().size

                val scanned = scanner.scanAll(
                    extraTreeUris = treeUris,
                    extraFileUris = fileUris,
                    excludedKeys = excludedKeys,
                    onProgress = { progress ->
                        _tracks.value = progress.tracks
                        _scanStageText.value = progress.stageLabel
                        val newlyAdded = progress.tracks.count { !previousIds.contains(it.id) }
                        _scanNewCount.value = newlyAdded
                        progress.latestTrackTitle?.let { _scanLatestTitle.value = it }
                    }
                )
                _tracks.value = scanned
                persistTracksAndMarkScanned(scanned)

                val newlyAdded = scanned.count { !previousIds.contains(it.id) }
                _scanNewCount.value = newlyAdded
                val summary = if (newlyAdded > 0 && previousIds.isNotEmpty()) {
                    "Quét xong & đã lưu: ${scanned.size} bài (+$newlyAdded mới)"
                } else {
                    "Quét xong & đã lưu: ${scanned.size} bài hát"
                }
                _lastScanSummary.value = summary
                appendActivityLog(summary)
            } catch (_: CancellationException) {
                // Save whatever was found before stopping so user does not need to rescan
                val current = _tracks.value
                if (current.isNotEmpty()) {
                    persistTracksAndMarkScanned(current)
                }
            } finally {
                if (scanJob == jobRef) {
                    _isScanning.value = false
                    _scanLatestTitle.value = null
                }
            }
        }
        scanJob = jobRef
    }

    fun stopScan() {
        val wasScanning = _isScanning.value || scanJob?.isActive == true
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
        _scanLatestTitle.value = null
        if (wasScanning) {
            val current = _tracks.value
            viewModelScope.launch(Dispatchers.IO) {
                persistTracksAndMarkScanned(current)
            }
            val count = current.size
            _scanStageText.value = "Đã dừng quét nhạc"
            _lastScanSummary.value = "Đã dừng quét — đã lưu $count bài hát"
            appendActivityLog("Đã dừng quét & lưu ($count bài)")
        }
    }

    fun addSafFolder(uri: Uri) {
        val app = getApplication<Application>()
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            app.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }
        val current = prefs.getStringSet(KEY_SAF_TREE_URIS, emptySet()).orEmpty().toMutableSet()
        current.add(uri.toString())
        prefs.edit().putStringSet(KEY_SAF_TREE_URIS, current).apply()
        _savedFolderCount.value = current.size + loadSavedFileUris().size
        _selectedFolders.value = buildSelectedFolderItems()
        val label = PhoneMusicScanner.formatTreeDisplayName(uri)
        appendActivityLog("Đã thêm thư mục: $label")

        // Incremental scan of ONLY the selected folder (no full device rescan)
        viewModelScope.launch {
            val excludedKeys = loadExcludedKeys()
            val added = scanner.scanSingleTree(uri, excludedKeys)
            if (added.isNotEmpty()) {
                val merged = mergeTracks(_tracks.value, added)
                _tracks.value = merged
                persistTracksAndMarkScanned(merged)
                _lastScanSummary.value = "Đã thêm ${added.size} bài từ thư mục $label"
            } else {
                _lastScanSummary.value = "Không có bài mới trong thư mục $label"
            }
        }
    }

    fun removeSafFolder(uriString: String) {
        val app = getApplication<Application>()
        val current = prefs.getStringSet(KEY_SAF_TREE_URIS, emptySet()).orEmpty().toMutableSet()
        if (!current.remove(uriString)) return
        prefs.edit().putStringSet(KEY_SAF_TREE_URIS, current).apply()

        runCatching {
            app.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val label = runCatching { PhoneMusicScanner.formatTreeDisplayName(Uri.parse(uriString)) }
            .getOrDefault("Thư mục")
        _savedFolderCount.value = current.size + loadSavedFileUris().size
        _selectedFolders.value = buildSelectedFolderItems()

        val treeAuthorityPrefix = uriString.substringBefore("/tree/")
        val treeId = runCatching { Uri.parse(uriString).lastPathSegment }.getOrNull().orEmpty()
        val updated = _tracks.value.filterNot { t ->
            val cu = t.contentUri.orEmpty()
            (cu.startsWith(treeAuthorityPrefix) && treeId.isNotBlank() && cu.contains(Uri.encode(treeId))) ||
                (t.album == label && cu.startsWith("content://com.android."))
        }
        _tracks.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            persistTracksAndMarkScanned(updated)
        }
        _lastScanSummary.value = "Đã bỏ thư mục: $label (${updated.size} bài còn lại)"
        appendActivityLog("Đã bỏ thư mục: $label")
    }

    fun addSafFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        val current = prefs.getStringSet(KEY_SAF_FILE_URIS, emptySet()).orEmpty().toMutableSet()
        val excludedKeys = loadExcludedKeys().toMutableSet()
        uris.forEach { uri ->
            try {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val uriStr = uri.toString()
            current.add(uriStr)
            excludedKeys.remove(uriStr)
        }
        prefs.edit()
            .putStringSet(KEY_SAF_FILE_URIS, current)
            .putStringSet(KEY_EXCLUDED_TRACK_KEYS, excludedKeys)
            .apply()
        _savedFolderCount.value = loadSavedTreeUris().size + current.size
        appendActivityLog("Đã thêm ${uris.size} file nhạc đã chọn")

        // Incremental parse ONLY picked files (no full device rescan)
        viewModelScope.launch {
            val added = scanner.parsePickedFiles(uris, excludedKeys)
            if (added.isNotEmpty()) {
                val merged = mergeTracks(_tracks.value, added)
                _tracks.value = merged
                persistTracksAndMarkScanned(merged)
                _lastScanSummary.value = "Đã thêm ${added.size} bài hát đã chọn"
            }
        }
    }

    fun removeTrack(track: MobileAudioTrack) {
        if (_previewTrackId.value == track.id) {
            stopPreview()
        }

        if (!track.contentUri.isNullOrBlank()) {
            val savedFiles = prefs.getStringSet(KEY_SAF_FILE_URIS, emptySet()).orEmpty().toMutableSet()
            if (savedFiles.remove(track.contentUri)) {
                prefs.edit().putStringSet(KEY_SAF_FILE_URIS, savedFiles).apply()
                _savedFolderCount.value = loadSavedTreeUris().size + savedFiles.size
            }
        }

        val excludedIds = loadExcludedTrackIds().toMutableSet()
        excludedIds.add(track.id)

        val excludedKeys = loadExcludedKeys().toMutableSet()
        excludedKeys.add(track.id)
        if (track.filePath.isNotBlank()) excludedKeys.add(track.filePath)
        if (!track.contentUri.isNullOrBlank()) excludedKeys.add(track.contentUri)

        prefs.edit()
            .putStringSet(KEY_EXCLUDED_TRACK_IDS, excludedIds)
            .putStringSet(KEY_EXCLUDED_TRACK_KEYS, excludedKeys)
            .apply()

        val updated = _tracks.value.filterNot { it.id == track.id }
        _tracks.value = updated
        _excludedTrackCount.value = excludedIds.size
        viewModelScope.launch(Dispatchers.IO) {
            persistTracksAndMarkScanned(updated)
        }
        appendActivityLog("Đã bỏ bài hát: ${track.title}")
    }

    fun restoreExcludedTracks() {
        prefs.edit()
            .remove(KEY_EXCLUDED_TRACK_IDS)
            .remove(KEY_EXCLUDED_TRACK_KEYS)
            .apply()
        _excludedTrackCount.value = 0
        appendActivityLog("Đã khôi phục toàn bộ bài hát đã bỏ")
        scanMusic(forceRestart = true)
    }

    fun toggleServer() {
        if (_isServerRunning.value) {
            stopServer()
        } else {
            startServer()
        }
    }

    fun startServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = httpServer.start()
                val qr = QrGenerator.generate(url, 480)
                withContext(Dispatchers.Main) {
                    acquireWakeLock()
                    _serverUrl.value = url
                    _qrBitmap.value = qr
                    _isServerRunning.value = true
                    appendActivityLog("Bật Server nhạc Wi-Fi tại $url")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isServerRunning.value = false
                    _statusText.value = "Không thể bật Server Wi-Fi: ${e.message ?: "Lỗi mạng"}"
                }
            }
        }
    }

    fun stopServer() {
        httpServer.stop()
        releaseWakeLock()
        _isServerRunning.value = false
        _serverUrl.value = null
        _qrBitmap.value = null
        appendActivityLog("Đã tắt Server nhạc Wi-Fi")
    }

    // --- TV Discovery, Casting & Remote Control ---

    private fun startTvUdpListener() {
        if (tvUdpJob?.isActive == true) return
        tvUdpJob = viewModelScope.launch(Dispatchers.IO) {
            val ownIp = MobileHttpMusicServer.getLocalIpv4Address()
            TvRemoteClient.listenTvUdpBeacons(ownIp) { tv ->
                mergeDiscoveredTv(tv)
                if (!_tvStatus.value.connected) {
                    connectToTv(tv.url)
                }
            }
        }
    }

    private fun mergeDiscoveredTv(tv: DiscoveredTvDevice) {
        _discoveredTvs.update { current ->
            val mutable = current.toMutableList()
            val idx = mutable.indexOfFirst { it.url == tv.url }
            if (idx >= 0) mutable[idx] = tv else mutable.add(0, tv)
            mutable
        }
    }

    fun scanForTvs() {
        if (_isScanningTvs.value) return
        viewModelScope.launch {
            _isScanningTvs.value = true
            _tvConnectionMessage.value = "Đang tìm TV Music trong cùng mạng Wi-Fi..."
            try {
                val found = TvRemoteClient.scanSubnetForTvs()
                found.forEach { mergeDiscoveredTv(it) }
                if (found.isNotEmpty()) {
                    val target = found.first()
                    connectToTv(target.url)
                } else if (_discoveredTvs.value.isEmpty()) {
                    _tvConnectionMessage.value = "Chưa thấy TV Music — hãy mở TV Music trên Tivi hoặc nhập IP Tivi."
                }
            } catch (e: Exception) {
                _tvConnectionMessage.value = "Lỗi tìm Tivi: ${e.message}"
            } finally {
                _isScanningTvs.value = false
            }
        }
    }

    fun updateManualTvIp(raw: String) {
        _manualTvIp.value = raw
    }

    fun connectToTv(rawUrl: String) {
        val normalized = TvRemoteClient.normalizeTvUrl(rawUrl)
        if (normalized.isBlank()) {
            _tvConnectionMessage.value = "Nhập địa chỉ IP Tivi (ví dụ: 192.168.1.20)"
            return
        }
        viewModelScope.launch {
            _tvConnectionMessage.value = "Đang kết nối $normalized..."
            val status = TvRemoteClient.fetchTvStatus(normalized)
            if (status != null) {
                _tvStatus.value = status
                _manualTvIp.value = normalized
                prefs.edit().putString(KEY_LAST_TV_URL, normalized).apply()
                mergeDiscoveredTv(DiscoveredTvDevice(url = normalized, deviceName = status.deviceName))
                _tvConnectionMessage.value = "Đã kết nối: ${status.deviceName}"
                startTvStatusPolling(normalized)
            } else {
                _tvConnectionMessage.value = "Không kết nối được tới $normalized — kiểm tra TV Music đang mở."
            }
        }
    }

    fun disconnectFromTv() {
        tvPollJob?.cancel()
        tvPollJob = null
        _tvStatus.value = TvRemoteStatus()
        _tvConnectionMessage.value = "Đã ngắt kết nối Tivi"
    }

    private fun startTvStatusPolling(tvUrl: String) {
        tvPollJob?.cancel()
        tvPollJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val latest = TvRemoteClient.fetchTvStatus(tvUrl)
                if (latest != null) {
                    _tvStatus.value = latest
                }
            }
        }
    }

    fun playTrackOnTv(track: MobileAudioTrack) {
        stopPreview()
        viewModelScope.launch {
            _castingTrackId.value = track.id
            // Auto-start mobile HTTP server if needed so TV can stream immediately
            val phoneUrl = withContext(Dispatchers.IO) {
                val url = httpServer.start()
                val qr = QrGenerator.generate(url, 480)
                withContext(Dispatchers.Main) {
                    acquireWakeLock()
                    _serverUrl.value = url
                    _qrBitmap.value = qr
                    _isServerRunning.value = true
                }
                url
            }

            // Pick target TV URL
            var targetTvUrl = _tvStatus.value.takeIf { it.connected }?.tvUrl
                ?: _discoveredTvs.value.firstOrNull()?.url
                ?: TvRemoteClient.normalizeTvUrl(_manualTvIp.value)

            if (targetTvUrl.isBlank()) {
                _tvConnectionMessage.value = "Đang tự động tìm TV Music trên Wi-Fi..."
                val found = TvRemoteClient.scanSubnetForTvs()
                found.forEach { mergeDiscoveredTv(it) }
                targetTvUrl = found.firstOrNull()?.url.orEmpty()
            }

            if (targetTvUrl.isBlank()) {
                _castingTrackId.value = null
                _tvConnectionMessage.value = "Chưa tìm thấy Tivi nào. Vui lòng nhập IP Tivi hoặc mở TV Music trước."
                return@launch
            }

            val queue = uiState.value.filteredTracks.ifEmpty { _tracks.value }
            val result = TvRemoteClient.playTrackOnTv(targetTvUrl, phoneUrl, track, queue)
            _castingTrackId.value = null

            result.onSuccess { msg ->
                _tvConnectionMessage.value = msg
                _manualTvIp.value = targetTvUrl
                prefs.edit().putString(KEY_LAST_TV_URL, targetTvUrl).apply()
                appendActivityLog(msg)
                val status = TvRemoteClient.fetchTvStatus(targetTvUrl)
                if (status != null) {
                    _tvStatus.value = status
                }
                if (tvPollJob?.isActive != true) {
                    startTvStatusPolling(targetTvUrl)
                }
            }.onFailure { err ->
                _tvConnectionMessage.value = "Không thể phát lên Tivi: ${err.message ?: "Kiểm tra cùng Wi-Fi"}"
            }
        }
    }

    fun sendTvControl(action: String, positionMs: Long? = null) {
        val tvUrl = _tvStatus.value.takeIf { it.connected }?.tvUrl
            ?: _discoveredTvs.value.firstOrNull()?.url
            ?: TvRemoteClient.normalizeTvUrl(_manualTvIp.value)
        if (tvUrl.isBlank()) {
            _tvConnectionMessage.value = "Chưa kết nối Tivi để điều khiển"
            return
        }
        viewModelScope.launch {
            val ok = TvRemoteClient.sendTvControl(tvUrl, action, positionMs)
            if (ok) {
                delay(180L)
                TvRemoteClient.fetchTvStatus(tvUrl)?.let { _tvStatus.value = it }
            }
        }
    }

    // --- Local Phone Preview ---

    fun togglePreview(track: MobileAudioTrack) {
        if (_previewTrackId.value == track.id && _isPreviewPlaying.value) {
            stopPreview()
            return
        }
        stopPreview()
        val app = getApplication<Application>()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (track.filePath.isNotBlank()) {
                    setDataSource(track.filePath)
                } else if (!track.contentUri.isNullOrBlank()) {
                    setDataSource(app, Uri.parse(track.contentUri))
                } else {
                    release()
                    return
                }
                setOnPreparedListener {
                    it.start()
                    _previewTrackId.value = track.id
                    _isPreviewPlaying.value = true
                }
                setOnCompletionListener {
                    _isPreviewPlaying.value = false
                    _previewTrackId.value = null
                }
                setOnErrorListener { _, _, _ ->
                    _isPreviewPlaying.value = false
                    _previewTrackId.value = null
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (_: Exception) {
            stopPreview()
        }
    }

    fun stopPreview() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _isPreviewPlaying.value = false
        _previewTrackId.value = null
    }

    private fun mergeTracks(
        existing: List<MobileAudioTrack>,
        additions: List<MobileAudioTrack>
    ): List<MobileAudioTrack> {
        val map = LinkedHashMap<String, MobileAudioTrack>(existing.size + additions.size)
        existing.forEach { map[it.id] = it }
        additions.forEach { map[it.id] = it }
        return map.values.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    private suspend fun persistTracksAndMarkScanned(tracks: List<MobileAudioTrack>) = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray()
            tracks.forEach { t ->
                arr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("album", t.album)
                        put("durationMs", t.durationMs)
                        put("filePath", t.filePath)
                        put("contentUri", t.contentUri ?: "")
                        put("format", t.format)
                        put("bitrate", t.bitrate)
                        put("fileSizeBytes", t.fileSizeBytes)
                        put("parentDirectory", t.parentDirectory)
                        put("albumArtUri", t.albumArtUri ?: "")
                    }
                )
            }
            cacheFile.writeText(arr.toString())
            prefs.edit().putBoolean(KEY_HAS_SCANNED_ONCE, true).apply()
            _hasScannedOnce.value = true
        } catch (_: Exception) {
        }
    }

    private fun readCachedTracksFromDisk(): List<MobileAudioTrack> {
        return try {
            if (!cacheFile.exists() || cacheFile.length() == 0L) return emptyList()
            val arr = JSONArray(cacheFile.readText())
            val list = ArrayList<MobileAudioTrack>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    MobileAudioTrack(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist", "Nghệ sĩ chưa rõ"),
                        album = obj.optString("album", "Điện thoại"),
                        durationMs = obj.optLong("durationMs", 0L),
                        filePath = obj.optString("filePath", ""),
                        contentUri = obj.optString("contentUri", "").takeIf { it.isNotBlank() },
                        format = obj.optString("format", "MP3"),
                        bitrate = obj.optString("bitrate", "320 kbps"),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        parentDirectory = obj.optString("parentDirectory", ""),
                        albumArtUri = obj.optString("albumArtUri", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun checkAudioPermission(): Boolean {
        val app = getApplication<Application>()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadSavedTreeUris(): Set<Uri> {
        return prefs.getStringSet(KEY_SAF_TREE_URIS, emptySet())
            .orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .toSet()
    }

    private fun loadSavedFileUris(): Set<Uri> {
        return prefs.getStringSet(KEY_SAF_FILE_URIS, emptySet())
            .orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .toSet()
    }

    private fun loadExcludedTrackIds(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED_TRACK_IDS, emptySet()).orEmpty()
    }

    private fun loadExcludedKeys(): Set<String> {
        return prefs.getStringSet(KEY_EXCLUDED_TRACK_KEYS, emptySet()).orEmpty()
    }

    private fun buildSelectedFolderItems(): List<SelectedFolderItem> {
        return loadSavedTreeUris()
            .map { uri ->
                SelectedFolderItem(
                    uriString = uri.toString(),
                    displayName = PhoneMusicScanner.formatTreeDisplayName(uri)
                )
            }
            .sortedBy { it.displayName.lowercase(Locale.ROOT) }
    }

    private fun appendActivityLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _activityLog.update { current ->
            (listOf("$stamp · $message") + current).take(12)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MobileServerWifi::ServerLock")?.apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        tvUdpJob?.cancel()
        tvPollJob?.cancel()
        stopPreview()
        httpServer.stop()
        releaseWakeLock()
    }

    companion object {
        private const val PREFS_NAME = "mobile_server_prefs"
        private const val CACHE_FILE_NAME = "cached_mobile_tracks.json"
        private const val KEY_HAS_SCANNED_ONCE = "has_scanned_once"
        private const val KEY_LAST_TV_URL = "last_tv_url"
        private const val KEY_SAF_TREE_URIS = "saf_tree_uris"
        private const val KEY_SAF_FILE_URIS = "saf_file_uris"
        private const val KEY_EXCLUDED_TRACK_IDS = "excluded_track_ids"
        private const val KEY_EXCLUDED_TRACK_KEYS = "excluded_track_keys"
    }
}
