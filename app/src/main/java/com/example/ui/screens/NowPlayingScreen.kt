package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.model.EqualizerPreset
import com.example.model.RepeatMode
import com.example.ui.components.AlbumCoverView
import com.example.ui.components.AudioVisualizerView
import com.example.ui.components.BunnyProgressBar
import com.example.ui.components.tvFocusable
import com.example.ui.theme.LocalTvStyle

@Composable
fun NowPlayingScreen(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    isShuffle: Boolean,
    equalizerPreset: EqualizerPreset,
    sleepTimerSeconds: Int?,
    visualizerBands: List<Float>,
    playlist: List<AudioTrack>,
    allTracks: List<AudioTrack> = emptyList(),
    coverArtStyle: CoverArtStyle = CoverArtStyle.SPINNING_DISC,
    onSetCoverArtStyle: (CoverArtStyle) -> Unit = {},
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekRewind: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSetEqualizer: (EqualizerPreset) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectQueueTrack: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current

    val displayTracks = remember(playlist, allTracks) {
        playlist.ifEmpty { allTracks }
    }

    val progress = if (currentTrack != null && durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val elapsedSeconds = if (currentTrack != null) (currentPositionMs / 1000).coerceAtLeast(0) else 0L
    val elapsedStr = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    val totalSeconds = if (currentTrack != null) (durationMs / 1000).coerceAtLeast(0) else 0L
    val totalStr = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    val queueListState = rememberLazyListState()
    LaunchedEffect(currentTrack?.id, displayTracks.size) {
        val activeId = currentTrack?.id ?: return@LaunchedEffect
        val idx = displayTracks.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            queueListState.animateScrollToItem(idx)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("now_playing_root")
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        val isCompactLayout = maxWidth < 680.dp
        val coverSize = if (isCompactLayout) 136.dp else 196.dp

        if (isCompactLayout) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumCoverView(
                        albumArtUri = currentTrack?.albumArtUri,
                        coverStyle = coverArtStyle,
                        isPlaying = currentTrack != null && isPlaying,
                        size = coverSize,
                        cornerRadius = 14.dp,
                        showCenterHole = true
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        NowPlayingTrackHeader(
                            currentTrack = currentTrack,
                            totalQueueSize = displayTracks.size,
                            onToggleFavorite = onToggleFavorite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CoverStyleToggleRow(
                            coverArtStyle = coverArtStyle,
                            onSetCoverArtStyle = onSetCoverArtStyle
                        )
                    }
                }

                PlayMenuSection(
                    isPlaying = currentTrack != null && isPlaying,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    sleepTimerSeconds = sleepTimerSeconds,
                    onPlayPause = onPlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onSeekForward = onSeekForward,
                    onSeekRewind = onSeekRewind,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onSetSleepTimer = onSetSleepTimer
                )

                BunnyProgressBar(
                    progress = progress,
                    durationMs = durationMs,
                    elapsedStr = elapsedStr,
                    totalStr = totalStr,
                    isPlaying = currentTrack != null && isPlaying,
                    enabled = currentTrack != null && durationMs > 0L,
                    onSeekTo = onSeekTo
                )

                NowPlayingQueueSection(
                    tracks = displayTracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    coverArtStyle = coverArtStyle,
                    listState = queueListState,
                    onSelectQueueTrack = onSelectQueueTrack,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                // Left Column: Cover Art + Cover Style Toggle + Audio Visualizer + Equalizer Presets
                Column(
                    modifier = Modifier
                        .weight(0.78f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AlbumCoverView(
                        albumArtUri = currentTrack?.albumArtUri,
                        coverStyle = coverArtStyle,
                        isPlaying = currentTrack != null && isPlaying,
                        size = coverSize,
                        cornerRadius = 14.dp,
                        showCenterHole = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CoverStyleToggleRow(
                        coverArtStyle = coverArtStyle,
                        onSetCoverArtStyle = onSetCoverArtStyle
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    AudioVisualizerView(
                        bands = visualizerBands,
                        barCount = 16,
                        height = 56.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(style.cardSurface)
                            .border(1.dp, style.borderColor, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EqualizerPreset.values().forEach { eq ->
                            val isSelected = equalizerPreset == eq
                            Box(
                                modifier = Modifier
                                    .tvFocusable(
                                        tag = "eq_chip_${eq.name.lowercase()}",
                                        shape = RoundedCornerShape(12.dp),
                                        defaultBgColor = if (isSelected) style.selectedPillSurface else style.cardSurface,
                                        defaultBorderColor = if (isSelected) style.accentColor else style.borderColor,
                                        onClick = { onSetEqualizer(eq) }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = eq.title,
                                    color = if (isSelected) style.accentColor else style.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Right Column:
                // 1. Track Info
                // 2. Play Menu (Transport + Shuffle/Repeat/Timer)
                // 3. Bunny Progress Bar (moved below Play Menu)
                // 4. Smooth-scrolling Song List directly below
                Column(
                    modifier = Modifier
                        .weight(1.22f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NowPlayingTrackHeader(
                        currentTrack = currentTrack,
                        totalQueueSize = displayTracks.size,
                        onToggleFavorite = onToggleFavorite
                    )

                    PlayMenuSection(
                        isPlaying = currentTrack != null && isPlaying,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        sleepTimerSeconds = sleepTimerSeconds,
                        onPlayPause = onPlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeekForward = onSeekForward,
                        onSeekRewind = onSeekRewind,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onSetSleepTimer = onSetSleepTimer
                    )

                    BunnyProgressBar(
                        progress = progress,
                        durationMs = durationMs,
                        elapsedStr = elapsedStr,
                        totalStr = totalStr,
                        isPlaying = currentTrack != null && isPlaying,
                        enabled = currentTrack != null && durationMs > 0L,
                        onSeekTo = onSeekTo
                    )

                    NowPlayingQueueSection(
                        tracks = displayTracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        coverArtStyle = coverArtStyle,
                        listState = queueListState,
                        onSelectQueueTrack = onSelectQueueTrack,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverStyleToggleRow(
    coverArtStyle: CoverArtStyle,
    onSetCoverArtStyle: (CoverArtStyle) -> Unit
) {
    val style = LocalTvStyle.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isSquare = coverArtStyle == CoverArtStyle.ROUNDED_SQUARE
        val isDisc = coverArtStyle == CoverArtStyle.SPINNING_DISC

        Row(
            modifier = Modifier
                .tvFocusable(
                    tag = "np_cover_square",
                    shape = RoundedCornerShape(10.dp),
                    defaultBgColor = if (isSquare) style.selectedPillSurface else style.cardSurface,
                    defaultBorderColor = if (isSquare) style.accentColor else style.borderColor,
                    onClick = { onSetCoverArtStyle(CoverArtStyle.ROUNDED_SQUARE) }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CropSquare,
                contentDescription = "Vuông bo góc",
                tint = if (isSquare) style.accentColor else style.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Vuông bo góc",
                color = if (isSquare) style.accentColor else style.textSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSquare) FontWeight.Bold else FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier
                .tvFocusable(
                    tag = "np_cover_disc",
                    shape = RoundedCornerShape(10.dp),
                    defaultBgColor = if (isDisc) style.selectedPillSurface else style.cardSurface,
                    defaultBorderColor = if (isDisc) style.accentColor else style.borderColor,
                    onClick = { onSetCoverArtStyle(CoverArtStyle.SPINNING_DISC) }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = "Đĩa xoay",
                tint = if (isDisc) style.accentColor else style.textSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Đĩa xoay",
                color = if (isDisc) style.accentColor else style.textSecondary,
                fontSize = 11.sp,
                fontWeight = if (isDisc) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NowPlayingTrackHeader(
    currentTrack: AudioTrack?,
    totalQueueSize: Int,
    onToggleFavorite: (String) -> Unit
) {
    val style = LocalTvStyle.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(style.accentLightBg)
                        .border(1.dp, style.accentLightBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (currentTrack != null) {
                            "${currentTrack.format} ${currentTrack.bitrate}".trim()
                        } else {
                            "SẴN SÀNG PHÁT"
                        },
                        color = style.accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(style.surfaceVariant)
                        .border(1.dp, style.borderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = currentTrack?.sampleRate ?: "$totalQueueSize bài hát",
                        color = style.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (currentTrack != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .tvFocusable(
                            tag = "np_btn_favorite",
                            shape = CircleShape,
                            onClick = { onToggleFavorite(currentTrack.id) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Yêu thích",
                        tint = if (currentTrack.isFavorite) style.redColor else style.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = currentTrack?.title ?: if (totalQueueSize > 0) "Chưa chọn bài hát" else "Trình phát nhạc TV Music",
            color = style.textPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (currentTrack != null) {
                "${currentTrack.artist} • ${currentTrack.album}"
            } else if (totalQueueSize > 0) {
                "Bấm Phát hoặc chọn bài hát trong danh sách bên dưới"
            } else {
                "Quét kho nhạc hoặc kết nối Mobile Server WiFi để phát nhạc"
            },
            color = if (currentTrack != null) style.accentColor else style.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayMenuSection(
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    sleepTimerSeconds: Int?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekRewind: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit
) {
    val style = LocalTvStyle.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .tvFocusable(
                        tag = "np_btn_rewind",
                        shape = CircleShape,
                        onClick = onSeekRewind
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Lùi 10 giây",
                    tint = style.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .tvFocusable(
                        tag = "np_btn_prev",
                        shape = CircleShape,
                        onClick = onSkipPrevious
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Bài trước",
                    tint = style.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .tvFocusable(
                        tag = "np_btn_play_pause",
                        shape = CircleShape,
                        scaleUpFactor = 1.08f,
                        defaultBgColor = style.accentColor,
                        focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                        defaultBorderColor = style.accentColor,
                        elevation = 4.dp,
                        onClick = onPlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                    tint = style.accentContentColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .tvFocusable(
                        tag = "np_btn_next",
                        shape = CircleShape,
                        onClick = onSkipNext
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Bài tiếp theo",
                    tint = style.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .tvFocusable(
                        tag = "np_btn_forward",
                        shape = CircleShape,
                        onClick = onSeekForward
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Tiến 10 giây",
                    tint = style.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .tvFocusable(
                        tag = "np_btn_shuffle",
                        shape = RoundedCornerShape(10.dp),
                        defaultBgColor = if (isShuffle) style.selectedPillSurface else style.cardSurface,
                        defaultBorderColor = if (isShuffle) style.accentColor else style.borderColor,
                        onClick = onToggleShuffle
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Xáo trộn",
                        tint = if (isShuffle) style.accentColor else style.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isShuffle) "Xáo trộn: BẬT" else "Xáo trộn",
                        color = if (isShuffle) style.accentColor else style.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .tvFocusable(
                        tag = "np_btn_repeat",
                        shape = RoundedCornerShape(10.dp),
                        defaultBgColor = if (repeatMode != RepeatMode.OFF) style.selectedPillSurface else style.cardSurface,
                        defaultBorderColor = if (repeatMode != RepeatMode.OFF) style.accentColor else style.borderColor,
                        onClick = onCycleRepeatMode
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = repeatMode.label,
                        tint = if (repeatMode != RepeatMode.OFF) style.accentColor else style.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = repeatMode.label,
                        color = if (repeatMode != RepeatMode.OFF) style.accentColor else style.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .tvFocusable(
                        tag = "np_btn_sleep_timer",
                        shape = RoundedCornerShape(10.dp),
                        defaultBgColor = if (sleepTimerSeconds != null) style.amberBg else style.cardSurface,
                        defaultBorderColor = if (sleepTimerSeconds != null) style.amberBorder else style.borderColor,
                        onClick = {
                            val nextMinutes = when (sleepTimerSeconds?.let { it / 60 }) {
                                null -> 15
                                in 1..15 -> 30
                                in 16..30 -> 45
                                in 31..45 -> 60
                                else -> null
                            }
                            onSetSleepTimer(nextMinutes)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Hẹn giờ",
                        tint = if (sleepTimerSeconds != null) style.amberColor else style.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (sleepTimerSeconds != null) {
                            val m = sleepTimerSeconds / 60
                            val s = sleepTimerSeconds % 60
                            "Hẹn: %02d:%02d".format(m, s)
                        } else {
                            "Hẹn giờ tắt"
                        },
                        color = if (sleepTimerSeconds != null) style.amberColor else style.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingQueueSection(
    tracks: List<AudioTrack>,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    coverArtStyle: CoverArtStyle,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectQueueTrack: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(style.cardSurface.copy(alpha = 0.72f))
            .border(1.dp, style.borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = style.accentColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Danh sách bài hát (${tracks.size})",
                    color = style.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val currentIdx = currentTrack?.let { ct -> tracks.indexOfFirst { it.id == ct.id } } ?: -1
            if (currentIdx >= 0) {
                Text(
                    text = "Bài ${currentIdx + 1}/${tracks.size}",
                    color = style.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = style.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chưa có bài hát trong danh sách",
                        color = style.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("np_queue_list"),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { index, item -> "${index}_${item.id}" }
                ) { index, track ->
                    val isCurrent = currentTrack?.id == track.id
                    NowPlayingQueueRow(
                        index = index + 1,
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        coverArtStyle = coverArtStyle,
                        onClick = { onSelectQueueTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingQueueRow(
    index: Int,
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    coverArtStyle: CoverArtStyle,
    onClick: () -> Unit
) {
    val style = LocalTvStyle.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(
                tag = "np_queue_item_${track.id}",
                shape = RoundedCornerShape(10.dp),
                scaleUpFactor = 1.015f,
                defaultBgColor = if (isCurrent) style.selectedPillSurface else style.surfaceVariant.copy(alpha = 0.45f),
                defaultBorderColor = if (isCurrent) style.selectedBorderColor else Color.Transparent,
                focusedBorderColor = style.focusedBorderColor,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = "Đang phát",
                    tint = style.accentColor,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "%02d".format(index),
                    color = if (isCurrent) style.accentColor else style.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        AlbumCoverView(
            albumArtUri = track.albumArtUri,
            coverStyle = coverArtStyle,
            isPlaying = isCurrent && isPlaying,
            size = 34.dp,
            cornerRadius = 6.dp,
            showCenterHole = false
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrent) style.accentColor else style.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.format}",
                color = style.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = track.formattedDuration,
            color = if (isCurrent) style.accentColor else style.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
