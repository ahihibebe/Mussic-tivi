package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.AppUiStyle
import com.example.model.TvNavTab
import com.example.ui.components.NowPlayingMiniBar
import com.example.ui.components.QrBackgroundDialog
import com.example.ui.components.TvSidebar
import com.example.ui.screens.FolderBrowserScreen
import com.example.ui.screens.InfoScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SongsListScreen
import com.example.ui.screens.WifiPhoneScreen
import com.example.ui.theme.AppleTvDeepBg
import com.example.ui.theme.AppleTvGlowBottom
import com.example.ui.theme.AppleTvGlowTop
import com.example.ui.theme.AppleTvMidBg
import com.example.ui.theme.LightBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicPlayerViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiStyle by viewModel.uiStyle.collectAsStateWithLifecycle()
            val customBackgroundPath by viewModel.customBackgroundPath.collectAsStateWithLifecycle()

            MyApplicationTheme(
                uiStyle = uiStyle,
                hasCustomBackground = customBackgroundPath != null
            ) {
                TvMusicApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        if (granted && viewModel.allTracks.value.isEmpty() && !viewModel.isScanning.value) {
            viewModel.scanLibrary()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                viewModel.togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                viewModel.playbackManager.resume()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                viewModel.playbackManager.pause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                viewModel.skipNext()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                viewModel.skipPrevious()
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                viewModel.seekForward()
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                viewModel.seekRewind()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Composable
fun TvMusicApp(viewModel: MusicPlayerViewModel) {
    val context = LocalContext.current

    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val browsingFolder by viewModel.browsingFolder.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val filteredTracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val folderTracks by viewModel.currentFolderTracks.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val equalizerPreset by viewModel.equalizerPreset.collectAsStateWithLifecycle()
    val sleepTimerSeconds by viewModel.sleepTimerSeconds.collectAsStateWithLifecycle()
    val visualizerBands by viewModel.visualizerBands.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

    val uiStyle by viewModel.uiStyle.collectAsStateWithLifecycle()
    val coverArtStyle by viewModel.coverArtStyle.collectAsStateWithLifecycle()
    val customBackgroundPath by viewModel.customBackgroundPath.collectAsStateWithLifecycle()
    val backgroundOpacity by viewModel.backgroundOpacity.collectAsStateWithLifecycle()
    val glassBlurRadius by viewModel.glassBlurRadius.collectAsStateWithLifecycle()
    val isQrDialogVisible by viewModel.isQrDialogVisible.collectAsStateWithLifecycle()
    val qrServerUrl by viewModel.qrServerUrl.collectAsStateWithLifecycle()
    val qrUploadStatus by viewModel.qrUploadStatus.collectAsStateWithLifecycle()
    val wifiMusicRole by viewModel.wifiMusicRole.collectAsStateWithLifecycle()
    val isWifiServerRunning by viewModel.isWifiServerRunning.collectAsStateWithLifecycle()
    val wifiServerUrl by viewModel.wifiServerUrl.collectAsStateWithLifecycle()
    val wifiActivityStatus by viewModel.wifiActivityStatus.collectAsStateWithLifecycle()
    val wifiTracks by viewModel.wifiTracks.collectAsStateWithLifecycle()
    val discoveredPhoneServers by viewModel.discoveredPhoneServers.collectAsStateWithLifecycle()
    val isScanningLanServers by viewModel.isScanningLanServers.collectAsStateWithLifecycle()
    val connectedPhoneServer by viewModel.connectedPhoneServer.collectAsStateWithLifecycle()
    val remotePhoneTracks by viewModel.remotePhoneTracks.collectAsStateWithLifecycle()
    val isLoadingRemoteTracks by viewModel.isLoadingRemoteTracks.collectAsStateWithLifecycle()
    val remoteError by viewModel.remoteError.collectAsStateWithLifecycle()
    val manualServerIp by viewModel.manualServerIp.collectAsStateWithLifecycle()

    // Storage permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLibrary()
        }
    }

    // Storage Access Framework folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
            }
            Toast.makeText(context, "Đã thêm thư mục quét nhạc", Toast.LENGTH_SHORT).show()
            viewModel.addSafFolder(uri)
        }
    }

    // Zero-permission Android Photo Picker for custom TV background
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setBackgroundFromUri(uri)
            Toast.makeText(context, "Đã cập nhật ảnh nền TV!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Base theme backdrop (Apple TV Cinema Ambient or Crisp Light)
        if (uiStyle == AppUiStyle.APPLE_TV_GLASS) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(AppleTvDeepBg, AppleTvMidBg, AppleTvDeepBg)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AppleTvGlowTop.copy(alpha = 0.65f), Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height * 0.20f),
                        radius = size.maxDimension * 0.55f
                    ),
                    center = Offset(size.width * 0.22f, size.height * 0.20f),
                    radius = size.maxDimension * 0.55f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AppleTvGlowBottom.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.80f),
                        radius = size.maxDimension * 0.60f
                    ),
                    center = Offset(size.width * 0.82f, size.height * 0.80f),
                    radius = size.maxDimension * 0.60f
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightBackground)
            )
        }

        // Layer 2: Custom User Background Photo (from QR upload or device Photo Picker)
        if (customBackgroundPath != null) {
            val imageBlurModifier = if (uiStyle == AppUiStyle.APPLE_TV_GLASS && glassBlurRadius > 0.5f) {
                Modifier.blur(glassBlurRadius.dp)
            } else {
                Modifier
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(customBackgroundPath!!))
                    .crossfade(true)
                    .build(),
                contentDescription = "Ảnh nền tùy chỉnh",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backgroundOpacity.coerceIn(0f, 1f))
                    .then(imageBlurModifier)
            )

            // Subtle readability veil over background image
            val scrimColor = if (uiStyle == AppUiStyle.APPLE_TV_GLASS) {
                Color(0xFF060A14).copy(alpha = 0.26f * backgroundOpacity)
            } else {
                Color.White.copy(alpha = 0.14f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
        }

        // Layer 3: Foreground TV App Chrome & Screens
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isCompactViewport = maxWidth < 680.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    TvSidebar(
                        selectedTab = currentTab,
                        onTabSelected = { tab -> viewModel.selectTab(tab) },
                        isPlaying = isPlaying,
                        isWifiServerRunning = isWifiServerRunning,
                        sleepTimerSeconds = sleepTimerSeconds,
                        onToggleStyle = { viewModel.toggleUiStyle() },
                        onOpenQrBackground = { viewModel.openQrBackgroundDialog() },
                        compact = isCompactViewport
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                    when (currentTab) {
                        TvNavTab.FOLDERS -> {
                            FolderBrowserScreen(
                                folders = allFolders,
                                browsingFolder = browsingFolder,
                                folderTracks = folderTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                isScanning = isScanning,
                                coverArtStyle = coverArtStyle,
                                onOpenFolder = { folder -> viewModel.openFolder(folder) },
                                onCloseFolder = { viewModel.closeFolder() },
                                onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                onPlayFolder = { folder, shuffle -> viewModel.playFolder(folder, shuffle) },
                                onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) },
                                onRefreshScan = { viewModel.scanLibrary() },
                                onPickStorageFolder = { folderPickerLauncher.launch(null) }
                            )
                        }

                        TvNavTab.ALL_SONGS -> {
                            SongsListScreen(
                                tracks = filteredTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                searchQuery = searchQuery,
                                selectedFormat = selectedFormat,
                                sortOption = sortOption,
                                coverArtStyle = coverArtStyle,
                                onSearchChanged = { viewModel.setSearchQuery(it) },
                                onFormatSelected = { viewModel.selectFormat(it) },
                                onSortChanged = { viewModel.setSortOption(it) },
                                onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                onPlayAll = { shuffle ->
                                    if (filteredTracks.isNotEmpty()) {
                                        val q = if (shuffle) filteredTracks.shuffled() else filteredTracks
                                        viewModel.playTrack(q.first(), q)
                                    }
                                },
                                onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) }
                            )
                        }

                        TvNavTab.NOW_PLAYING -> {
                            NowPlayingScreen(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                currentPositionMs = currentPositionMs,
                                durationMs = durationMs,
                                repeatMode = repeatMode,
                                isShuffle = isShuffle,
                                equalizerPreset = equalizerPreset,
                                sleepTimerSeconds = sleepTimerSeconds,
                                visualizerBands = visualizerBands,
                                playlist = playlist,
                                coverArtStyle = coverArtStyle,
                                onSetCoverArtStyle = { viewModel.setCoverArtStyle(it) },
                                onPlayPause = { viewModel.togglePlayPause() },
                                onSkipNext = { viewModel.skipNext() },
                                onSkipPrevious = { viewModel.skipPrevious() },
                                onSeekTo = { viewModel.seekTo(it) },
                                onSeekForward = { viewModel.seekForward() },
                                onSeekRewind = { viewModel.seekRewind() },
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                                onSetEqualizer = { viewModel.setEqualizer(it) },
                                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                                onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) },
                                onSelectQueueTrack = { track -> viewModel.playTrack(track, playlist) }
                            )
                        }

                        TvNavTab.PLAYLISTS -> {
                            PlaylistsScreen(
                                favoriteTracks = favoriteTracks,
                                recentTracks = recentTracks,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                coverArtStyle = coverArtStyle,
                                onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                onPlayAll = { tracks, shuffle ->
                                    if (tracks.isNotEmpty()) {
                                        val q = if (shuffle) tracks.shuffled() else tracks
                                        viewModel.playTrack(q.first(), q)
                                    }
                                },
                                onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) }
                            )
                        }

                        TvNavTab.WIFI_PHONE -> {
                            WifiPhoneScreen(
                                wifiMusicRole = wifiMusicRole,
                                isServerRunning = isWifiServerRunning,
                                serverUrl = wifiServerUrl,
                                activityStatus = wifiActivityStatus,
                                totalTracksCount = allTracks.size,
                                wifiTracks = wifiTracks,
                                discoveredServers = discoveredPhoneServers,
                                isScanningServers = isScanningLanServers,
                                connectedPhoneServer = connectedPhoneServer,
                                remoteTracks = remotePhoneTracks,
                                isLoadingRemoteTracks = isLoadingRemoteTracks,
                                remoteError = remoteError,
                                manualServerIp = manualServerIp,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                coverArtStyle = coverArtStyle,
                                onSelectRole = { viewModel.setWifiMusicRole(it) },
                                onToggleServer = { viewModel.toggleWifiMusicServer() },
                                onRefreshLibrary = { viewModel.scanLibrary() },
                                onScanLanServers = { viewModel.scanLanPhoneServers() },
                                onManualIpChanged = { viewModel.setManualServerIp(it) },
                                onConnectToServer = { viewModel.connectToPhoneServer(it) },
                                onDisconnectRemoteServer = { viewModel.disconnectRemotePhoneServer() },
                                onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                                onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) }
                            )
                        }

                        TvNavTab.SETTINGS -> {
                            SettingsScreen(
                                uiStyle = uiStyle,
                                coverArtStyle = coverArtStyle,
                                customBackgroundPath = customBackgroundPath,
                                backgroundOpacity = backgroundOpacity,
                                glassBlurRadius = glassBlurRadius,
                                currentEqualizer = equalizerPreset,
                                sleepTimerSeconds = sleepTimerSeconds,
                                totalTracksCount = allTracks.size,
                                totalFoldersCount = allFolders.size,
                                isScanning = isScanning,
                                onSetUiStyle = { viewModel.setUiStyle(it) },
                                onSetCoverArtStyle = { viewModel.setCoverArtStyle(it) },
                                onSetBackgroundOpacity = { viewModel.setBackgroundOpacity(it) },
                                onSetGlassBlurRadius = { viewModel.setGlassBlurRadius(it) },
                                onOpenQrBackground = { viewModel.openQrBackgroundDialog() },
                                onPickBackgroundImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onClearBackground = { viewModel.clearCustomBackground() },
                                onSetEqualizer = { viewModel.setEqualizer(it) },
                                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                                onRefreshScan = { viewModel.scanLibrary() },
                                onPickStorageFolder = { folderPickerLauncher.launch(null) }
                            )
                        }

                        TvNavTab.INFO -> {
                            InfoScreen()
                        }
                    }

                    if (currentTab != TvNavTab.NOW_PLAYING && currentTrack != null) {
                        NowPlayingMiniBar(
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            coverArtStyle = coverArtStyle,
                            onPlayPause = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onOpenFullscreen = { viewModel.selectTab(TvNavTab.NOW_PLAYING) },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

        // Layer 4: QR Code Background Transfer Dialog
        if (isQrDialogVisible) {
            QrBackgroundDialog(
                serverUrl = qrServerUrl,
                uploadStatus = qrUploadStatus,
                customBackgroundPath = customBackgroundPath,
                backgroundOpacity = backgroundOpacity,
                onOpacityChange = { viewModel.setBackgroundOpacity(it) },
                onPickLocalImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearBackground = { viewModel.clearCustomBackground() },
                onDismiss = { viewModel.closeQrBackgroundDialog() }
            )
        }
    }
}
