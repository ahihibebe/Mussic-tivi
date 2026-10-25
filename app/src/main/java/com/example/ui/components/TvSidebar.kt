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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TvNavTab
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioPrimaryLight
import com.example.ui.theme.AudioRed
import com.example.ui.theme.FocusHoverBg
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TvSidebar(
    selectedTab: TvNavTab,
    onTabSelected: (TvNavTab) -> Unit,
    isPlaying: Boolean,
    sleepTimerSeconds: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(LightSurface)
            .border(width = 1.dp, color = LightBorder)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // App Header with realistic turntable/audio branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = "Logo",
                        tint = Color(0xFFFDE68A),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "TV Music",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                    Text(
                        text = "Trình phát thư mục TV",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Navigation Items with Realistic Icons
            TvNavTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val icon = when (tab) {
                    TvNavTab.FOLDERS -> Icons.Default.Folder
                    TvNavTab.ALL_SONGS -> Icons.Default.LibraryMusic
                    TvNavTab.NOW_PLAYING -> Icons.Default.Album
                    TvNavTab.PLAYLISTS -> Icons.Default.Favorite
                    TvNavTab.SETTINGS -> Icons.Default.Tune
                }

                val iconColor = when {
                    isSelected -> AudioPrimary
                    tab == TvNavTab.PLAYLISTS -> AudioRed
                    tab == TvNavTab.FOLDERS -> AudioAmber
                    else -> TextSecondary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .tvFocusable(
                            tag = "nav_tab_${tab.name.lowercase()}",
                            shape = RoundedCornerShape(12.dp),
                            defaultBgColor = if (isSelected) AudioPrimaryLight else Color.Transparent,
                            focusedBgColor = FocusHoverBg,
                            defaultBorderColor = if (isSelected) Color(0xFFBFDBFE) else Color.Transparent,
                            focusedBorderColor = AudioPrimary,
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
                        color = if (isSelected) AudioPrimary else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    // Active playing indicator
                    if (tab == TvNavTab.NOW_PLAYING && isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AudioPrimary)
                        )
                    }
                }
            }
        }

        // Bottom Footer: Sleep Timer or Audio Format Information
        if (sleepTimerSeconds != null && sleepTimerSeconds > 0) {
            val minutes = sleepTimerSeconds / 60
            val seconds = sleepTimerSeconds % 60
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEF3C7)) // Soft Amber
                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Hẹn giờ",
                    tint = AudioAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tắt sau: %02d:%02d".format(minutes, seconds),
                    color = AudioAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
                    .border(1.dp, LightBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Định dạng:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "FLAC • MP3 • WAV • AAC • OGG",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
