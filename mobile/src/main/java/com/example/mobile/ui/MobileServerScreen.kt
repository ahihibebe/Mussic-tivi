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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.mobile.ui.theme.AmberNotice
import com.example.mobile.ui.theme.CyanSignal
import com.example.mobile.ui.theme.CyanSignalGlow
import com.example.mobile.ui.theme.DeepSlateBg
import com.example.mobile.ui.theme.EmeraldActive
import com.example.mobile.ui.theme.EmeraldSurface
import com.example.mobile.ui.theme.JetBrainsMonoFamily
import com.example.mobile.ui.theme.SlateBorder
import com.example.mobile.ui.theme.SlateCardSurface
import com.example.mobile.ui.theme.SlateElevatedSurface
import com.example.mobile.ui.theme.TextPrimaryLight
import com.example.mobile.ui.theme.TextSecondarySlate
import com.example.mobile.viewmodel.MobileServerUiState

@Composable
fun MobileServerScreen(
    uiState: MobileServerUiState,
    onToggleServer: () -> Unit,
    onRescanMusic: () -> Unit,
    onPickFolder: () -> Unit,
    onRequestPermission: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onTogglePreview: (MobileAudioTrack) -> Unit
) {
    var showQrDialogCard by remember { mutableStateOf(false) }
    var copiedBanner by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        containerColor = DeepSlateBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF10203B),
                            DeepSlateBg,
                            Color(0xFF070D19)
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

                if (!uiState.hasAudioPermission) {
                    item(key = "permission_banner") {
                        PermissionRequiredCard(onRequestPermission = onRequestPermission)
                    }
                }

                item(key = "actions_row") {
                    LibraryActionRow(
                        isScanning = uiState.isScanning,
                        savedFolderCount = uiState.savedFolderCount,
                        onRescanMusic = onRescanMusic,
                        onPickFolder = onPickFolder
                    )
                }

                item(key = "tv_guide_card") {
                    TvConnectionGuideCard(
                        serverUrl = uiState.serverUrl,
                        isRunning = uiState.isServerRunning,
                        activityLog = uiState.activityLog
                    )
                }

                item(key = "search_header") {
                    TrackListHeaderAndSearch(
                        totalTracks = uiState.tracks.size,
                        filteredCount = uiState.filteredTracks.size,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = onSearchQueryChange
                    )
                }

                if (uiState.isScanning && uiState.tracks.isEmpty()) {
                    item(key = "scanning_indicator") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                            border = BorderStroke(1.dp, SlateBorder)
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
                                    color = CyanSignal,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Đang quét nhạc trên điện thoại...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimaryLight
                                )
                            }
                        }
                    }
                } else if (uiState.filteredTracks.isEmpty()) {
                    item(key = "empty_tracks") {
                        EmptyTracksCard(
                            hasPermission = uiState.hasAudioPermission,
                            hasQuery = uiState.searchQuery.isNotBlank(),
                            onRescanMusic = onRescanMusic,
                            onPickFolder = onPickFolder,
                            onRequestPermission = onRequestPermission
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredTracks,
                        key = { it.id }
                    ) { track ->
                        MobileTrackItemCard(
                            track = track,
                            isPlaying = uiState.previewTrackId == track.id && uiState.isPreviewPlaying,
                            onTogglePreview = { onTogglePreview(track) }
                        )
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
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
                    .border(1.dp, SlateBorder, RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Mobile Server WiFi",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimaryLight,
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
            color = if (isRunning) EmeraldSurface else SlateElevatedSurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (isRunning) EmeraldActive.copy(alpha = 0.65f) else SlateBorder
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
                        .background(if (isRunning) EmeraldActive else TextSecondarySlate)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "ONLINE" else "TẠM TẮT",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = if (isRunning) Color(0xFFA7F3D0) else TextSecondarySlate
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
        targetValue = if (uiState.isServerRunning) 1.09f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (uiState.isServerRunning) EmeraldActive.copy(alpha = 0.7f) else CyanSignal.copy(alpha = 0.45f),
        label = "hero_border"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
        border = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = if (uiState.isServerRunning) {
                            listOf(Color(0xFF0E2B2B), SlateCardSurface)
                        } else {
                            listOf(Color(0xFF132744), SlateCardSurface)
                        }
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (uiState.isServerRunning) {
                            EmeraldActive.copy(alpha = 0.14f)
                        } else {
                            CyanSignal.copy(alpha = 0.10f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (uiState.isServerRunning) {
                            EmeraldActive.copy(alpha = 0.45f)
                        } else {
                            CyanSignal.copy(alpha = 0.30f)
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(onClick = onToggleServer)
                    .testTag("mobile_toggle_server")
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(if (uiState.isServerRunning) EmeraldActive else CyanSignalGlow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isServerRunning) Icons.Filled.WifiTethering else Icons.Filled.Wifi,
                            contentDescription = "Server Wi-Fi",
                            tint = Color(0xFF04101A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Đặt máy này làm Server nhạc",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryLight
                        )
                        Text(
                            text = if (uiState.isServerRunning) {
                                "Đang phát sóng LAN cho Tivi quét trực tiếp"
                            } else {
                                "Bật để TV Music stream thẳng nhạc từ máy này"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.isServerRunning) Color(0xFFA7F3D0) else TextSecondarySlate
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.isServerRunning,
                    onCheckedChange = { onToggleServer() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF042016),
                        checkedTrackColor = EmeraldActive,
                        uncheckedThumbColor = TextPrimaryLight,
                        uncheckedTrackColor = SlateElevatedSurface
                    )
                )
            }

            Text(
                text = uiState.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.isServerRunning) Color(0xFFBAE6FD) else TextSecondarySlate
            )

            AnimatedVisibility(visible = uiState.isServerRunning && !uiState.serverUrl.isNullOrBlank()) {
                val url = uiState.serverUrl.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SlateElevatedSurface,
                        border = BorderStroke(1.dp, CyanSignal.copy(alpha = 0.40f))
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
                                    color = CyanSignal
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = url,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimaryLight
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onCopyUrl(url) },
                                    modifier = Modifier.testTag("mobile_btn_copy_url"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    border = BorderStroke(1.dp, SlateBorder)
                                ) {
                                    Text(
                                        text = if (copiedBanner) "Đã chép" else "Chép IP",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimaryLight
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
                                        tint = CyanSignal
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
                                    color = Color(0xFF0F172A)
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
                    .height(50.dp)
                    .testTag("mobile_primary_server_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isServerRunning) Color(0xFF991B1B) else CyanSignalGlow,
                    contentColor = if (uiState.isServerRunning) Color.White else Color(0xFF041322)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B210B)),
        border = BorderStroke(1.dp, AmberNotice.copy(alpha = 0.6f))
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
                    color = Color(0xFFFDE68A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Cho phép ứng dụng quét MP3, FLAC, WAV, M4A trong bộ nhớ máy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFCD34D)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.testTag("mobile_btn_grant_permission"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberNotice,
                    contentColor = Color(0xFF1E1302)
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
    savedFolderCount: Int,
    onRescanMusic: () -> Unit,
    onPickFolder: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = onRescanMusic,
            enabled = !isScanning,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("mobile_btn_rescan"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isScanning) "Đang quét..." else "Quét lại nhạc")
        }

        OutlinedButton(
            onClick = onPickFolder,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("mobile_btn_pick_folder"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Icon(
                Icons.Filled.CreateNewFolder,
                contentDescription = null,
                tint = CyanSignal,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (savedFolderCount > 0) "Thư mục ($savedFolderCount)" else "Chọn thư mục",
                color = TextPrimaryLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = null,
                    tint = CyanSignal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cách kết nối trên TV Music",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryLight
                )
            }
            Text(
                text = "1. Kết nối điện thoại và Tivi cùng một mạng Wi-Fi.\n" +
                    "2. Bật \"Đặt máy này làm Server nhạc\" ở phía trên.\n" +
                    "3. Trên Tivi mở TV Music -> tab \"Phát qua Wi-Fi\" -> chọn \"Lấy nhạc trực tiếp từ ĐT\". Tivi sẽ tự tìm thấy máy này" +
                    if (isRunning && !serverUrl.isNullOrBlank()) " (hoặc nhập $serverUrl)." else ".",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondarySlate
            )

            if (activityLog.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DeepSlateBg.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.6f))
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
                            color = CyanSignal
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
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
                    tint = CyanSignal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kho nhạc chia sẻ",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryLight
                )
            }
            Text(
                text = if (searchQuery.isBlank()) "$totalTracks bài" else "$filteredCount / $totalTracks bài",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFamily,
                color = CyanSignal
            )
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
                    text = "Tìm tên bài hát, ca sĩ, định dạng...",
                    color = TextSecondarySlate
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondarySlate)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Xóa tìm kiếm", tint = TextSecondarySlate)
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanSignal,
                unfocusedBorderColor = SlateBorder,
                focusedContainerColor = SlateCardSurface,
                unfocusedContainerColor = SlateCardSurface,
                focusedTextColor = TextPrimaryLight,
                unfocusedTextColor = TextPrimaryLight
            )
        )
    }
}

@Composable
private fun EmptyTracksCard(
    hasPermission: Boolean,
    hasQuery: Boolean,
    onRescanMusic: () -> Unit,
    onPickFolder: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
        border = BorderStroke(1.dp, SlateBorder)
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
                tint = TextSecondarySlate,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = when {
                    hasQuery -> "Không tìm thấy bài hát khớp từ khóa"
                    !hasPermission -> "Chưa có quyền truy cập kho nhạc"
                    else -> "Chưa tìm thấy file nhạc nào trên máy"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryLight
            )
            Text(
                text = if (!hasPermission) {
                    "Nhấn Cấp quyền hoặc Chọn thư mục chứa nhạc MP3 / FLAC / WAV."
                } else {
                    "Hãy chép nhạc vào thư mục Music/Downloads hoặc bấm Chọn thư mục để quét thêm."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondarySlate
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasPermission) {
                    Button(onClick = onRequestPermission) {
                        Text("Cấp quyền")
                    }
                } else {
                    FilledTonalButton(onClick = onRescanMusic) {
                        Text("Quét lại")
                    }
                }
                OutlinedButton(onClick = onPickFolder) {
                    Text("Chọn thư mục", color = TextPrimaryLight)
                }
            }
        }
    }
}

@Composable
private fun MobileTrackItemCard(
    track: MobileAudioTrack,
    isPlaying: Boolean,
    onTogglePreview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mobile_track_${track.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) SlateElevatedSurface else SlateCardSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) CyanSignal.copy(alpha = 0.7f) else SlateBorder.copy(alpha = 0.75f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTogglePreview)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SlateElevatedSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(12.dp)),
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
                        tint = CyanSignal.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaying) CyanSignal else TextPrimaryLight,
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
                        color = CyanSignal.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = track.format,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanSignal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${track.bitrate} · ${track.formattedDuration}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = TextSecondarySlate
                    )
                }
            }

            IconButton(
                onClick = onTogglePreview,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("mobile_preview_${track.id}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng nghe thử" else "Nghe thử bài hát",
                    tint = if (isPlaying) CyanSignal else TextPrimaryLight
                )
            }
        }
    }
}
