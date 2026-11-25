package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TvNavTab
import com.example.ui.theme.LocalTvStyle

@Composable
fun TvSidebar(
    selectedTab: TvNavTab,
    onTabSelected: (TvNavTab) -> Unit,
    isPlaying: Boolean,
    isWifiServerRunning: Boolean = false,
    sleepTimerSeconds: Int?,
    onToggleStyle: () -> Unit = {},
    onOpenQrBackground: () -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current

    val sidebarShape = if (style.isGlassStyle) {
        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(0.dp)
    }

    val sidebarBorderModifier = if (style.isGlassStyle) {
        Modifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(style.glassSpecularTop, style.glassSpecularBottom)
            ),
            shape = sidebarShape
        )
    } else {
        Modifier.border(width = 1.dp, color = style.borderColor)
    }

    val sidebarWidth = if (compact) 76.dp else 256.dp

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .clip(sidebarShape)
            .background(style.sidebarSurface)
            .then(sidebarBorderModifier)
            .verticalScroll(rememberScrollState())
            .padding(
                vertical = 18.dp,
                horizontal = if (compact) 10.dp else 16.dp
            ),
        horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // App Header
            if (compact) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (style.isGlassStyle) Color(0x40FFFFFF) else Color(0xFF1E293B)
                        )
                        .border(
                            1.dp,
                            if (style.isGlassStyle) style.glassSpecularTop else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = "TV Music",
                        tint = if (style.isGlassStyle) style.accentColor else Color(0xFFFDE68A),
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (style.isGlassStyle) Color(0x40FFFFFF) else Color(0xFF1E293B)
                            )
                            .border(
                                1.dp,
                                if (style.isGlassStyle) style.glassSpecularTop else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "Logo",
                            tint = if (style.isGlassStyle) style.accentColor else Color(0xFFFDE68A),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "TV Music",
                            color = style.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = if (style.isGlassStyle) "Apple TV Glass" else "Trình phát thư mục TV",
                            color = style.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Items
            TvNavTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val icon = when (tab) {
                    TvNavTab.FOLDERS -> Icons.Default.Folder
                    TvNavTab.ALL_SONGS -> Icons.Default.LibraryMusic
                    TvNavTab.NOW_PLAYING -> Icons.Default.Album
                    TvNavTab.BACKGROUND_PLAY -> Icons.Default.NightsStay
                    TvNavTab.PLAYLISTS -> Icons.Default.Favorite
                    TvNavTab.WIFI_PHONE -> Icons.Default.Wifi
                    TvNavTab.SETTINGS -> Icons.Default.Tune
                    TvNavTab.INFO -> Icons.Default.Info
                }

                val iconColor = when {
                    isSelected -> style.accentColor
                    tab == TvNavTab.PLAYLISTS -> style.redColor
                    tab == TvNavTab.FOLDERS -> style.amberColor
                    tab == TvNavTab.BACKGROUND_PLAY -> style.accentColor
                    tab == TvNavTab.WIFI_PHONE -> style.emeraldColor
                    tab == TvNavTab.INFO -> style.emeraldColor
                    else -> style.textSecondary
                }

                if (compact) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .size(48.dp)
                            .tvFocusable(
                                tag = "nav_tab_${tab.name.lowercase()}",
                                shape = RoundedCornerShape(12.dp),
                                defaultBgColor = if (isSelected) style.selectedPillSurface else Color.Transparent,
                                focusedBgColor = style.focusedSurface,
                                defaultBorderColor = if (isSelected) style.selectedBorderColor else Color.Transparent,
                                focusedBorderColor = style.focusedBorderColor,
                                elevation = 0.dp,
                                onClick = { onTabSelected(tab) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.title,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .tvFocusable(
                                tag = "nav_tab_${tab.name.lowercase()}",
                                shape = RoundedCornerShape(12.dp),
                                defaultBgColor = if (isSelected) style.selectedPillSurface else Color.Transparent,
                                focusedBgColor = style.focusedSurface,
                                defaultBorderColor = if (isSelected) style.selectedBorderColor else Color.Transparent,
                                focusedBorderColor = style.focusedBorderColor,
                                elevation = 0.dp,
                                onClick = { onTabSelected(tab) }
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.title,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = tab.title,
                            color = if (isSelected) {
                                if (style.isGlassStyle) style.textPrimary else style.accentColor
                            } else style.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        if ((tab == TvNavTab.NOW_PLAYING && isPlaying) ||
                            (tab == TvNavTab.BACKGROUND_PLAY && isPlaying) ||
                            (tab == TvNavTab.WIFI_PHONE && isWifiServerRunning)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (tab == TvNavTab.WIFI_PHONE) style.emeraldColor else style.accentColor)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (compact) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusable(
                            tag = "sidebar_toggle_style",
                            shape = RoundedCornerShape(12.dp),
                            defaultBgColor = style.surfaceVariant,
                            defaultBorderColor = style.borderColor,
                            elevation = 0.dp,
                            onClick = onToggleStyle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (style.isGlassStyle) Icons.Default.AutoAwesome else Icons.Default.WbSunny,
                        contentDescription = "Chuyển giao diện",
                        tint = style.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusable(
                            tag = "sidebar_qr_bg",
                            shape = RoundedCornerShape(12.dp),
                            defaultBgColor = style.surfaceVariant,
                            defaultBorderColor = style.borderColor,
                            elevation = 0.dp,
                            onClick = onOpenQrBackground
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "Ảnh nền QR",
                        tint = style.emeraldColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusable(
                            tag = "sidebar_toggle_style",
                            shape = RoundedCornerShape(12.dp),
                            defaultBgColor = style.surfaceVariant,
                            defaultBorderColor = style.borderColor,
                            elevation = 0.dp,
                            onClick = onToggleStyle
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (style.isGlassStyle) Icons.Default.AutoAwesome else Icons.Default.WbSunny,
                        contentDescription = "Chuyển giao diện",
                        tint = style.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (style.isGlassStyle) "Style: Apple TV Kính" else "Style: Nền Trắng",
                        color = style.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusable(
                            tag = "sidebar_qr_bg",
                            shape = RoundedCornerShape(12.dp),
                            defaultBgColor = style.surfaceVariant,
                            defaultBorderColor = style.borderColor,
                            elevation = 0.dp,
                            onClick = onOpenQrBackground
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "Ảnh nền QR",
                        tint = style.emeraldColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ảnh nền QR điện thoại",
                        color = style.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (sleepTimerSeconds != null && sleepTimerSeconds > 0) {
                    val minutes = sleepTimerSeconds / 60
                    val seconds = sleepTimerSeconds % 60
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(style.amberBg)
                            .border(1.dp, style.amberBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Hẹn giờ",
                            tint = style.amberColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tắt sau: %02d:%02d".format(minutes, seconds),
                            color = style.amberColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(style.surfaceVariant)
                            .border(1.dp, style.borderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Định dạng:",
                            color = style.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "FLAC • MP3 • WAV • AAC • OGG",
                            color = style.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
