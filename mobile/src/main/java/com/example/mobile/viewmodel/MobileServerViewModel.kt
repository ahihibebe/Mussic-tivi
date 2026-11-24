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
import com.example.mobile.scanner.PhoneMusicScanner
import com.example.mobile.server.MobileHttpMusicServer
import com.example.mobile.server.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MobileServerUiState(
    val tracks: List<MobileAudioTrack> = emptyList(),
    val filteredTracks: List<MobileAudioTrack> = emptyList(),
    val searchQuery: String = "",
    val isScanning: Boolean = false,
    val scanStageText: String = "",
    val scanNewCount: Int = 0,
    val scanLatestTitle: String? = null,
    val lastScanSummary: String? = null,
    val isServerRunning: Boolean = false,
    val serverUrl: String? = null,
    val qrBitmap: ImageBitmap? = null,
    val statusText: String = "Nhấn \"Đặt máy này làm Server nhạc\" để Tivi quét và phát trực tiếp",
    val activityLog: List<String> = emptyList(),
    val hasAudioPermission: Boolean = false,
    val savedFolderCount: Int = 0,
    val previewTrackId: String? = null,
    val isPreviewPlaying: Boolean = false,
    val deviceName: String = "Android"
)

class MobileServerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scanner = PhoneMusicScanner(application)
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _tracks = MutableStateFlow<List<MobileAudioTrack>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isScanning = MutableStateFlow(false)
    private val _scanStageText = MutableStateFlow("")
    private val _scanNewCount = MutableStateFlow(0)
    private val _scanLatestTitle = MutableStateFlow<String?>(null)
    private val _lastScanSummary = MutableStateFlow<String?>(null)
    private val _isServerRunning = MutableStateFlow(false)
    private val _serverUrl = MutableStateFlow<String?>(null)
    private val _qrBitmap = MutableStateFlow<ImageBitmap?>(null)
    private val _statusText = MutableStateFlow("Chạm \"Đặt máy này làm Server nhạc\" để Tivi cùng Wi-Fi phát trực tiếp")
    private val _activityLog = MutableStateFlow<List<String>>(emptyList())
    private val _hasAudioPermission = MutableStateFlow(checkAudioPermission())
    private val _savedFolderCount = MutableStateFlow(loadSavedTreeUris().size + loadSavedFileUris().size)
    private val _previewTrackId = MutableStateFlow<String?>(null)
    private val _isPreviewPlaying = MutableStateFlow(false)

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
        val summary: String?
    )

    private val scanMetaFlow = combine(
        _isScanning,
        _scanStageText,
        _scanNewCount,
        _scanLatestTitle,
        _lastScanSummary
    ) { scanning, stage, newCount, latest, summary ->
        ScanVisualMeta(scanning, stage, newCount, latest, summary)
    }

    val uiState: StateFlow<MobileServerUiState> = combine(
        _tracks,
        _searchQuery,
        scanMetaFlow,
        _isServerRunning,
        _serverUrl,
        _qrBitmap,
        _statusText,
        _activityLog,
        _hasAudioPermission,
        _savedFolderCount,
        _previewTrackId,
        _isPreviewPlaying
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tracks = values[0] as List<MobileAudioTrack>
        val query = values[1] as String
        val scanMeta = values[2] as ScanVisualMeta
        val isRunning = values[3] as Boolean
        val serverUrl = values[4] as String?
        val qrBitmap = values[5] as ImageBitmap?
        val statusText = values[6] as String
        @Suppress("UNCHECKED_CAST")
        val activityLog = values[7] as List<String>
        val hasPerm = values[8] as Boolean
        val folderCount = values[9] as Int
        val previewId = values[10] as String?
        val isPreview = values[11] as Boolean

        val filtered = if (query.isBlank()) {
            tracks
        } else {
            val q = query.trim().lowercase(Locale.ROOT)
            tracks.filter {
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
            isScanning = scanMeta.isScanning,
            scanStageText = scanMeta.stageText,
            scanNewCount = scanMeta.newCount,
            scanLatestTitle = scanMeta.latestTitle,
            lastScanSummary = scanMeta.summary,
            isServerRunning = isRunning,
            serverUrl = serverUrl,
            qrBitmap = qrBitmap,
            statusText = statusText,
            activityLog = activityLog,
            hasAudioPermission = hasPerm,
            savedFolderCount = folderCount,
            previewTrackId = previewId,
            isPreviewPlaying = isPreview,
            deviceName = httpServer.deviceDisplayName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MobileServerUiState(
            hasAudioPermission = _hasAudioPermission.value,
            savedFolderCount = _savedFolderCount.value,
            deviceName = httpServer.deviceDisplayName
        )
    )

    init {
        scanMusic()
    }

    fun onPermissionUpdated(granted: Boolean) {
        val current = granted || checkAudioPermission()
        val changed = current != _hasAudioPermission.value
        _hasAudioPermission.value = current
        if (current && (changed || _tracks.value.isEmpty())) {
            scanMusic()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun scanMusic() {
        if (_isScanning.value) return
        viewModelScope.launch {
            val previousIds = _tracks.value.map { it.id }.toSet()
            _isScanning.value = true
            _scanStageText.value = "Đang khởi tạo quét kho nhạc..."
            _scanNewCount.value = 0
            _scanLatestTitle.value = null
            _lastScanSummary.value = null

            try {
                val treeUris = loadSavedTreeUris()
                val fileUris = loadSavedFileUris()
                _savedFolderCount.value = treeUris.size + fileUris.size
                val scanned = scanner.scanAll(
                    extraTreeUris = treeUris,
                    extraFileUris = fileUris,
                    onProgress = { progress ->
                        _tracks.value = progress.tracks
                        _scanStageText.value = progress.stageLabel
                        val newlyAdded = progress.tracks.count { !previousIds.contains(it.id) }
                        _scanNewCount.value = newlyAdded
                        progress.latestTrackTitle?.let { _scanLatestTitle.value = it }
                    }
                )
                _tracks.value = scanned
                val newlyAdded = scanned.count { !previousIds.contains(it.id) }
                _scanNewCount.value = newlyAdded
                val summary = if (newlyAdded > 0 && previousIds.isNotEmpty()) {
                    "Quét xong: ${scanned.size} bài hát (+$newlyAdded bài mới)"
                } else {
                    "Quét xong: tìm thấy ${scanned.size} bài hát trên máy"
                }
                _lastScanSummary.value = summary
                appendActivityLog(summary)
            } finally {
                _isScanning.value = false
                _scanLatestTitle.value = null
            }
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
        appendActivityLog("Đã thêm thư mục nhạc tùy chọn")
        scanMusic()
    }

    fun addSafFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        val current = prefs.getStringSet(KEY_SAF_FILE_URIS, emptySet()).orEmpty().toMutableSet()
        uris.forEach { uri ->
            try {
                app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            current.add(uri.toString())
        }
        prefs.edit().putStringSet(KEY_SAF_FILE_URIS, current).apply()
        _savedFolderCount.value = loadSavedTreeUris().size + current.size
        appendActivityLog("Đã thêm ${uris.size} file nhạc đã chọn")
        scanMusic()
    }

    fun toggleServer() {
        if (_isServerRunning.value) {
            stopServer()
        } else {
            startServer()
        }
    }

    fun startServer() {
        if (_tracks.value.isEmpty() && (_hasAudioPermission.value || _savedFolderCount.value > 0)) {
            scanMusic()
        }
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
        stopPreview()
        httpServer.stop()
        releaseWakeLock()
    }

    companion object {
        private const val PREFS_NAME = "mobile_server_prefs"
        private const val KEY_SAF_TREE_URIS = "saf_tree_uris"
        private const val KEY_SAF_FILE_URIS = "saf_file_uris"
    }
}
