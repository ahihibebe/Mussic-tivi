package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.model.AudioTrack
import com.example.model.EqualizerPreset
import com.example.model.RepeatMode
import com.example.ui.components.AudioVisualizerView
import com.example.ui.components.VinylDiscView
import com.example.ui.components.tvFocusable
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioPrimaryLight
import com.example.ui.theme.AudioRed
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceCard
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

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
    if (currentTrack == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Chưa có bài hát nào được chọn",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Hãy duyệt thư mục máy hoặc danh sách bài hát để bắt đầu nghe nhạc",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val elapsedSeconds = (currentPositionMs / 1000).coerceAtLeast(0)
    val elapsedStr = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val totalStr = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Left Column: Realistic Vinyl Player & Studio Visualizer
        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VinylDiscView(
                isPlaying = isPlaying,
                size = 240.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Studio Spectrum Visualizer
            AudioVisualizerView(
                bands = visualizerBands,
                barCount = 16,
                height = 68.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F5F9))
                    .border(1.dp, LightBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Equalizer Presets
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
                                defaultBgColor = if (isSelected) AudioPrimaryLight else LightSurface,
                                defaultBorderColor = if (isSelected) AudioPrimary else LightBorder,
                                onClick = { onSetEqualizer(eq) }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = eq.title,
                            color = if (isSelected) AudioPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Right Column: Track Details & Controls
        Column(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Track Info & Badges
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Badges
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AudioPrimaryLight)
                                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${currentTrack.format} ${currentTrack.bitrate}",
                                color = AudioPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, LightBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentTrack.sampleRate,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Favorite Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .tvFocusable(
                                tag = "np_btn_favorite",
                                shape = CircleShape,
                                defaultBgColor = LightSurface,
                                onClick = { onToggleFavorite(currentTrack.id) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Yêu thích",
                            tint = if (currentTrack.isFavorite) AudioRed else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Track Title (Readable Slate 900)
                Text(
                    text = currentTrack.title,
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Artist & Album
                Text(
                    text = currentTrack.artist,
                    color = AudioPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "${currentTrack.album} • ${currentTrack.parentDirectory}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Middle: Progress Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        val targetMs = (newProgress * durationMs).toLong()
                        onSeekTo(targetMs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = AudioPrimary,
                        activeTrackColor = AudioPrimary,
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = elapsedStr,
                        color = AudioPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = totalStr,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Primary Playback Controls with Mouse & D-Pad support
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 10s
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .tvFocusable(
                            tag = "np_btn_rewind",
                            shape = CircleShape,
                            defaultBgColor = LightSurface,
                            onClick = onSeekRewind
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Lùi 10 giây",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Skip Previous
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .tvFocusable(
                            tag = "np_btn_prev",
                            shape = CircleShape,
                            defaultBgColor = LightSurface,
                            onClick = onSkipPrevious
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Bài trước",
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Big Play / Pause Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .tvFocusable(
                            tag = "np_btn_play_pause",
                            shape = CircleShape,
                            scaleUpFactor = 1.08f,
                            defaultBgColor = AudioPrimary,
                            focusedBgColor = Color(0xFF1E40AF),
                            elevation = 4.dp,
                            onClick = onPlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Skip Next
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .tvFocusable(
                            tag = "np_btn_next",
                            shape = CircleShape,
                            defaultBgColor = LightSurface,
                            onClick = onSkipNext
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Bài tiếp theo",
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Forward 10s
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .tvFocusable(
                            tag = "np_btn_forward",
                            shape = CircleShape,
                            defaultBgColor = LightSurface,
                            onClick = onSeekForward
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Tiến 10 giây",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Secondary Row: Shuffle, Repeat, Sleep Timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "np_btn_shuffle",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = if (isShuffle) AudioPrimaryLight else LightSurface,
                            defaultBorderColor = if (isShuffle) AudioPrimary else LightBorder,
                            onClick = onToggleShuffle
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Xáo trộn",
                            tint = if (isShuffle) AudioPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isShuffle) "Xáo trộn: BẬT" else "Xáo trộn",
                            color = if (isShuffle) AudioPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Repeat Button
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "np_btn_repeat",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = if (repeatMode != RepeatMode.OFF) AudioPrimaryLight else LightSurface,
                            defaultBorderColor = if (repeatMode != RepeatMode.OFF) AudioPrimary else LightBorder,
                            onClick = onCycleRepeatMode
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = repeatMode.label,
                            tint = if (repeatMode != RepeatMode.OFF) AudioPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = repeatMode.label,
                            color = if (repeatMode != RepeatMode.OFF) AudioPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Sleep Timer Button
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            tag = "np_btn_sleep_timer",
                            shape = RoundedCornerShape(10.dp),
                            defaultBgColor = if (sleepTimerSeconds != null) Color(0xFFFEF3C7) else LightSurface,
                            defaultBorderColor = if (sleepTimerSeconds != null) Color(0xFFFDE68A) else LightBorder,
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
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Hẹn giờ",
                            tint = if (sleepTimerSeconds != null) AudioAmber else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (sleepTimerSeconds != null) {
                                val m = sleepTimerSeconds / 60
                                val s = sleepTimerSeconds % 60
                                "Hẹn giờ: %02d:%02d".format(m, s)
                            } else {
                                "Hẹn giờ tắt"
                            },
                            color = if (sleepTimerSeconds != null) AudioAmber else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
