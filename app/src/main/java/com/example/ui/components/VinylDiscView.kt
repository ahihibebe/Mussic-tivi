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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AudioAmber

@Composable
fun VinylDiscView(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp
) {
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

    Box(
        modifier = modifier
            .size(size)
            .shadow(16.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x40000000))
            .clip(CircleShape)
            .background(Color(0xFF18181B)) // Matte black vinyl
            .border(2.dp, Color(0xFF27272A), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Grooves on vinyl canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(currentAngle)
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.width / 2f

            // Realistic concentric vinyl grooves
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

            // Realistic vinyl light reflection (dual sheen)
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                startAngle = 35f,
                sweepAngle = 45f,
                useCenter = true
            )
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.09f), Color.Transparent),
                    center = center,
                    radius = maxRadius
                ),
                startAngle = 215f,
                sweepAngle = 45f,
                useCenter = true
            )
        }

        // Center Vintage Analog Label
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
                .border(2.dp, Color(0xFFFDE68A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = "Vinyl Label",
                tint = Color(0xFFFEF3C7),
                modifier = Modifier.size(size * 0.18f)
            )

            // Center spindle hole
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF8FAFC))
                    .border(1.5.dp, Color(0xFF94A3B8), CircleShape)
            )
        }
    }
}
