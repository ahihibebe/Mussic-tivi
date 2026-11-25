package com.example.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mobile.R
import com.example.mobile.model.MobileAudioTrack
import com.example.mobile.network.DiscoveredTvDevice
import com.example.mobile.network.TvRemoteStatus
import com.example.mobile.ui.theme.AmberBorder
import com.example.mobile.ui.theme.AmberNotice
import com.example.mobile.ui.theme.AmberSoftBg
import com.example.mobile.ui.theme.AmberTextDark
import com.example.mobile.ui.theme.CrimsonStop
import com.example.mobile.ui.theme.EmeraldActive
import com.example.mobile.ui.theme.EmeraldBorder
import com.example.mobile.ui.theme.EmeraldSoftBg
import com.example.mobile.ui.theme.EmeraldTextDark
import com.example.mobile.ui.theme.JetBrainsMonoFamily
import com.example.mobile.ui.theme.SkyBlueDeep
import com.example.mobile.ui.theme.SkyBluePrimary
import com.example.mobile.ui.theme.SkyBlueSoft
import com.example.mobile.ui.theme.SkyBlueTint
import com.example.mobile.ui.theme.SoftSlateBorder
import com.example.mobile.ui.theme.SoftSlateVariant
import com.example.mobile.ui.theme.SoftWhiteCanvas
import com.example.mobile.ui.theme.TextMutedSlate
import com.example.mobile.ui.theme.TextPrimaryDark
import com.example.mobile.ui.theme.TextSecondarySlate
import com.example.mobile.ui.theme.WhiteCardSurface
import com.example.mobile.ui.theme.WhitePageBg
import com.example.mobile.viewmodel.MobileServerUiState
import com.example.mobile.viewmodel.SelectedFolderItem

@Composable
fun MobileServerScreen(
    uiState: MobileServerUiState,
    onToggleServer: () -> Unit,
    onRescanMusic: () -> Unit,
    onStopScan: () -> Unit = {},
    onPickFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit = {},
    onPickFiles: () -> Unit,
    onRequestPermission: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFormat: (String) -> Unit = {},
    onPlayOnTv: (MobileAudioTrack) -> Unit = {},
    onScanForTvs: () -> Unit = {},
    onManualTvIpChange: (String) -> Unit = {},
    onConnectToTv: (String) -> Unit = {},
    onDisconnectFromTv: () -> Unit = {},
    onSendTvControl: (String, Long?) -> Unit = { _, _ -> },
    onTogglePreview: (MobileAudioTrack) -> Unit,
    onRemoveTrack: (MobileAudioTrack) -> Unit = {},
    onRestoreExcludedTracks: () -> Unit = {}
) {
    var showQrDialogCard by remember { mutableStateOf(false) }
    var copiedBanner by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        containerColor = WhitePageBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            WhitePageBg,
                            SoftWhiteCanvas,
                            WhitePageBg
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "header") {
                    MobileServerHeader(
                        deviceName = uiState.deviceName,
                        trackCount = uiState.tracks.size,
                        isRunning = uiState.isServerRunning
                    )
                }

                item(key = "hero_server_card") {
                    HeroServerControlCard(
                        uiState = uiState,
                        showQr = showQrDialogCard,
                        copiedBanner = copiedBanner,
                        onToggleServer = onToggleServer,
                        onToggleQr = { showQrDialogCard = !showQrDialogCard },
                        onCopyUrl = { url ->
                            clipboard.setText(AnnotatedString(url))
                            copiedBanner = true
                        }
                    )
                }

                item(key = "tv_remote_control_card") {
                    TvRemoteControlCard(
                        tvStatus = uiState.tvStatus,
                        discoveredTvs = uiState.discoveredTvs,
                        isScanningTvs = uiState.isScanningTvs,
                        manualTvIp = uiState.manualTvIp,
                        tvMessage = uiState.tvConnectionMessage,
                        onScanTvs = onScanForTvs,
                        onManualIpChange = onManualTvIpChange,
                        onConnectTv = onConnectToTv,
                        onDisconnectTv = onDisconnectFromTv,
                        onSendControl = onSendTvControl
                    )
                }

                if (!uiState.hasAudioPermission) {
                    item(key = "permission_banner") {
                        PermissionRequiredCard(onRequestPermission = onRequestPermission)
                    }
                }

                item(key = "actions_row") {
                    LibraryActionRow(
                        isScanning = uiState.isScanning,
                        hasTracksOrScanned = uiState.hasScannedOnce || uiState.tracks.isNotEmpty(),
                        savedFolderCount = uiState.savedFolderCount,
                        selectedFolders = uiState.selectedFolders,
                        onRescanMusic = onRescanMusic,
                        onStopScan = onStopScan,
                        onPickFolder = onPickFolder,
                        onRemoveFolder = onRemoveFolder,
                        onPickFiles = onPickFiles
                    )
                }

                item(key = "search_header") {
                    TrackListHeaderAndSearch(
                        totalTracks = uiState.tracks.size,
                        filteredCount = uiState.filteredTracks.size,
                        excludedTrackCount = uiState.excludedTrackCount,
                        searchQuery = uiState.searchQuery,
                        selectedFormat = uiState.selectedFormat,
                        formatCounts = uiState.formatCounts,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectFormat = onSelectFormat,
                        onRestoreExcludedTracks = onRestoreExcludedTracks
                    )
                }

                if (uiState.isScanning || !uiState.lastScanSummary.isNullOrBlank()) {
                    item(key = "live_scan_banner") {
                        LiveScanStatusBanner(
                            isScanning = uiState.isScanning,
                            stageText = uiState.scanStageText,
                            totalFound = uiState.tracks.size,
                            newCount = uiState.scanNewCount,
                            latestTitle = uiState.scanLatestTitle,
                            summary = uiState.lastScanSummary
                        )
                    }
                }

                if (uiState.isScanning && uiState.tracks.isEmpty()) {
                    item(key = "scanning_indicator") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = WhiteCardSurface),
                            border = BorderStroke(1.dp, SoftSlateBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = SkyBluePrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = uiState.scanStageText.ifBlank { "Đang quét nhạc trên điện thoại..." },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    }
                } else if (uiState.filteredTracks.isEmpty()) {
                    item(key = "empty_tracks") {
                        EmptyTracksCard(
                            hasPermission = uiState.hasAudioPermission,
                            hasQueryOrFilter = uiState.searchQuery.isNotBlank() || uiState.selectedFormat != "ALL",
                            onRescanMusic = onRescanMusic,
                            onResetFilter = {
                                onSearchQueryChange("")
                                onSelectFormat("ALL")
                            },
                            onPickFolder = onPickFolder,
                            onPickFiles = onPickFiles,
                            onRequestPermission = onRequestPermission
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredTracks,
                        key = { it.id }
                    ) { track ->
                        val isOnTv = uiState.tvStatus.connected &&
                            (uiState.tvStatus.trackId?.endsWith("_${track.id}") == true ||
                                uiState.tvStatus.title == track.title)
                        val isCasting = uiState.castingTrackId == track.id
                        MobileTrackItemCard(
                            track = track,
                            isPreviewPlaying = uiState.previewTrackId == track.id && uiState.isPreviewPlaying,
                            isPlayingOnTv = isOnTv,
                            isCasting = isCasting,
                            onPlayOnTv = { onPlayOnTv(track) },
                            onTogglePreview = { onTogglePreview(track) },
                            onRemoveTrack = { onRemoveTrack(track) }
                        )
                    }
                }

                item(key = "tv_guide_card") {
                    TvConnectionGuideCard(
                        serverUrl = uiState.serverUrl,
                        isRunning = uiState.isServerRunning,
                        activityLog = uiState.activityLog
                    )
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TvRemoteControlCard(
    tvStatus: TvRemoteStatus,
    discoveredTvs: List<DiscoveredTvDevice>,
    isScanningTvs: Boolean,
    manualTvIp: String,
    tvMessage: String?,
    onScanTvs: () -> Unit,
    onManualIpChange: (String) -> Unit,
    onConnectTv: (String) -> Unit,
    onDisconnectTv: () -> Unit,
    onSendControl: (String, Long?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mobile_tv_remote_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteCardSurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (tvStatus.connected) SkyBluePrimary.copy(alpha = 0.55f) else SoftSlateBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (tvStatus.connected) SkyBlueSoft else SoftSlateVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (tvStatus.connected) Icons.Filled.CastConnected else Icons.Filled.Tv,
                            contentDescription = "Điều khiển Tivi",
                            tint = if (tvStatus.connected) SkyBlueDeep else TextSecondarySlate,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Điều khiển trình phát Tivi",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = if (tvStatus.connected) {
                                "Đã kết nối: ${tvStatus.deviceName} · Bấm bài bên dưới để phát thẳng trên TV"
                            } else {
                                "Bấm bất kỳ bài hát nào bên dưới để phát trực tiếp lên Tivi"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = if (tvStatus.connected) SkyBlueDeep else TextSecondarySlate
                        )
                    }
                }

                if (tvStatus.connected) {
                    OutlinedButton(
                        onClick = onDisconnectTv,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("mobile_btn_disconnect_tv"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, SoftSlateBorder)
                    ) {
                        Text("Ngắt", fontSize = 12.sp, color = TextSecondarySlate)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onScanTvs,
                        enabled = !isScanningTvs,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("mobile_btn_find_tv"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SkyBlueSoft,
                            contentColor = SkyBlueDeep
                        )
                    ) {
                        if (isScanningTvs) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = SkyBlueDeep
                            )
                        } else {
                            Icon(Icons.Filled.WifiFind, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isScanningTvs) "Đang tìm..." else "Tìm TV", fontSize = 12.sp)
                    }
                }
            }

            if (!tvMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SkyBlueTint,
                    border = BorderStroke(1.dp, SkyBlueSoft)
                ) {
                    Text(
                        text = tvMessage,
                        fontSize = 12.sp,
                        color = SkyBlueDeep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Discovered TV devices quick select
            if (discoveredTvs.isNotEmpty() && !tvStatus.connected) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(discoveredTvs, key = { it.url }) { tv ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onConnectTv(tv.url) },
                            shape = RoundedCornerShape(999.dp),
                            color = SkyBlueTint,
                            border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Tv, contentDescription = null, tint = SkyBlueDeep, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${tv.deviceName} (${tv.url.substringAfter("://").substringBefore(":")})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SkyBlueDeep
                                )
                            }
                        }
                    }
                }
            }

            if (!tvStatus.connected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualTvIp,
                        onValueChange = onManualIpChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text("Nhập IP Tivi (vd: 192.168.1.20)", fontSize = 13.sp, color = TextMutedSlate)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyBluePrimary,
                            unfocusedBorderColor = SoftSlateBorder,
                            focusedContainerColor = SoftWhiteCanvas,
                            unfocusedContainerColor = SoftWhiteCanvas
                        )
                    )
                    Button(
                        onClick = { onConnectTv(manualTvIp) },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("mobile_btn_connect_tv"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBluePrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Kết nối", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Live TV Now Playing & Transport Controls
            if (tvStatus.connected) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SoftWhiteCanvas,
                    border = BorderStroke(1.dp, SoftSlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tvStatus.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimaryDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = tvStatus.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp,
                                    color = TextSecondarySlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (tvStatus.format.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SkyBlueSoft
                                ) {
                                    Text(
                                        text = tvStatus.format,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SkyBlueDeep,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Seek Slider
                        val safeDuration = tvStatus.durationMs.coerceAtLeast(1L)
                        var isDragging by remember { mutableStateOf(false) }
                        var dragFraction by remember { mutableFloatStateOf(0f) }
                        val currentFraction = if (isDragging) {
                            dragFraction
                        } else {
                            (tvStatus.positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                        }

                        Column {
                            Slider(
                                value = currentFraction,
                                onValueChange = {
                                    isDragging = true
                                    dragFraction = it
                                },
                                onValueChangeFinished = {
                                    val targetMs = (dragFraction * safeDuration).toLong()
                                    isDragging = false
                                    onSendControl("seek", targetMs)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = SkyBluePrimary,
                                    activeTrackColor = SkyBluePrimary,
                                    inactiveTrackColor = SoftSlateBorder
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDurationMs(if (isDragging) (dragFraction * safeDuration).toLong() else tvStatus.positionMs),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = TextMutedSlate
                                )
                                Text(
                                    text = formatDurationMs(tvStatus.durationMs),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = TextMutedSlate
                                )
                            }
                        }

                        // Transport buttons (all >= 48.dp touch target)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onSendControl("shuffle", null) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("tv_remote_shuffle")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = "Xáo trộn trên Tivi",
                                    tint = if (tvStatus.isShuffle) SkyBluePrimary else TextMutedSlate
                                )
                            }

                            IconButton(
                                onClick = { onSendControl("prev", null) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("tv_remote_prev")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Bài trước trên Tivi",
                                    tint = TextPrimaryDark,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSendControl("rewind", null) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("tv_remote_rewind")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = "Tua lùi 10 giây",
                                    tint = TextPrimaryDark
                                )
                            }

                            FilledIconButton(
                                onClick = { onSendControl("toggle", null) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .testTag("tv_remote_play_pause"),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = SkyBluePrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (tvStatus.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (tvStatus.isPlaying) "Tạm dừng Tivi" else "Phát trên Tivi",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSendControl("forward", null) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("tv_remote_forward")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = "Tua tới 10 giây",
                                    tint = TextPrimaryDark
                                )
                            }

                            IconButton(
                                onClick = { onSendControl("next", null) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("tv_remote_next")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Bài tiếp theo trên Tivi",
                                    tint = TextPrimaryDark,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSendControl("repeat", null) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("tv_remote_repeat")
                            ) {
                                Icon(
                                    imageVector = if (tvStatus.repeatMode == "ONE") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                    contentDescription = "Lặp lại trên Tivi",
                                    tint = if (tvStatus.repeatMode == "OFF") TextMutedSlate else SkyBluePrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileServerHeader(
    deviceName: String,
    trackCount: Int,
    isRunning: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_mobile_server_art),
                contentDescription = "Biểu tượng Mobile Server WiFi",
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, SoftSlateBorder, RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Mobile Server WiFi",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$deviceName · $trackCount bài hát",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isRunning) EmeraldSoftBg else SoftSlateVariant,
            border = BorderStroke(
                width = 1.dp,
                color = if (isRunning) EmeraldBorder else SoftSlateBorder
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) EmeraldActive else TextMutedSlate)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "ONLINE" else "CHỜ",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = if (isRunning) EmeraldTextDark else TextSecondarySlate
                )
            }
        }
    }
}

@Composable
private fun HeroServerControlCard(
    uiState: MobileServerUiState,
    showQr: Boolean,
    copiedBanner: Boolean,
    onToggleServer: () -> Unit,
    onToggleQr: () -> Unit,
    onCopyUrl: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "server_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.isServerRunning) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (uiState.isServerRunning) EmeraldActive.copy(alpha = 0.65f) else SkyBluePrimary.copy(alpha = 0.35f),
        label = "hero_border"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteCardSurface),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = if (uiState.isServerRunning) {
                            listOf(EmeraldSoftBg.copy(alpha = 0.6f), WhiteCardSurface)
                        } else {
                            listOf(SkyBlueTint, WhiteCardSurface)
                        }
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (uiState.isServerRunning) {
                            EmeraldSoftBg
                        } else {
                            SkyBlueSoft.copy(alpha = 0.55f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (uiState.isServerRunning) EmeraldBorder else SkyBluePrimary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(onClick = onToggleServer)
                    .testTag("mobile_toggle_server")
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(if (uiState.isServerRunning) EmeraldActive else SkyBluePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isServerRunning) Icons.Filled.WifiTethering else Icons.Filled.Wifi,
                            contentDescription = "Server Wi-Fi",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Đặt máy này làm Server nhạc",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = if (uiState.isServerRunning) {
                                "Đang phát sóng LAN cho Tivi stream trực tiếp"
                            } else {
                                "Bật để TV Music quét hoặc nhận bài trực tiếp từ ĐT"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 12.sp,
                            color = if (uiState.isServerRunning) EmeraldTextDark else TextSecondarySlate
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.isServerRunning,
                    onCheckedChange = { onToggleServer() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = EmeraldActive,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCBD5E1),
                        uncheckedBorderColor = SoftSlateBorder
                    )
                )
            }

            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.isServerRunning) SkyBlueDeep else TextSecondarySlate
            )

            AnimatedVisibility(visible = uiState.isServerRunning && !uiState.serverUrl.isNullOrBlank()) {
                val url = uiState.serverUrl.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SoftWhiteCanvas,
                        border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ĐỊA CHỈ SERVER TRONG MẠNG WI-FI",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SkyBlueDeep
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = url,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryDark
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onCopyUrl(url) },
                                    modifier = Modifier.testTag("mobile_btn_copy_url"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    border = BorderStroke(1.dp, SoftSlateBorder)
                                ) {
                                    Text(
                                        text = if (copiedBanner) "Đã chép" else "Chép IP",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimaryDark
                                    )
                                }
                                IconButton(
                                    onClick = onToggleQr,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("mobile_btn_toggle_qr")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.QrCode2,
                                        contentDescription = "Hiện mã QR địa chỉ Server",
                                        tint = SkyBluePrimary
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showQr && uiState.qrBitmap != null) {
                        val qr = uiState.qrBitmap
                        if (qr != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(1.dp, SoftSlateBorder, RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = qr,
                                    contentDescription = "Mã QR địa chỉ Server nhạc",
                                    modifier = Modifier.size(164.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = url,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onToggleServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("mobile_primary_server_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isServerRunning) CrimsonStop else SkyBluePrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (uiState.isServerRunning) Icons.Filled.CheckCircle else Icons.Filled.WifiTethering,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isServerRunning) {
                        "Tắt Server nhạc trên máy này"
                    } else {
                        "Đặt máy này làm Server nhạc ngay"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AmberSoftBg),
        border = BorderStroke(1.dp, AmberBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cần quyền đọc nhạc trên điện thoại",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberTextDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Cho phép ứng dụng quét MP3, FLAC, WAV, M4A trong bộ nhớ máy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AmberTextDark.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.testTag("mobile_btn_grant_permission"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberNotice,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cấp quyền")
            }
        }
    }
}

@Composable
private fun LibraryActionRow(
    isScanning: Boolean,
    hasTracksOrScanned: Boolean,
    savedFolderCount: Int,
    selectedFolders: List<SelectedFolderItem>,
    onRescanMusic: () -> Unit,
    onStopScan: () -> Unit,
    onPickFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onPickFiles: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onRescanMusic,
                    enabled = !isScanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("mobile_btn_rescan"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SkyBlueSoft,
                        contentColor = SkyBlueDeep
                    )
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isScanning -> "Đang quét..."
                            hasTracksOrScanned -> "Quét lại nhạc"
                            else -> "Quét nhạc ngay"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onStopScan,
                    enabled = isScanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("mobile_btn_stop_scan"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isScanning) CrimsonStop.copy(alpha = 0.65f) else SoftSlateBorder
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isScanning) CrimsonStop.copy(alpha = 0.10f) else WhiteCardSurface,
                        contentColor = if (isScanning) CrimsonStop else TextMutedSlate,
                        disabledContainerColor = SoftWhiteCanvas,
                        disabledContentColor = TextMutedSlate
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.StopCircle,
                        contentDescription = "Dừng quét nhạc",
                        tint = if (isScanning) CrimsonStop else TextMutedSlate,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dừng quét",
                        color = if (isScanning) CrimsonStop else TextMutedSlate,
                        fontWeight = if (isScanning) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onPickFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("mobile_btn_pick_folder"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SoftSlateBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = WhiteCardSurface,
                        contentColor = TextPrimaryDark
                    )
                ) {
                    Icon(
                        Icons.Filled.CreateNewFolder,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedFolders.isNotEmpty()) {
                            "Chọn thư mục (${selectedFolders.size})"
                        } else if (savedFolderCount > 0) {
                            "Thư mục ($savedFolderCount)"
                        } else {
                            "Chọn thư mục"
                        },
                        color = TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onPickFiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("mobile_btn_pick_files"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SoftSlateBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = WhiteCardSurface,
                        contentColor = TextPrimaryDark
                    )
                ) {
                    Icon(
                        Icons.Filled.AudioFile,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chọn bài hát lẻ",
                        color = TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (selectedFolders.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mobile_selected_folders_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteCardSurface),
                border = BorderStroke(1.dp, SoftSlateBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "THƯ MỤC ĐÃ CHỌN (${selectedFolders.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = SkyBlueDeep
                        )
                        Text(
                            text = "Nhấn \"Bỏ\" để gỡ thư mục",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp,
                            color = TextMutedSlate
                        )
                    }

                    selectedFolders.forEachIndexed { index, folder ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SoftWhiteCanvas,
                            border = BorderStroke(1.dp, SoftSlateBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = SkyBluePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = folder.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { onRemoveFolder(folder.uriString) },
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("mobile_remove_folder_$index"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, CrimsonStop.copy(alpha = 0.45f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CrimsonStop.copy(alpha = 0.06f),
                                        contentColor = CrimsonStop
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Bỏ thư mục ${folder.displayName}",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Bỏ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvConnectionGuideCard(
    serverUrl: String?,
    isRunning: Boolean,
    activityLog: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteCardSurface),
        border = BorderStroke(1.dp, SoftSlateBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hướng dẫn kết nối nhanh",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryDark
                )
            }
            Text(
                text = "• Chạm trực tiếp vào bài hát để phát ngay lên Tivi qua cùng Wi-Fi.\n" +
                    "• Hoặc mở TV Music -> tab \"Phát qua Wi-Fi\" -> \"Lấy nhạc trực tiếp từ ĐT\"" +
                    if (isRunning && !serverUrl.isNullOrBlank()) " ($serverUrl)." else ".",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondarySlate
            )

            if (activityLog.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftWhiteCanvas,
                    border = BorderStroke(1.dp, SoftSlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "NHẬT KÝ HOẠT ĐỘNG GẦN ĐÂY",
                            style = MaterialTheme.typography.labelMedium,
                            color = SkyBlueDeep
                        )
                        activityLog.take(4).forEach { entry ->
                            Text(
                                text = entry,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = TextSecondarySlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListHeaderAndSearch(
    totalTracks: Int,
    filteredCount: Int,
    excludedTrackCount: Int,
    searchQuery: String,
    selectedFormat: String,
    formatCounts: Map<String, Int>,
    onSearchQueryChange: (String) -> Unit,
    onSelectFormat: (String) -> Unit,
    onRestoreExcludedTracks: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Danh sách nhạc · Chạm để phát Tivi",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryDark
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (excludedTrackCount > 0) {
                    OutlinedButton(
                        onClick = onRestoreExcludedTracks,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("mobile_btn_restore_excluded"),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        border = BorderStroke(1.dp, SkyBluePrimary.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SkyBlueTint,
                            contentColor = SkyBlueDeep
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = "Khôi phục bài đã bỏ",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Khôi phục ($excludedTrackCount)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = if (searchQuery.isBlank() && selectedFormat == "ALL") {
                        "$totalTracks bài"
                    } else {
                        "$filteredCount / $totalTracks bài"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = SkyBlueDeep
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mobile_search_input"),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Tìm tên bài hát, ca sĩ, album...",
                    color = TextMutedSlate
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextMutedSlate)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Xóa tìm kiếm", tint = TextMutedSlate)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SkyBluePrimary,
                unfocusedBorderColor = SoftSlateBorder,
                focusedContainerColor = WhiteCardSurface,
                unfocusedContainerColor = WhiteCardSurface,
                focusedTextColor = TextPrimaryDark,
                unfocusedTextColor = TextPrimaryDark
            )
        )

        // Format classification tabs above the song list
        val orderedFormats = remember(formatCounts) {
            val baseOrder = listOf("ALL", "FLAC", "WAV", "MP3", "M4A", "AAC", "OGG", "OPUS", "WMA", "MIDI")
            val presentExtras = formatCounts.keys.filter { it !in baseOrder }.sorted()
            val combined = (baseOrder + presentExtras).filter { fmt ->
                fmt == "ALL" || (formatCounts[fmt] ?: 0) > 0
            }
            combined
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mobile_format_tabs"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orderedFormats, key = { it }) { fmt ->
                val count = formatCounts[fmt] ?: 0
                val isSelected = selectedFormat == fmt
                val label = if (fmt == "ALL") "Tất cả ($count)" else "$fmt ($count)"
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelectFormat(fmt) }
                        .testTag("mobile_format_tab_$fmt"),
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) SkyBluePrimary else WhiteCardSurface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) SkyBluePrimary else SoftSlateBorder
                    )
                ) {
                    Text(
                        text = label,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else TextSecondarySlate,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTracksCard(
    hasPermission: Boolean,
    hasQueryOrFilter: Boolean,
    onRescanMusic: () -> Unit,
    onResetFilter: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WhiteCardSurface),
        border = BorderStroke(1.dp, SoftSlateBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AudioFile,
                contentDescription = null,
                tint = TextMutedSlate,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = when {
                    hasQueryOrFilter -> "Không có bài hát thuộc mục lọc hiện tại"
                    !hasPermission -> "Chưa có quyền truy cập kho nhạc"
                    else -> "Chưa có danh sách nhạc đã lưu"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryDark
            )
            Text(
                text = when {
                    hasQueryOrFilter -> "Nhấn Hiện tất cả để xem toàn bộ bài hát."
                    !hasPermission -> "Nhấn Cấp quyền hoặc Chọn trực tiếp bài hát trong Download."
                    else -> "Nhấn \"Quét nhạc ngay\" một lần, danh sách sẽ được lưu lại vĩnh viễn."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondarySlate
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasQueryOrFilter) {
                    FilledTonalButton(onClick = onResetFilter) {
                        Text("Hiện tất cả")
                    }
                } else if (!hasPermission) {
                    Button(onClick = onRequestPermission) {
                        Text("Cấp quyền")
                    }
                } else {
                    Button(onClick = onRescanMusic) {
                        Text("Quét nhạc ngay")
                    }
                }
                OutlinedButton(
                    onClick = onPickFiles,
                    border = BorderStroke(1.dp, SoftSlateBorder)
                ) {
                    Text("Chọn file", color = TextPrimaryDark)
                }
                OutlinedButton(
                    onClick = onPickFolder,
                    border = BorderStroke(1.dp, SoftSlateBorder)
                ) {
                    Text("Thư mục", color = TextPrimaryDark)
                }
            }
        }
    }
}

@Composable
private fun LiveScanStatusBanner(
    isScanning: Boolean,
    stageText: String,
    totalFound: Int,
    newCount: Int,
    latestTitle: String?,
    summary: String?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mobile_live_scan_banner"),
        shape = RoundedCornerShape(14.dp),
        color = if (isScanning) SkyBlueTint else EmeraldSoftBg,
        border = BorderStroke(1.dp, if (isScanning) SkyBluePrimary.copy(alpha = 0.4f) else EmeraldBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = SkyBluePrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldActive,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanning) {
                        stageText.ifBlank { "Đang quét kho nhạc..." }
                    } else {
                        summary ?: "Đã lưu ($totalFound bài hát)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isScanning) SkyBlueDeep else EmeraldTextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isScanning && !latestTitle.isNullOrBlank()) {
                    Text(
                        text = "+ Vừa thêm: $latestTitle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (isScanning) SkyBlueSoft else EmeraldBorder)
            ) {
                Text(
                    text = if (newCount > 0) "$totalFound bài (+$newCount)" else "$totalFound bài",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isScanning) SkyBlueDeep else EmeraldTextDark,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MobileTrackItemCard(
    track: MobileAudioTrack,
    isPreviewPlaying: Boolean,
    isPlayingOnTv: Boolean,
    isCasting: Boolean,
    onPlayOnTv: () -> Unit,
    onTogglePreview: () -> Unit,
    onRemoveTrack: () -> Unit
) {
    val active = isPlayingOnTv || isCasting
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mobile_track_${track.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) SkyBlueTint else if (isPreviewPlaying) EmeraldSoftBg else WhiteCardSurface
        ),
        border = BorderStroke(
            width = if (active) 1.5.dp else 1.dp,
            color = if (active) SkyBluePrimary else if (isPreviewPlaying) EmeraldBorder else SoftSlateBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlayOnTv)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftSlateVariant)
                    .border(1.dp, SoftSlateBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!track.albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = track.albumArtUri,
                        contentDescription = "Ảnh bìa ${track.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Album,
                        contentDescription = null,
                        tint = SkyBluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) SkyBlueDeep else TextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track.artist} · ${track.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SkyBlueSoft
                    ) {
                        Text(
                            text = track.format,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SkyBlueDeep,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isPlayingOnTv) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldSoftBg,
                            border = BorderStroke(1.dp, EmeraldBorder)
                        ) {
                            Text(
                                text = "PHÁT TRÊN TV",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldTextDark,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "${track.bitrate} · ${track.formattedDuration}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = TextMutedSlate
                        )
                    }
                }
            }

            IconButton(
                onClick = onTogglePreview,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mobile_preview_${track.id}")
            ) {
                Icon(
                    imageVector = if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.Headphones,
                    contentDescription = if (isPreviewPlaying) "Tạm dừng nghe thử" else "Nghe thử trên điện thoại",
                    tint = if (isPreviewPlaying) EmeraldActive else TextSecondarySlate
                )
            }

            IconButton(
                onClick = onRemoveTrack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mobile_remove_track_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Bỏ bài hát khỏi danh sách đã thêm",
                    tint = CrimsonStop
                )
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
}
