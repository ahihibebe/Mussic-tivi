package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.SortOption
import com.example.ui.components.tvFocusable
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioPrimaryLight
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SongsListScreen(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    searchQuery: String,
    selectedFormat: String,
    sortOption: SortOption,
    onSearchChanged: (String) -> Unit,
    onFormatSelected: (String) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val formats = listOf("ALL", "FLAC", "MP3", "WAV", "AAC", "OGG")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Title & Search Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Tất Cả Bài Hát",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${tracks.size} bài hát được phát hiện trên thiết bị",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Search input field with mouse cursor
            Row(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LightSurface)
                    .border(1.dp, LightBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(AudioPrimary),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Tìm bài hát, nghệ sĩ...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .tvFocusable(
                                tag = "btn_clear_search",
                                shape = RoundedCornerShape(6.dp),
                                onClick = { onSearchChanged("") }
                            )
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Xóa tìm kiếm",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Format Filter Chips + Play All / Shuffle buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Filter chips with mouse pointer
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                formats.forEach { fmt ->
                    val isSelected = selectedFormat == fmt
                    val label = when (fmt) {
                        "ALL" -> "Tất cả"
                        "FLAC" -> "FLAC Lossless"
                        "WAV" -> "WAV Master"
                        else -> fmt
                    }

                    Box(
                        modifier = Modifier
                            .tvFocusable(
                                tag = "fmt_chip_$fmt",
                                shape = RoundedCornerShape(20.dp),
                                defaultBgColor = if (isSelected) AudioPrimary else LightSurface,
                                focusedBgColor = if (isSelected) Color(0xFF1E40AF) else LightSurfaceVariant,
                                defaultBorderColor = if (isSelected) AudioPrimary else LightBorder,
                                onClick = { onFormatSelected(fmt) }
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Play All & Shuffle Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "btn_songs_play_all",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = AudioPrimary,
                            focusedBgColor = Color(0xFF1E40AF),
                            onClick = { onPlayAll(false) }
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                        text = "Phát tất cả",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "btn_songs_shuffle",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = LightSurface,
                            onClick = { onPlayAll(true) }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Xáo trộn",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tracks List
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Không tìm thấy bài hát phù hợp",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Hãy thử đổi từ khóa tìm kiếm hoặc chọn định dạng 'Tất cả'",
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
                itemsIndexed(tracks) { index, track ->
                    val isCurrentlyPlaying = currentTrack?.id == track.id
                    TrackRowItem(
                        index = index + 1,
                        track = track,
                        isCurrent = isCurrentlyPlaying,
                        isPlaying = isCurrentlyPlaying && isPlaying,
                        onPlay = { onPlayTrack(track, tracks) },
                        onToggleFavorite = { onToggleFavorite(track.id) }
                    )
                }
            }
        }
    }
}
