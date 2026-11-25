package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.CoverArtStyle
import com.example.ui.theme.AudioAmber
import com.example.ui.theme.LocalTvStyle
import java.io.File

@Composable
fun AlbumCoverView(
    albumArtUri: String?,
    coverStyle: CoverArtStyle,
    isPlaying: Boolean,
    size: Dp,
    cornerRadius: Dp = 14.dp,
    showCenterHole: Boolean = true,
    modifier: Modifier = Modifier
) {
    when (coverStyle) {
        CoverArtStyle.ROUNDED_SQUARE -> {
            RoundedSquareCoverView(
                albumArtUri = albumArtUri,
                isPlaying = isPlaying,
                size = size,
                cornerRadius = cornerRadius,
                modifier = modifier
            )
        }
        CoverArtStyle.SPINNING_DISC -> {
            VinylDiscView(
                isPlaying = isPlaying,
                size = size,
                albumArtUri = albumArtUri,
                showCenterHole = showCenterHole,
                modifier = modifier
            )
        }
    }
}

@Composable
fun RoundedSquareCoverView(
    albumArtUri: String?,
    isPlaying: Boolean,
    size: Dp,
    cornerRadius: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)
    var loadFailed by remember(albumArtUri) { mutableStateOf(false) }

    val borderBrush = if (style.isGlassStyle) {
        Brush.verticalGradient(listOf(style.glassSpecularTop, style.glassSpecularBottom))
    } else {
        Brush.verticalGradient(listOf(style.borderColor, style.borderColor))
    }

    val modelData = remember(albumArtUri) { resolveCoilImageModel(albumArtUri) }

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (size >= 120.dp) 14.dp else 4.dp,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = if (style.isGlassStyle) {
                        listOf(Color(0xFF141416), Color(0xFF050507))
                    } else {
                        listOf(Color(0xFF1E293B), Color(0xFF334155))
                    }
                )
            )
            .border(width = if (size >= 120.dp) 1.5.dp else 1.dp, brush = borderBrush, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        if (modelData != null && !loadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(modelData)
                    .crossfade(true)
                    .build(),
                contentDescription = "Ảnh cover bài hát",
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Album else Icons.Default.MusicNote,
                contentDescription = "Ảnh cover mặc định",
                tint = if (style.isGlassStyle) style.accentColor else Color(0xFFFDE68A),
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

@Composable
fun VinylDiscView(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp,
    albumArtUri: String? = null,
    showCenterHole: Boolean = true
) {
    val context = LocalContext.current
    var loadFailed by remember(albumArtUri) { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_angle"
    )

    val currentAngle = if (isPlaying) angle else 0f
    val modelData = remember(albumArtUri) { resolveCoilImageModel(albumArtUri) }
    val hasCoverImage = modelData != null && !loadFailed

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (size >= 120.dp) 16.dp else 4.dp,
                shape = CircleShape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000)
            )
            .clip(CircleShape)
            .background(Color(0xFF18181B))
            .border(if (size >= 120.dp) 2.dp else 1.dp, Color(0xFF27272A), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (hasCoverImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(modelData)
                    .crossfade(true)
                    .build(),
                contentDescription = "Ảnh cover đĩa xoay",
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(currentAngle)
                    .clip(CircleShape)
            )
        }

        // Grooves & vinyl light reflection overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentAngle)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.width / 2f

            if (hasCoverImage) {
                // Subtle translucent concentric disc rings over cover picture
                for (i in 1..4) {
                    val r = maxRadius * (0.48f + i * 0.11f)
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.18f),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            } else {
                val grooveColors = listOf(
                    Color(0xFF27272A),
                    Color(0xFF1F1F23),
                    Color(0xFF3F3F46),
                    Color(0xFF242428)
                )

                for (i in 1..9) {
                    val r = maxRadius * (0.42f + i * 0.058f)
                    drawCircle(
                        color = grooveColors[i % grooveColors.size],
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }
            }

            // Realistic vinyl light reflection (dual sheen)
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                startAngle = 35f,
                sweepAngle = 45f,
                useCenter = true
            )
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                startAngle = 215f,
                sweepAngle = 45f,
                useCenter = true
            )
        }

        if (!hasCoverImage) {
            // Center Vintage Analog Label when no cover art is embedded
            Box(
                modifier = Modifier
                    .size(size * 0.38f)
                    .rotate(currentAngle)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AudioAmber, Color(0xFF78350F))
                        )
                    )
                    .border(if (size >= 120.dp) 2.dp else 1.dp, Color(0xFFFDE68A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = "Vinyl Label",
                    tint = Color(0xFFFEF3C7),
                    modifier = Modifier.size(size * 0.18f)
                )
            }
        }

        // Center spindle hole
        if (showCenterHole) {
            val holeSize = (size * 0.10f).coerceIn(8.dp, 24.dp)
            Box(
                modifier = Modifier
                    .size(holeSize)
                    .clip(CircleShape)
                    .background(Color(0xFFF8FAFC))
                    .border(
                        width = if (size >= 120.dp) 2.dp else 1.dp,
                        color = Color(0xFF475569),
                        shape = CircleShape
                    )
            )
        }
    }
}

private fun resolveCoilImageModel(albumArtUri: String?): Any? {
    if (albumArtUri.isNullOrBlank()) return null
    return if (albumArtUri.startsWith("/")) {
        val file = File(albumArtUri)
        if (file.exists() && file.length() > 0L) file else null
    } else {
        albumArtUri
    }
}
