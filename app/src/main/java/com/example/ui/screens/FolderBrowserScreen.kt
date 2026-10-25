package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioFolder
import com.example.model.AudioTrack
import com.example.ui.components.tvFocusable
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioEmerald
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioPrimaryLight
import com.example.ui.theme.AudioRed
import com.example.ui.theme.FocusHoverBg
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FolderBrowserScreen(
    folders: List<AudioFolder>,
    browsingFolder: AudioFolder?,
    folderTracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    isScanning: Boolean,
    onOpenFolder: (AudioFolder) -> Unit,
    onCloseFolder: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayFolder: (AudioFolder, Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefreshScan: () -> Unit,
    onPickStorageFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (browsingFolder != null) {
        BackHandler {
            onCloseFolder()
        }

        // Inside a specific folder view
        FolderDetailView(
            folder = browsingFolder,
            tracks = folderTracks,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            onBack = onCloseFolder,
            onPlayTrack = { track -> onPlayTrack(track, folderTracks) },
            onPlayAll = { shuffle -> onPlayFolder(browsingFolder, shuffle) },
            onToggleFavorite = onToggleFavorite,
            modifier = modifier
        )
    } else {
        // Folders list overview
        FolderOverviewView(
            folders = folders,
            isScanning = isScanning,
            onOpenFolder = onOpenFolder,
            onRefreshScan = onRefreshScan,
            onPickStorageFolder = onPickStorageFolder,
            modifier = modifier
        )
    }
}

@Composable
private fun FolderOverviewView(
    folders: List<AudioFolder>,
    isScanning: Boolean,
    onOpenFolder: (AudioFolder) -> Unit,
    onRefreshScan: () -> Unit,
    onPickStorageFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "Duyệt Thư Mục Thiết Bị",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Bộ nhớ máy, thẻ nhớ & ổ đĩa USB cắm ngoài trên TV",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pick Folder Button
                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "btn_pick_folder",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = LightSurface,
                            onClick = onPickStorageFolder
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Chọn thư mục",
                        tint = AudioPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mở thư mục khác",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Scan Library Button
                Row(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "btn_refresh_scan",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = if (isScanning) AudioPrimaryLight else LightSurface,
                            onClick = onRefreshScan
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = AudioPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Quét lại",
                            tint = AudioPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScanning) "Đang quét..." else "Quét lại",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (folders.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Không tìm thấy thư mục âm nhạc nào",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Nhấn 'Quét lại' hoặc 'Mở thư mục khác' để nạp file nhạc từ thiết bị hoặc USB",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 250.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(folders) { folder ->
                    FolderCard(
                        folder = folder,
                        onOpen = { onOpenFolder(folder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: AudioFolder,
    onOpen: () -> Unit
) {
    val isDemoFolder = folder.name.contains("Demo", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                tag = "folder_card_${folder.name.hashCode()}",
                shape = RoundedCornerShape(14.dp),
                scaleUpFactor = 1.02f,
                defaultBgColor = LightSurfaceCard,
                onClick = onOpen
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isDemoFolder) Color(0xFFFEF3C7) else Color(0xFFEFF6FF)
                )
                .border(
                    1.dp,
                    if (isDemoFolder) Color(0xFFFDE68A) else Color(0xFFDBEAFE),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = folder.name,
                tint = if (isDemoFolder) AudioAmber else AudioPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, LightBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${folder.trackCount} bài hát",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isDemoFolder) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "FLAC/MP3",
                            color = AudioAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderDetailView(
    folder: AudioFolder,
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Breadcrumb & Back bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .tvFocusable(
                        tag = "btn_folder_back",
                        shape = CircleShape,
                        defaultBgColor = LightSurface,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = folder.path,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play All Button
            Row(
                modifier = Modifier
                    .tvFocusable(
                        tag = "btn_folder_play_all",
                        shape = RoundedCornerShape(10.dp),
                        defaultBgColor = AudioPrimary,
                        focusedBgColor = Color(0xFF1E40AF),
                        onClick = { onPlayAll(false) }
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
                    text = "Phát tất cả",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Shuffle All Button
            Row(
                modifier = Modifier
                    .tvFocusable(
                        tag = "btn_folder_shuffle",
                        shape = RoundedCornerShape(10.dp),
                        defaultBgColor = LightSurface,
                        onClick = { onPlayAll(true) }
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

        Spacer(modifier = Modifier.height(20.dp))

        // Tracks List
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
                    onPlay = { onPlayTrack(track) },
                    onToggleFavorite = { onToggleFavorite(track.id) }
                )
            }
        }
    }
}

@Composable
fun TrackRowItem(
    index: Int,
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                tag = "track_item_${track.id}",
                shape = RoundedCornerShape(12.dp),
                scaleUpFactor = 1.015f,
                defaultBgColor = if (isCurrent) AudioPrimaryLight else LightSurfaceCard,
                defaultBorderColor = if (isCurrent) Color(0xFF93C5FD) else LightBorder,
                focusedBorderColor = AudioPrimary,
                onClick = onPlay
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number or playing animation
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Đang phát",
                    tint = AudioPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "%02d".format(index),
                    color = if (isCurrent) AudioPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) AudioPrimary else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${track.artist} • ${track.album}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Format Badge (FLAC, MP3, WAV, AAC, OGG) with natural tones
        val (badgeBg, badgeBorder, badgeText) = when (track.format) {
            "FLAC" -> Triple(Color(0xFFFEF3C7), Color(0xFFFDE68A), AudioAmber)
            "WAV" -> Triple(Color(0xFFEDE9FE), Color(0xFFDDD6FE), Color(0xFF6D28D9))
            "OGG" -> Triple(Color(0xFFD1FAE5), Color(0xFFA7F3D0), AudioEmerald)
            else -> Triple(AudioPrimaryLight, Color(0xFFBFDBFE), AudioPrimary)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBg)
                .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = track.format,
                color = badgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Duration & Bitrate
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = track.formattedDuration,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = track.bitrate,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Favorite Toggle Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .tvFocusable(
                    tag = "fav_btn_${track.id}",
                    shape = CircleShape,
                    defaultBgColor = Color.Transparent,
                    onClick = onToggleFavorite
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Yêu thích",
                tint = if (track.isFavorite) AudioRed else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
