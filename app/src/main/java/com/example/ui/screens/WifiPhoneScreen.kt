package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.model.WifiMusicRole
import com.example.qr.DiscoveredPhoneServer
import com.example.qr.QrCodeGenerator
import com.example.ui.components.tvFocusable
import com.example.ui.theme.LocalTvStyle
import com.example.ui.viewmodel.MusicPlayerViewModel
import com.example.webrtc.TvWebRtcStage
import com.example.webrtc.TvWebRtcUiState

@Composable
fun WifiPhoneScreen(
    wifiMusicRole: WifiMusicRole,
    isServerRunning: Boolean,
    serverUrl: String?,
    activityStatus: String,
    totalTracksCount: Int,
    wifiTracks: List<AudioTrack>,
    discoveredServers: List<DiscoveredPhoneServer>,
    isScanningServers: Boolean,
    connectedPhoneServer: DiscoveredPhoneServer?,
    remoteTracks: List<AudioTrack>,
    isLoadingRemoteTracks: Boolean,
    remoteError: String?,
    manualServerIp: String,
    webRtcState: TvWebRtcUiState = TvWebRtcUiState(),
    webRtcOfferInput: String = "",
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    coverArtStyle: CoverArtStyle,
    onSelectRole: (WifiMusicRole) -> Unit,
    onToggleServer: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onScanLanServers: () -> Unit,
    onManualIpChanged: (String) -> Unit,
    onConnectToServer: (String) -> Unit,
    onDisconnectRemoteServer: () -> Unit,
    onWebRtcOfferInputChanged: (String) -> Unit = {},
    onAcceptWebRtcOffer: (String) -> Unit = {},
    onDisconnectWebRtc: () -> Unit = {},
    onRefreshWebRtcTracks: () -> Unit = {},
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var remoteSearchQuery by remember { mutableStateOf("") }

    val filteredRemoteTracks = remember(remoteTracks, remoteSearchQuery) {
        if (remoteSearchQuery.isBlank()) {
            remoteTracks
        } else {
            val q = remoteSearchQuery.trim().lowercase()
            remoteTracks.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
    }

    LaunchedEffect(serverUrl, isServerRunning) {
        qrBitmap = if (isServerRunning && !serverUrl.isNullOrBlank()) {
            QrCodeGenerator.generateQrBitmap(serverUrl, 512)
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Screen Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phát Nhạc Qua Mạng Wi-Fi",
                        color = style.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Chọn lấy nhạc trực tiếp từ Điện thoại làm Server (không cần gửi file) hoặc chia sẻ qua mã QR",
                        color = style.textSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val isBadgeActive = when (wifiMusicRole) {
                    WifiMusicRole.TV_CLIENT_PULL_PHONE -> connectedPhoneServer != null || webRtcState.isDataChannelOpen
                    else -> isServerRunning
                }
                val badgeText = when (wifiMusicRole) {
                    WifiMusicRole.TV_CLIENT_PULL_PHONE -> when {
                        webRtcState.isDataChannelOpen -> "WebRTC STUN"
                        connectedPhoneServer != null -> "Đã kết nối ĐT"
                        else -> "Chế độ TV nhận"
                    }
                    WifiMusicRole.PHONE_SERVER_HOST ->
                        if (isServerRunning) "Đang làm Server" else "Server đang tắt"
                    WifiMusicRole.TV_WEB_HUB ->
                        if (isServerRunning) "Đang phát QR Web" else "Đang tắt"
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isBadgeActive) style.emeraldBg else style.surfaceVariant)
                        .border(
                            1.dp,
                            if (isBadgeActive) style.emeraldBorder else style.borderColor,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isBadgeActive) style.emeraldColor else style.textMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = badgeText,
                        color = if (isBadgeActive) style.emeraldColor else style.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3-Mode Role Selector
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 700.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RoleOptionCard(
                            role = WifiMusicRole.TV_CLIENT_PULL_PHONE,
                            icon = Icons.Default.Devices,
                            isSelected = wifiMusicRole == WifiMusicRole.TV_CLIENT_PULL_PHONE,
                            tag = "wifi_role_tv_pull",
                            onClick = { onSelectRole(WifiMusicRole.TV_CLIENT_PULL_PHONE) },
                            modifier = Modifier.weight(1f)
                        )
                        RoleOptionCard(
                            role = WifiMusicRole.PHONE_SERVER_HOST,
                            icon = Icons.Default.Dns,
                            isSelected = wifiMusicRole == WifiMusicRole.PHONE_SERVER_HOST,
                            tag = "wifi_role_phone_server",
                            onClick = { onSelectRole(WifiMusicRole.PHONE_SERVER_HOST) },
                            modifier = Modifier.weight(1f)
                        )
                        RoleOptionCard(
                            role = WifiMusicRole.TV_WEB_HUB,
                            icon = Icons.Default.QrCode2,
                            isSelected = wifiMusicRole == WifiMusicRole.TV_WEB_HUB,
                            tag = "wifi_role_tv_web",
                            onClick = { onSelectRole(WifiMusicRole.TV_WEB_HUB) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RoleOptionCard(
                            role = WifiMusicRole.TV_CLIENT_PULL_PHONE,
                            icon = Icons.Default.Devices,
                            isSelected = wifiMusicRole == WifiMusicRole.TV_CLIENT_PULL_PHONE,
                            tag = "wifi_role_tv_pull",
                            onClick = { onSelectRole(WifiMusicRole.TV_CLIENT_PULL_PHONE) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        RoleOptionCard(
                            role = WifiMusicRole.PHONE_SERVER_HOST,
                            icon = Icons.Default.Dns,
                            isSelected = wifiMusicRole == WifiMusicRole.PHONE_SERVER_HOST,
                            tag = "wifi_role_phone_server",
                            onClick = { onSelectRole(WifiMusicRole.PHONE_SERVER_HOST) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        RoleOptionCard(
                            role = WifiMusicRole.TV_WEB_HUB,
                            icon = Icons.Default.QrCode2,
                            isSelected = wifiMusicRole == WifiMusicRole.TV_WEB_HUB,
                            tag = "wifi_role_tv_web",
                            onClick = { onSelectRole(WifiMusicRole.TV_WEB_HUB) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        when (wifiMusicRole) {
            WifiMusicRole.TV_CLIENT_PULL_PHONE -> {
                // Panel 1: TV scans & connects directly to Phone Server (Zero Upload)
                item {
                    PhoneDiscoveryCard(
                        discoveredServers = discoveredServers,
                        isScanningServers = isScanningServers,
                        connectedPhoneServer = connectedPhoneServer,
                        isLoadingRemoteTracks = isLoadingRemoteTracks,
                        remoteError = remoteError,
                        manualServerIp = manualServerIp,
                        onManualIpChanged = onManualIpChanged,
                        onScanLanServers = onScanLanServers,
                        onConnectToServer = onConnectToServer,
                        onDisconnectRemoteServer = onDisconnectRemoteServer
                    )
                }

                // Panel 1b: WebRTC Cross-Network Streaming via Google STUN
                item {
                    WebRtcStunReceiverCard(
                        webRtcState = webRtcState,
                        offerInput = webRtcOfferInput,
                        onOfferInputChanged = onWebRtcOfferInputChanged,
                        onAcceptOffer = { onAcceptWebRtcOffer(webRtcOfferInput) },
                        onDisconnectWebRtc = onDisconnectWebRtc,
                        onRefreshWebRtcTracks = onRefreshWebRtcTracks
                    )
                }

                if (connectedPhoneServer != null) {
                    item {
                        ConnectedPhoneLibraryHeader(
                            server = connectedPhoneServer,
                            tracksCount = remoteTracks.size,
                            searchQuery = remoteSearchQuery,
                            onSearchChanged = { remoteSearchQuery = it },
                            onPlayAll = {
                                if (filteredRemoteTracks.isNotEmpty()) {
                                    onPlayTrack(filteredRemoteTracks.first(), filteredRemoteTracks)
                                }
                            },
                            onShuffleAll = {
                                if (filteredRemoteTracks.isNotEmpty()) {
                                    val shuffled = filteredRemoteTracks.shuffled()
                                    onPlayTrack(shuffled.first(), shuffled)
                                }
                            },
                            onRefreshRemote = {
                                if (connectedPhoneServer.url == MusicPlayerViewModel.WEBRTC_SERVER_URL) {
                                    onRefreshWebRtcTracks()
                                } else {
                                    onConnectToServer(connectedPhoneServer.url)
                                }
                            }
                        )
                    }

                    if (filteredRemoteTracks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = if (remoteTracks.isEmpty()) {
                                    "Chưa tìm thấy bài hát trên ${connectedPhoneServer.deviceName}"
                                } else {
                                    "Không có bài hát khớp từ khóa \"$remoteSearchQuery\""
                                },
                                subtitle = "Kiểm tra quyền đọc nhạc trên điện thoại hoặc nhấn Quét lại."
                            )
                        }
                    } else {
                        itemsIndexed(filteredRemoteTracks) { index, track ->
                            val isCurrent = currentTrack?.id == track.id
                            TrackRowItem(
                                index = index + 1,
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                coverArtStyle = coverArtStyle,
                                onPlay = { onPlayTrack(track, filteredRemoteTracks) },
                                onToggleFavorite = { onToggleFavorite(track.id) }
                            )
                        }
                    }
                }
            }

            WifiMusicRole.PHONE_SERVER_HOST -> {
                // Panel 2: This device acts as Music Server for TV to scan & stream directly
                item {
                    val deviceLabel = remember {
                        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isWide = maxWidth >= 700.dp
                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                QrConnectCard(
                                    title = "Máy chủ: $deviceLabel",
                                    isServerRunning = isServerRunning,
                                    serverUrl = serverUrl?.substringBefore("/?tab="),
                                    qrBitmap = qrBitmap,
                                    activityStatus = activityStatus,
                                    toggleButtonTextOn = "Tắt Server nhạc",
                                    toggleButtonTextOff = "Bật Server nhạc",
                                    onToggleServer = onToggleServer,
                                    onRefreshLibrary = onRefreshLibrary,
                                    modifier = Modifier.width(310.dp)
                                )

                                PhoneServerGuideColumn(
                                    deviceName = deviceLabel,
                                    serverIp = serverUrl?.substringBefore("/?tab="),
                                    totalTracksCount = totalTracksCount,
                                    isServerRunning = isServerRunning,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                QrConnectCard(
                                    title = "Máy chủ: $deviceLabel",
                                    isServerRunning = isServerRunning,
                                    serverUrl = serverUrl?.substringBefore("/?tab="),
                                    qrBitmap = qrBitmap,
                                    activityStatus = activityStatus,
                                    toggleButtonTextOn = "Tắt Server nhạc",
                                    toggleButtonTextOff = "Bật Server nhạc",
                                    onToggleServer = onToggleServer,
                                    onRefreshLibrary = onRefreshLibrary,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                PhoneServerGuideColumn(
                                    deviceName = deviceLabel,
                                    serverIp = serverUrl?.substringBefore("/?tab="),
                                    totalTracksCount = totalTracksCount,
                                    isServerRunning = isServerRunning,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            WifiMusicRole.TV_WEB_HUB -> {
                // Panel 3: Existing QR Code Web Hub (listen to TV on phone browser / upload)
                item {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isWide = maxWidth >= 700.dp
                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                QrConnectCard(
                                    title = "Quét QR trên điện thoại",
                                    isServerRunning = isServerRunning,
                                    serverUrl = serverUrl?.substringBefore("/?tab="),
                                    qrBitmap = qrBitmap,
                                    activityStatus = activityStatus,
                                    toggleButtonTextOn = "Tắt phát Wi-Fi",
                                    toggleButtonTextOff = "Bật phát Wi-Fi",
                                    onToggleServer = onToggleServer,
                                    onRefreshLibrary = onRefreshLibrary,
                                    modifier = Modifier.width(310.dp)
                                )

                                WifiGuideAndStatsColumn(
                                    totalTracksCount = totalTracksCount,
                                    wifiTracksCount = wifiTracks.size,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                QrConnectCard(
                                    title = "Quét QR trên điện thoại",
                                    isServerRunning = isServerRunning,
                                    serverUrl = serverUrl?.substringBefore("/?tab="),
                                    qrBitmap = qrBitmap,
                                    activityStatus = activityStatus,
                                    toggleButtonTextOn = "Tắt phát Wi-Fi",
                                    toggleButtonTextOff = "Bật phát Wi-Fi",
                                    onToggleServer = onToggleServer,
                                    onRefreshLibrary = onRefreshLibrary,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                WifiGuideAndStatsColumn(
                                    totalTracksCount = totalTracksCount,
                                    wifiTracksCount = wifiTracks.size,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = style.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nhạc đã nhận từ điện thoại (${wifiTracks.size})",
                            color = style.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (wifiTracks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Chưa có bài hát nào được gửi từ điện thoại",
                            subtitle = "Bạn có thể nghe ngay toàn bộ $totalTracksCount bài trên TV bằng điện thoại, hoặc mở tab \"Gửi nhạc lên TV\" trên trình duyệt điện thoại để tải thêm nhạc."
                        )
                    }
                } else {
                    itemsIndexed(wifiTracks) { index, track ->
                        val isCurrent = currentTrack?.id == track.id
                        TrackRowItem(
                            index = index + 1,
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            coverArtStyle = coverArtStyle,
                            onPlay = { onPlayTrack(track, wifiTracks) },
                            onToggleFavorite = { onToggleFavorite(track.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleOptionCard(
    role: WifiMusicRole,
    icon: ImageVector,
    isSelected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    Row(
        modifier = modifier
            .tvFocusable(
                tag = tag,
                shape = RoundedCornerShape(14.dp),
                defaultBgColor = if (isSelected) style.selectedPillSurface else style.cardSurface,
                defaultBorderColor = if (isSelected) style.accentColor else style.borderColor,
                onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) style.accentLightBg else style.surfaceVariant)
                .border(
                    1.dp,
                    if (isSelected) style.accentLightBorder else style.borderColor,
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = role.title,
                tint = if (isSelected) style.accentColor else style.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = role.title,
                color = if (isSelected) style.accentColor else style.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = role.subtitle,
                color = style.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Đang chọn",
                tint = style.accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun PhoneDiscoveryCard(
    discoveredServers: List<DiscoveredPhoneServer>,
    isScanningServers: Boolean,
    connectedPhoneServer: DiscoveredPhoneServer?,
    isLoadingRemoteTracks: Boolean,
    remoteError: String?,
    manualServerIp: String,
    onManualIpChanged: (String) -> Unit,
    onScanLanServers: () -> Unit,
    onConnectToServer: (String) -> Unit,
    onDisconnectRemoteServer: () -> Unit
) {
    val style = LocalTvStyle.current
    val cardShape = RoundedCornerShape(18.dp)
    val cardBorder = if (style.isGlassStyle) {
        Modifier.border(
            1.dp,
            Brush.verticalGradient(listOf(style.glassSpecularTop, style.glassSpecularBottom)),
            cardShape
        )
    } else {
        Modifier.border(1.dp, style.borderColor, cardShape)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(style.cardSurface)
            .then(cardBorder)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tìm & kết nối Điện thoại làm Máy chủ nhạc",
                    color = style.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Trên điện thoại mở app này -> chọn tab \"Phát qua Wi-Fi\" -> \"Đặt máy này làm Server nhạc\", Tivi sẽ quét & phát trực tiếp không cần gửi file.",
                    color = style.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(
                modifier = Modifier
                    .tvFocusable(
                        tag = "wifi_scan_phone_servers",
                        shape = RoundedCornerShape(12.dp),
                        defaultBgColor = style.accentColor,
                        focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                        defaultBorderColor = style.accentColor,
                        onClick = onScanLanServers
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanningServers) {
                    CircularProgressIndicator(
                        color = style.accentContentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = style.accentContentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanningServers) "Đang quét Wi-Fi..." else "Quét tìm ĐT trên Wi-Fi",
                    color = style.accentContentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Manual IP Connection Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(style.surfaceVariant)
                    .border(1.dp, style.borderColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = style.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = manualServerIp,
                    onValueChange = onManualIpChanged,
                    textStyle = TextStyle(color = style.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(style.accentColor),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (manualServerIp.isEmpty()) {
                            Text(
                                text = "Hoặc nhập IP điện thoại (ví dụ: 192.168.1.15:8765)",
                                color = style.textMuted,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Row(
                modifier = Modifier
                    .tvFocusable(
                        tag = "wifi_connect_manual_ip",
                        shape = RoundedCornerShape(10.dp),
                        defaultBgColor = style.surfaceVariant,
                        onClick = { onConnectToServer(manualServerIp) }
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoadingRemoteTracks) {
                    CircularProgressIndicator(
                        color = style.accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = style.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kết nối & Quét nhạc",
                    color = style.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (connectedPhoneServer != null) {
                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "wifi_disconnect_remote",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = style.redBg,
                            defaultBorderColor = style.redBorder,
                            onClick = onDisconnectRemoteServer
                        )
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Ngắt kết nối",
                        tint = style.redColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Discovered Phone Servers
        if (discoveredServers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Thiết bị tìm thấy trong cùng mạng Wi-Fi (${discoveredServers.size}):",
                    color = style.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                discoveredServers.forEachIndexed { index, srv ->
                    val isConnectedHere = connectedPhoneServer?.url == srv.url
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusable(
                                tag = "wifi_discovered_$index",
                                shape = RoundedCornerShape(12.dp),
                                defaultBgColor = if (isConnectedHere) style.selectedPillSurface else style.surfaceVariant,
                                defaultBorderColor = if (isConnectedHere) style.accentColor else style.borderColor,
                                onClick = { onConnectToServer(srv.url) }
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = if (isConnectedHere) style.accentColor else style.emeraldColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = srv.deviceName,
                                color = style.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${srv.url} • ${srv.tracksCount} bài hát sẵn sàng stream",
                                color = style.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isConnectedHere) style.emeraldBg else style.accentLightBg)
                                .border(
                                    1.dp,
                                    if (isConnectedHere) style.emeraldBorder else style.accentLightBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isConnectedHere) "Đang kết nối" else "Lấy danh sách",
                                color = if (isConnectedHere) style.emeraldColor else style.accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (!remoteError.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(style.amberBg)
                    .border(1.dp, style.amberBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = remoteError,
                    color = style.amberColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ConnectedPhoneLibraryHeader(
    server: DiscoveredPhoneServer,
    tracksCount: Int,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onRefreshRemote: () -> Unit
) {
    val style = LocalTvStyle.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kho nhạc trực tiếp từ ${server.deviceName} ($tracksCount bài)",
                    color = style.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Đang stream trực tiếp qua Wi-Fi (${server.url}) — Không tốn dung lượng lưu trữ trên Tivi",
                    color = style.emeraldColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "wifi_remote_play_all",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = style.accentColor,
                            defaultBorderColor = style.accentColor,
                            onClick = onPlayAll
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Phát tất cả",
                        tint = style.accentContentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Phát tất cả",
                        color = style.accentContentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .tvFocusable(
                            tag = "wifi_remote_shuffle",
                            shape = RoundedCornerShape(10.dp),
                            onClick = onShuffleAll
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Xáo trộn",
                        tint = style.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .tvFocusable(
                            tag = "wifi_refresh_remote",
                            shape = RoundedCornerShape(10.dp),
                            onClick = onRefreshRemote
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Quét lại điện thoại",
                        tint = style.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(style.cardSurface)
                .border(1.dp, style.borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Tìm kiếm",
                tint = style.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                textStyle = TextStyle(color = style.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(style.accentColor),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Tìm bài hát, nghệ sĩ trực tiếp trên điện thoại...",
                            color = style.textMuted,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun PhoneServerGuideColumn(
    deviceName: String,
    serverIp: String?,
    totalTracksCount: Int,
    isServerRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    val cardShape = RoundedCornerShape(18.dp)
    val cardBorder = if (style.isGlassStyle) {
        Modifier.border(
            1.dp,
            Brush.verticalGradient(listOf(style.glassSpecularTop, style.glassSpecularBottom)),
            cardShape
        )
    } else {
        Modifier.border(1.dp, style.borderColor, cardShape)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WifiStatChip(
                label = "Bài hát đang chia sẻ trực tiếp",
                value = "$totalTracksCount bài",
                accent = style.emeraldColor,
                modifier = Modifier.weight(1f)
            )
            WifiStatChip(
                label = "Trạng thái phát sóng Wi-Fi",
                value = if (isServerRunning) "Đang mở" else "Đang tắt",
                accent = if (isServerRunning) style.accentColor else style.textMuted,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(style.cardSurface)
                .then(cardBorder)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Hướng dẫn dùng $deviceName làm Server nhạc cho Tivi",
                color = style.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            WifiFeatureStep(
                icon = Icons.Default.Dns,
                iconTint = style.emeraldColor,
                title = "1. Không cần gửi hay chép file lên Tivi",
                description = "Toàn bộ $totalTracksCount bài hát và ảnh cover trên máy này được mở cổng stream trực tiếp trong mạng Wi-Fi."
            )

            WifiFeatureStep(
                icon = Icons.Default.Radar,
                iconTint = style.accentColor,
                title = "2. Mở app trên Tivi -> tab \"Phát qua Wi-Fi\"",
                description = "Chọn mục \"Lấy nhạc trực tiếp từ ĐT\" — Tivi sẽ tự động tìm thấy $deviceName (hoặc nhập địa chỉ ${serverIp ?: "IP đang hiển thị"})."
            )

            WifiFeatureStep(
                icon = Icons.Default.PlayArrow,
                iconTint = style.amberColor,
                title = "3. Phát nhạc chất lượng gốc tức thì",
                description = "Tivi giải mã & phát thẳng FLAC, WAV, MP3, M4A qua mạng nội bộ, giữ nguyên ảnh bìa và thông tin bài hát."
            )
        }
    }
}

@Composable
private fun QrConnectCard(
    title: String,
    isServerRunning: Boolean,
    serverUrl: String?,
    qrBitmap: ImageBitmap?,
    activityStatus: String,
    toggleButtonTextOn: String,
    toggleButtonTextOff: String,
    onToggleServer: () -> Unit,
    onRefreshLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    val cardShape = RoundedCornerShape(18.dp)
    val cardBorder = if (style.isGlassStyle) {
        Modifier.border(
            1.dp,
            Brush.verticalGradient(listOf(style.glassSpecularTop, style.glassSpecularBottom)),
            cardShape
        )
    } else {
        Modifier.border(1.dp, style.borderColor, cardShape)
    }

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(style.cardSurface)
            .then(cardBorder)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = style.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(196.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isServerRunning) Color.White else style.surfaceVariant)
                .border(1.dp, style.borderColor, RoundedCornerShape(14.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isServerRunning) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = style.textMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Máy chủ đang tắt",
                        color = style.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Mã QR kết nối Wi-Fi",
                    modifier = Modifier.size(176.dp)
                )
            } else {
                CircularProgressIndicator(
                    color = style.accentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(style.accentLightBg)
                .border(1.dp, style.accentLightBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = style.accentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isServerRunning) {
                    serverUrl ?: "Đang khởi tạo..."
                } else {
                    "Chưa mở cổng Wi-Fi"
                },
                color = style.accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = activityStatus,
            color = style.textSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .tvFocusable(
                        tag = "wifi_toggle_server",
                        shape = RoundedCornerShape(12.dp),
                        defaultBgColor = if (isServerRunning) style.redBg else style.accentColor,
                        focusedBgColor = if (isServerRunning) style.redBg else style.accentColor.copy(alpha = 0.85f),
                        defaultBorderColor = if (isServerRunning) style.redBorder else style.accentColor,
                        onClick = onToggleServer
                    )
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isServerRunning) style.redColor else style.accentContentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isServerRunning) toggleButtonTextOn else toggleButtonTextOff,
                    color = if (isServerRunning) style.redColor else style.accentContentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .tvFocusable(
                        tag = "wifi_refresh_library",
                        shape = RoundedCornerShape(12.dp),
                        defaultBgColor = style.surfaceVariant,
                        onClick = onRefreshLibrary
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Làm mới thư viện",
                    tint = style.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun WifiGuideAndStatsColumn(
    totalTracksCount: Int,
    wifiTracksCount: Int,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    val cardShape = RoundedCornerShape(18.dp)
    val cardBorder = if (style.isGlassStyle) {
        Modifier.border(
            1.dp,
            Brush.verticalGradient(listOf(style.glassSpecularTop, style.glassSpecularBottom)),
            cardShape
        )
    } else {
        Modifier.border(1.dp, style.borderColor, cardShape)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WifiStatChip(
                label = "Bài hát sẵn sàng phát trên ĐT",
                value = "$totalTracksCount bài",
                accent = style.accentColor,
                modifier = Modifier.weight(1f)
            )
            WifiStatChip(
                label = "Nhạc đã nhận qua Wi-Fi",
                value = "$wifiTracksCount bài",
                accent = style.emeraldColor,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(style.cardSurface)
                .then(cardBorder)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Cách sử dụng trên trình duyệt điện thoại",
                color = style.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            WifiFeatureStep(
                icon = Icons.Default.Headphones,
                iconTint = style.accentColor,
                title = "1. Nghe toàn bộ kho nhạc TV ngay trên điện thoại",
                description = "Kết nối điện thoại và Tivi cùng 1 Wi-Fi, quét mã QR (hoặc nhập địa chỉ IP vào Safari/Chrome) để phát nhạc trực tiếp bằng loa hoặc tai nghe điện thoại."
            )

            WifiFeatureStep(
                icon = Icons.Default.SettingsRemote,
                iconTint = style.amberColor,
                title = "2. Điều khiển phát nhạc trên TV từ xa",
                description = "Chuyển bài, tạm dừng, phát bài bất kỳ từ điện thoại lên loa TV ngay trong cùng giao diện."
            )

            WifiFeatureStep(
                icon = Icons.Default.CloudUpload,
                iconTint = style.emeraldColor,
                title = "3. Gửi nhạc từ điện thoại sang TV",
                description = "Chọn file MP3, FLAC, WAV, M4A trên điện thoại ở mục \"Gửi nhạc lên TV\" để lưu và phát trên TV tức thì."
            )
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String
) {
    val style = LocalTvStyle.current
    val cardShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(style.cardSurface)
            .border(1.dp, style.borderColor, cardShape)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(style.accentLightBg)
                .border(1.dp, style.accentLightBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = style.accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                color = style.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = style.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WifiStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(style.cardSurface)
            .border(1.dp, style.borderColor, shape)
            .padding(14.dp)
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = style.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WifiFeatureStep(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    val style = LocalTvStyle.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(style.surfaceVariant)
                .border(1.dp, style.borderColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = style.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = style.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun WebRtcStunReceiverCard(
    webRtcState: TvWebRtcUiState,
    offerInput: String,
    onOfferInputChanged: (String) -> Unit,
    onAcceptOffer: () -> Unit,
    onDisconnectWebRtc: () -> Unit,
    onRefreshWebRtcTracks: () -> Unit
) {
    val style = LocalTvStyle.current
    val clipboard = LocalClipboardManager.current
    var answerQrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var copiedAnswer by remember { mutableStateOf(false) }

    LaunchedEffect(webRtcState.answerToken) {
        val tok = webRtcState.answerToken
        answerQrBitmap = if (!tok.isNullOrBlank()) {
            QrCodeGenerator.generateQrBitmap(tok, 420)
        } else {
            null
        }
        copiedAnswer = false
    }

    val cardShape = RoundedCornerShape(18.dp)
    val cardBorder = if (webRtcState.isDataChannelOpen) {
        Modifier.border(1.5.dp, style.emeraldColor, cardShape)
    } else if (style.isGlassStyle) {
        Modifier.border(
            1.dp,
            Brush.verticalGradient(listOf(style.glassSpecularTop, style.glassSpecularBottom)),
            cardShape
        )
    } else {
        Modifier.border(1.dp, style.borderColor, cardShape)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(style.cardSurface)
            .then(cardBorder)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (webRtcState.isDataChannelOpen) style.emeraldBg else style.accentLightBg)
                        .border(
                            1.dp,
                            if (webRtcState.isDataChannelOpen) style.emeraldBorder else style.accentLightBorder,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "WebRTC STUN Google",
                        tint = if (webRtcState.isDataChannelOpen) style.emeraldColor else style.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "WebRTC Stream Khác Mạng (STUN Google)",
                        color = style.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "STUN: ${webRtcState.stunServer} · ICE: ${webRtcState.iceStateText}" +
                            (webRtcState.publicStunEndpoint?.let { " · NAT: $it" } ?: ""),
                        color = style.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (webRtcState.stage != TvWebRtcStage.IDLE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (webRtcState.isDataChannelOpen) {
                        Row(
                            modifier = Modifier
                                .tvFocusable(
                                    tag = "tv_webrtc_refresh_tracks",
                                    shape = RoundedCornerShape(10.dp),
                                    defaultBgColor = style.surfaceVariant,
                                    onClick = onRefreshWebRtcTracks
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Tải lại nhạc WebRTC",
                                tint = style.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tải lại nhạc",
                                color = style.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .tvFocusable(
                                tag = "tv_webrtc_disconnect",
                                shape = RoundedCornerShape(10.dp),
                                defaultBgColor = style.redBg,
                                defaultBorderColor = style.redBorder,
                                onClick = onDisconnectWebRtc
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Ngắt WebRTC",
                            tint = style.redColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ngắt WebRTC",
                            color = style.redColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Status banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (webRtcState.isDataChannelOpen) style.emeraldBg else style.surfaceVariant)
                .border(
                    1.dp,
                    if (webRtcState.isDataChannelOpen) style.emeraldBorder else style.borderColor,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (webRtcState.stage == TvWebRtcStage.GATHERING_STUN) {
                CircularProgressIndicator(
                    color = style.accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = webRtcState.statusDetail,
                color = if (webRtcState.isDataChannelOpen) style.emeraldColor else style.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (!webRtcState.isDataChannelOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(style.surfaceVariant)
                        .border(1.dp, style.borderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = offerInput,
                        onValueChange = onOfferInputChanged,
                        textStyle = TextStyle(color = style.textPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(style.accentColor),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (offerInput.isEmpty()) {
                                Text(
                                    text = "Dán mã Offer WebRTC (TVRTC1:...) từ điện thoại",
                                    color = style.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "tv_webrtc_paste_offer",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = style.surfaceVariant,
                            onClick = {
                                val text = clipboard.getText()?.text.orEmpty()
                                if (text.isNotBlank()) onOfferInputChanged(text)
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Dán mã Offer",
                        tint = style.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dán",
                        color = style.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "tv_webrtc_create_answer",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = style.accentColor,
                            defaultBorderColor = style.accentColor,
                            onClick = onAcceptOffer
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = "Tạo mã Answer WebRTC",
                        tint = style.accentContentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tạo Answer",
                        color = style.accentContentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val answerTok = webRtcState.answerToken
        if (!answerTok.isNullOrBlank() && !webRtcState.isDataChannelOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(style.surfaceVariant)
                    .border(1.dp, style.borderColor, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (answerQrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = answerQrBitmap!!,
                            contentDescription = "Mã QR Answer WebRTC",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Mã Answer WebRTC của Tivi (${answerTok.length} ký tự):",
                        color = style.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = answerTok,
                        color = style.accentColor,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier
                            .tvFocusable(
                                tag = "tv_webrtc_copy_answer",
                                shape = RoundedCornerShape(8.dp),
                                defaultBgColor = style.accentLightBg,
                                defaultBorderColor = style.accentLightBorder,
                                onClick = {
                                    clipboard.setText(AnnotatedString(answerTok))
                                    copiedAnswer = true
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Sao chép mã Answer",
                            tint = style.accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copiedAnswer) "Đã sao chép Answer!" else "Sao chép mã Answer",
                            color = style.accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
