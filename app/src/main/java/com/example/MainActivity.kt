package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.TvNavTab
import com.example.ui.components.NowPlayingMiniBar
import com.example.ui.components.TvSidebar
import com.example.ui.screens.FolderBrowserScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SongsListScreen
import com.example.ui.theme.LightBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicPlayerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                TvMusicApp(viewModel = viewModel)
            }
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
                Toast.makeText(context, "Đã chọn thư mục: ${uri.path}", Toast.LENGTH_SHORT).show()
                viewModel.scanLibrary()
            } catch (e: Exception) {
                viewModel.scanLibrary()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground),
        containerColor = LightBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // TV Left Sidebar
            TvSidebar(
                selectedTab = currentTab,
                onTabSelected = { tab ->
                    viewModel.selectTab(tab)
                },
                isPlaying = isPlaying,
                sleepTimerSeconds = sleepTimerSeconds
            )

            // Main Content Area
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

                    TvNavTab.SETTINGS -> {
                        SettingsScreen(
                            currentEqualizer = equalizerPreset,
                            sleepTimerSeconds = sleepTimerSeconds,
                            totalTracksCount = allTracks.size,
                            totalFoldersCount = allFolders.size,
                            isScanning = isScanning,
                            onSetEqualizer = { viewModel.setEqualizer(it) },
                            onSetSleepTimer = { viewModel.setSleepTimer(it) },
                            onRefreshScan = { viewModel.scanLibrary() },
                            onPickStorageFolder = { folderPickerLauncher.launch(null) }
                        )
                    }
                }

                // Persistent Mini Player at Bottom (when browsing other tabs)
                if (currentTab != TvNavTab.NOW_PLAYING && currentTrack != null) {
                    NowPlayingMiniBar(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
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
