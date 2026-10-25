package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.ui.components.tvFocusable
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioRed
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PlaylistsScreen(
    favoriteTracks: List<AudioTrack>,
    recentTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayAll: (List<AudioTrack>, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Favorites, 1: Recents
    val displayTracks = if (selectedSubTab == 0) favoriteTracks else recentTracks

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Danh Sách Nhạc",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedSubTab == 0) "${favoriteTracks.size} bài hát yêu thích" else "${recentTracks.size} bài nghe gần đây",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Sub-Tab Switcher with mouse pointer
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Favorites tab button
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "subtab_favs",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = if (selectedSubTab == 0) Color(0xFFFFE4E6) else LightSurface,
                            defaultBorderColor = if (selectedSubTab == 0) Color(0xFFFDA4AF) else LightBorder,
                            onClick = { selectedSubTab = 0 }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (selectedSubTab == 0) AudioRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Yêu thích (${favoriteTracks.size})",
                            color = if (selectedSubTab == 0) AudioRed else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Recents tab button
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "subtab_recents",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = if (selectedSubTab == 1) Color(0xFFDBEAFE) else LightSurface,
                            defaultBorderColor = if (selectedSubTab == 1) Color(0xFF93C5FD) else LightBorder,
                            onClick = { selectedSubTab = 1 }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = if (selectedSubTab == 1) AudioPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Gần đây (${recentTracks.size})",
                            color = if (selectedSubTab == 1) AudioPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Play All / Shuffle buttons for the list
        if (displayTracks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "btn_playlist_play_all",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = AudioPrimary,
                            focusedBgColor = Color(0xFF1E40AF),
                            onClick = { onPlayAll(displayTracks, false) }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Phát tất cả",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Phát danh sách",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "btn_playlist_shuffle",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = LightSurface,
                            onClick = { onPlayAll(displayTracks, true) }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Xáo trộn",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Xáo trộn",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // List
        if (displayTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedSubTab == 0) Icons.Default.Favorite else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedSubTab == 0) "Chưa có bài hát yêu thích" else "Chưa có lịch sử phát gần đây",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (selectedSubTab == 0) "Nhấn biểu tượng trái tim ở bất kỳ bài hát nào để lưu vào đây" else "Các bài hát bạn nghe sẽ tự động được hiển thị ở đây",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(displayTracks) { index, track ->
                    val isCurrent = currentTrack?.id == track.id
                    TrackRowItem(
                        index = index + 1,
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onPlay = { onPlayTrack(track, displayTracks) },
                        onToggleFavorite = { onToggleFavorite(track.id) }
                    )
                }
            }
        }
    }
}
