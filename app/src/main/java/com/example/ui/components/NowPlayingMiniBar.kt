package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioPrimaryLight
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun NowPlayingMiniBar(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(LightSurface)
            .border(1.dp, LightBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        // Thin progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = AudioPrimary,
            trackColor = Color(0xFFE2E8F0)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Track Info clickable with mouse or remote to open Fullscreen
            Row(
                modifier = Modifier
                    .weight(1f)
                    .tvFocusable(
                        tag = "minibar_track_info",
                        shape = RoundedCornerShape(12.dp),
                        defaultBgColor = Color.Transparent,
                        focusedBgColor = LightSurfaceVariant,
                        onClick = onOpenFullscreen
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Album else Icons.Default.Audiotrack,
                        contentDescription = "Cover",
                        tint = Color(0xFFFDE68A),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentTrack.title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Format Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AudioPrimaryLight)
                                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentTrack.format,
                                color = AudioPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "${currentTrack.artist} • ${currentTrack.formattedDuration}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Controls
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Button with mouse hover
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .tvFocusable(
                            tag = "minibar_play_pause",
                            shape = CircleShape,
                            scaleUpFactor = 1.08f,
                            defaultBgColor = AudioPrimary,
                            focusedBgColor = Color(0xFF1E40AF),
                            onClick = onPlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Next Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .tvFocusable(
                            tag = "minibar_skip_next",
                            shape = CircleShape,
                            defaultBgColor = LightSurfaceVariant,
                            onClick = onSkipNext
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Bài tiếp theo",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
