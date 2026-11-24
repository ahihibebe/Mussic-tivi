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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack
import com.example.model.CoverArtStyle
import com.example.ui.theme.LocalTvStyle

@Composable
fun NowPlayingMiniBar(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    coverArtStyle: CoverArtStyle = CoverArtStyle.SPINNING_DISC,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val style = LocalTvStyle.current
    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val barShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val barBorder = if (style.isGlassStyle) {
        Modifier.border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(style.glassSpecularTop, style.glassSpecularBottom)
            ),
            shape = barShape
        )
    } else {
        Modifier.border(1.dp, style.borderColor, barShape)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(barShape)
            .background(style.sidebarSurface)
            .then(barBorder)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = style.accentColor,
            trackColor = style.inactiveTrackColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .tvFocusable(
                        tag = "minibar_track_info",
                        shape = RoundedCornerShape(12.dp),
                        defaultBgColor = Color.Transparent,
                        focusedBgColor = style.surfaceVariant,
                        defaultBorderColor = Color.Transparent,
                        elevation = 0.dp,
                        onClick = onOpenFullscreen
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumCoverView(
                    albumArtUri = currentTrack.albumArtUri,
                    coverStyle = coverArtStyle,
                    isPlaying = isPlaying,
                    size = 44.dp,
                    cornerRadius = 8.dp,
                    showCenterHole = false
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentTrack.title,
                            color = style.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(style.accentLightBg)
                                .border(1.dp, style.accentLightBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentTrack.format,
                                color = style.accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "${currentTrack.artist} • ${currentTrack.formattedDuration}",
                        color = style.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .tvFocusable(
                            tag = "minibar_play_pause",
                            shape = CircleShape,
                            scaleUpFactor = 1.08f,
                            defaultBgColor = style.accentColor,
                            focusedBgColor = style.accentColor.copy(alpha = 0.85f),
                            defaultBorderColor = style.accentColor,
                            onClick = onPlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                        tint = style.accentContentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .tvFocusable(
                            tag = "minibar_skip_next",
                            shape = CircleShape,
                            defaultBgColor = style.surfaceVariant,
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
            }
        }
    }
}
