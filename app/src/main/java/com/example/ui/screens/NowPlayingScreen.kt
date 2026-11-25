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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    coverArtStyle: CoverArtStyle = CoverArtStyle.ROUNDED_SQUARE,
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("now_playing_root")
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        val isCompactLayout = maxWidth < 680.dp
        val coverSize = if (isCompactLayout) 124.dp else 188.dp

        if (isCompactLayout) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
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
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CoverStyleToggleRow(
                        coverArtStyle = coverArtStyle,
                        onSetCoverArtStyle = onSetCoverArtStyle,
                        compact = true
                    )
                    BunnyProgressBar(
                        progress = progress,
                        durationMs = durationMs,
                        elapsedStr = elapsedStr,
                        totalStr = totalStr,
                        isPlaying = currentTrack != null && isPlaying,
                        enabled = currentTrack != null && durationMs > 0L,
                        onSeekTo = onSeekTo,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

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
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                // Top Row: Cover Art top-aligned with Track Header + Play Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier.weight(0.78f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        AlbumCoverView(
                            albumArtUri = currentTrack?.albumArtUri,
                            coverStyle = coverArtStyle,
                            isPlaying = currentTrack != null && isPlaying,
                            size = coverSize,
                            cornerRadius = 14.dp,
                            showCenterHole = true
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.22f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Middle Row: Cover Style Toggle aligned horizontally with Bunny Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(0.78f),
                        contentAlignment = Alignment.Center
                    ) {
                        CoverStyleToggleRow(
                            coverArtStyle = coverArtStyle,
                            onSetCoverArtStyle = onSetCoverArtStyle
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1.22f),
                        contentAlignment = Alignment.Center
                    ) {
                        BunnyProgressBar(
                            progress = progress,
                            durationMs = durationMs,
                            elapsedStr = elapsedStr,
                            totalStr = totalStr,
                            isPlaying = currentTrack != null && isPlaying,
                            enabled = currentTrack != null && durationMs > 0L,
                            onSeekTo = onSeekTo,
                            modifier = Modifier
                                .widthIn(max = 380.dp)
                                .fillMaxWidth(0.90f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Row: Visualizer + Equalizer on the left, Smooth-scrolling Queue on the right
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.78f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
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

                    NowPlayingQueueSection(
                        tracks = displayTracks,
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        coverArtStyle = coverArtStyle,
                        listState = queueListState,
                        onSelectQueueTrack = onSelectQueueTrack,
                        modifier = Modifier
                            .weight(1.22f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverStyleToggleRow(
    coverArtStyle: CoverArtStyle,
    onSetCoverArtStyle: (CoverArtStyle) -> Unit,
    compact: Boolean = false
) {
    val style = LocalTvStyle.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
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
                .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CropSquare,
                contentDescription = "Vuông bo góc",
                tint = if (isSquare) style.accentColor else style.textSecondary,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
            Spacer(modifier = Modifier.width(if (compact) 4.dp else 5.dp))
            Text(
                text = if (compact) "Vuông" else "Vuông bo góc",
                color = if (isSquare) style.accentColor else style.textSecondary,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = if (isSquare) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
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
                .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = "Đĩa xoay",
                tint = if (isDisc) style.accentColor else style.textSecondary,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
            Spacer(modifier = Modifier.width(if (compact) 4.dp else 5.dp))
            Text(
                text = "Đĩa xoay",
                color = if (isDisc) style.accentColor else style.textSecondary,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = if (isDisc) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
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
            text = currentTrack?.title ?: if (totalQueueSize > 0) "Chưa chọn bài hát" else "Trình phát nhạc",
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
                    .size(42.dp)
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
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
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
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
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
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
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
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(42.dp)
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
                    modifier = Modifier.size(19.dp)
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
    var selectedFormat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("ALL") }

    val formatCounts = remember(tracks) {
        tracks.groupingBy { it.format.trim().uppercase().ifBlank { "KHÁC" } }.eachCount()
    }
    val availableFormats = remember(formatCounts) {
        val preferredOrder = listOf("FLAC", "WAV", "DSD", "ALAC", "AIFF", "MP3", "AAC", "M4A", "OGG", "OPUS", "WMA")
        val present = preferredOrder.filter { formatCounts.containsKey(it) } +
            formatCounts.keys.filter { it !in preferredOrder }.sorted()
        if (present.isNotEmpty()) present else listOf("FLAC", "WAV", "MP3", "AAC", "OGG")
    }

    LaunchedEffect(formatCounts) {
        if (selectedFormat != "ALL" && !formatCounts.containsKey(selectedFormat)) {
            selectedFormat = "ALL"
        }
    }

    val filteredTracks = remember(tracks, selectedFormat) {
        if (selectedFormat == "ALL") {
            tracks
        } else {
            tracks.filter { it.format.trim().uppercase().ifBlank { "KHÁC" } == selectedFormat }
        }
    }

    LaunchedEffect(currentTrack?.id, filteredTracks.size, selectedFormat) {
        val activeId = currentTrack?.id ?: return@LaunchedEffect
        val idx = filteredTracks.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
        }
    }

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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    text = "Danh sách (${filteredTracks.size})",
                    color = style.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allSelected = selectedFormat == "ALL"
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "np_queue_format_all",
                            shape = RoundedCornerShape(8.dp),
                            defaultBgColor = if (allSelected) style.accentColor else style.surfaceVariant.copy(alpha = 0.7f),
                            defaultBorderColor = if (allSelected) style.accentColor else style.borderColor,
                            onClick = { selectedFormat = "ALL" }
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Tất cả (${tracks.size})",
                        color = if (allSelected) style.accentContentColor else style.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                availableFormats.forEach { fmt ->
                    val isSelected = selectedFormat == fmt
                    val count = formatCounts[fmt] ?: 0
                    val label = if (count > 0) "$fmt ($count)" else fmt
                    Box(
                        modifier = Modifier
                            .tvFocusable(
                                tag = "np_queue_format_${fmt.lowercase()}",
                                shape = RoundedCornerShape(8.dp),
                                defaultBgColor = if (isSelected) style.accentColor else style.surfaceVariant.copy(alpha = 0.7f),
                                defaultBorderColor = if (isSelected) style.accentColor else style.borderColor,
                                onClick = { selectedFormat = fmt }
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) style.accentContentColor else style.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            val currentIdx = currentTrack?.let { ct -> filteredTracks.indexOfFirst { it.id == ct.id } } ?: -1
            if (currentIdx >= 0) {
                Text(
                    text = "${currentIdx + 1}/${filteredTracks.size}",
                    color = style.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredTracks.isEmpty()) {
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
                        text = if (selectedFormat == "ALL") {
                            "Chưa có bài hát trong danh sách"
                        } else {
                            "Không có bài hát định dạng $selectedFormat"
                        },
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
                    items = filteredTracks,
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
