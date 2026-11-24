package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.qr.QrCodeGenerator
import com.example.ui.components.tvFocusable
import com.example.ui.theme.LocalTvStyle

@Composable
fun WifiPhoneScreen(
    isServerRunning: Boolean,
    serverUrl: String?,
    activityStatus: String,
    totalTracksCount: Int,
    wifiTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    coverArtStyle: CoverArtStyle,
    onToggleServer: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

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
                        text = "Phát Nhạc Trên Điện Thoại (Cùng Wi-Fi)",
                        color = style.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quét mã QR để nghe kho nhạc từ TV trên điện thoại, điều khiển từ xa hoặc gửi nhạc sang TV",
                        color = style.textSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isServerRunning) style.emeraldBg else style.surfaceVariant)
                        .border(
                            1.dp,
                            if (isServerRunning) style.emeraldBorder else style.borderColor,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isServerRunning) style.emeraldColor else style.textMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServerRunning) "Đang phát sóng Wi-Fi" else "Đang tắt",
                        color = if (isServerRunning) style.emeraldColor else style.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Responsive Split: QR Card + Instructions / Stats
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
                            isServerRunning = isServerRunning,
                            serverUrl = serverUrl,
                            qrBitmap = qrBitmap,
                            activityStatus = activityStatus,
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
                            isServerRunning = isServerRunning,
                            serverUrl = serverUrl,
                            qrBitmap = qrBitmap,
                            activityStatus = activityStatus,
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

        // Uploaded tracks from phone over Wi-Fi
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
        }

        if (wifiTracks.isEmpty()) {
            item {
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
                            text = "Chưa có bài hát nào được gửi từ điện thoại",
                            color = style.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Bạn có thể nghe ngay toàn bộ $totalTracksCount bài trên TV bằng điện thoại, hoặc mở tab \"Gửi nhạc lên TV\" trên điện thoại để tải thêm nhạc.",
                            color = style.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
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

@Composable
private fun QrConnectCard(
    isServerRunning: Boolean,
    serverUrl: String?,
    qrBitmap: ImageBitmap?,
    activityStatus: String,
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
        Box(
            modifier = Modifier
                .size(204.dp)
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
                    contentDescription = "Mã QR phát nhạc trên điện thoại",
                    modifier = Modifier.size(184.dp)
                )
            } else {
                CircularProgressIndicator(
                    color = style.accentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                    serverUrl?.substringBefore("/?tab=") ?: "Đang khởi tạo..."
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

        Spacer(modifier = Modifier.height(14.dp))

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
                    text = if (isServerRunning) "Tắt phát Wi-Fi" else "Bật phát Wi-Fi",
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
        // Stats Row
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

        // 3 Feature Explanations
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
                text = "Cách sử dụng trên điện thoại",
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
