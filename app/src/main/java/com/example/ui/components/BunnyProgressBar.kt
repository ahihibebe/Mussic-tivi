package com.example.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalTvStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun BunnyProgressBar(
    progress: Float,
    durationMs: Long,
    elapsedStr: String,
    totalStr: String,
    isPlaying: Boolean,
    enabled: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalTvStyle.current
    var isFocused by remember { mutableStateOf(false) }

    val clampedProgress = progress.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "bunny_run_transition")
    val hopPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bunny_hop_phase"
    )

    val activePhase = if (isPlaying && enabled) hopPhase else 0f
    val bounceWave = if (isPlaying && enabled) abs(sin(activePhase * PI.toFloat())) else 0f
    val strideAngle = if (isPlaying && enabled) sin(activePhase * 2f * PI.toFloat()) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("np_bunny_progress_bar")
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) style.focusedSurface else style.cardSurface)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) style.focusedBorderColor else style.borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = enabled)
            .onKeyEvent { keyEvent ->
                if (!enabled || durationMs <= 0L) return@onKeyEvent false
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val stepMs = 5_000L
                    val currentMs = (clampedProgress * durationMs).toLong()
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekTo((currentMs + stepMs).coerceAtMost(durationMs))
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onSeekTo((currentMs - stepMs).coerceAtLeast(0L))
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(enabled, durationMs) {
                    if (!enabled || durationMs <= 0L) return@pointerInput
                    detectTapGestures { offset ->
                        val pad = 14.dp.toPx()
                        val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                        val ratio = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                        onSeekTo((ratio * durationMs).toLong())
                    }
                }
                .pointerInput(enabled, durationMs) {
                    if (!enabled || durationMs <= 0L) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val pad = 14.dp.toPx()
                        val usable = (size.width - pad * 2f).coerceAtLeast(1f)
                        val ratio = ((change.position.x - pad) / usable).coerceIn(0f, 1f)
                        onSeekTo((ratio * durationMs).toLong())
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val trackPadding = 14.dp.toPx()
                val usableWidth = (size.width - trackPadding * 2f).coerceAtLeast(1f)
                val trackY = size.height * 0.80f
                val trackHeight = 4.5.dp.toPx()
                val bunnyX = trackPadding + usableWidth * clampedProgress

                // Inactive background track
                drawRoundRect(
                    color = style.inactiveTrackColor,
                    topLeft = Offset(trackPadding, trackY - trackHeight / 2f),
                    size = Size(usableWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )

                // Active progress track
                val activeWidth = (bunnyX - trackPadding).coerceAtLeast(0f)
                if (activeWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                style.accentColor.copy(alpha = 0.78f),
                                style.accentColor
                            ),
                            startX = trackPadding,
                            endX = bunnyX
                        ),
                        topLeft = Offset(trackPadding, trackY - trackHeight / 2f),
                        size = Size(activeWidth, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                    )
                }

                // Trailing tiny dust puffs when bunny is actively running
                if (isPlaying && enabled && activeWidth > 14.dp.toPx()) {
                    val puffShift = activePhase * 7.dp.toPx()
                    drawCircle(
                        color = style.accentColor.copy(alpha = (0.36f * (1f - activePhase)).coerceIn(0f, 0.36f)),
                        radius = 2.0.dp.toPx(),
                        center = Offset((bunnyX - 12.dp.toPx() - puffShift).coerceAtLeast(trackPadding), trackY - 3.8.dp.toPx())
                    )
                    drawCircle(
                        color = style.accentColor.copy(alpha = (0.24f * (1f - activePhase)).coerceIn(0f, 0.25f)),
                        radius = 1.4.dp.toPx(),
                        center = Offset((bunnyX - 18.dp.toPx() - puffShift * 1.2f).coerceAtLeast(trackPadding), trackY - 2.4.dp.toPx())
                    )
                }

                // Progress anchor dot directly under the bunny
                drawCircle(
                    color = style.accentColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(bunnyX, trackY)
                )

                // Animated running bunny sitting / bounding above the progress anchor
                val hopOffsetY = bounceWave * 2.4.dp.toPx()
                val bunnyCenterX = bunnyX
                val bunnyGroundY = trackY - 1.8.dp.toPx() - hopOffsetY

                drawRunningBunny(
                    centerX = bunnyCenterX,
                    groundY = bunnyGroundY,
                    stride = strideAngle,
                    isRunning = isPlaying && enabled,
                    furColor = if (style.isGlassStyle) Color(0xFFF8FAFC) else Color(0xFFFFFFFF),
                    outlineColor = style.accentColor,
                    earInnerColor = Color(0xFFF472B6),
                    eyeColor = Color(0xFF0F172A)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = elapsedStr,
                color = if (enabled) style.accentColor else style.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = totalStr,
                color = style.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun DrawScope.drawRunningBunny(
    centerX: Float,
    groundY: Float,
    stride: Float,
    isRunning: Boolean,
    furColor: Color,
    outlineColor: Color,
    earInnerColor: Color,
    eyeColor: Color
) {
    val unit = 0.62.dp.toPx()
    val bodyTilt = if (isRunning) -8f + stride * 9f else 0f

    rotate(degrees = bodyTilt, pivot = Offset(centerX, groundY - 8f * unit)) {
        // Fluffy cotton tail (left/back)
        drawCircle(
            color = outlineColor.copy(alpha = 0.25f),
            radius = 4.2f * unit,
            center = Offset(centerX - 10.5f * unit, groundY - 7.5f * unit)
        )
        drawCircle(
            color = furColor,
            radius = 3.3f * unit,
            center = Offset(centerX - 10.5f * unit, groundY - 7.5f * unit)
        )

        // Hind leg (kicks backward when running)
        val hindSwing = if (isRunning) stride * 4.5f * unit else 0f
        drawLine(
            color = outlineColor,
            start = Offset(centerX - 4f * unit, groundY - 4f * unit),
            end = Offset(centerX - 9f * unit - hindSwing, groundY - 0.5f * unit),
            strokeWidth = 3.4f * unit,
            cap = StrokeCap.Round
        )
        drawLine(
            color = furColor,
            start = Offset(centerX - 4f * unit, groundY - 4f * unit),
            end = Offset(centerX - 9f * unit - hindSwing, groundY - 0.5f * unit),
            strokeWidth = 2.2f * unit,
            cap = StrokeCap.Round
        )

        // Front paw (reaches forward when running)
        val frontSwing = if (isRunning) stride * 4.2f * unit else 0f
        drawLine(
            color = outlineColor,
            start = Offset(centerX + 5f * unit, groundY - 4.5f * unit),
            end = Offset(centerX + 9.5f * unit + frontSwing, groundY - 0.5f * unit),
            strokeWidth = 3.2f * unit,
            cap = StrokeCap.Round
        )
        drawLine(
            color = furColor,
            start = Offset(centerX + 5f * unit, groundY - 4.5f * unit),
            end = Offset(centerX + 9.5f * unit + frontSwing, groundY - 0.5f * unit),
            strokeWidth = 2.0f * unit,
            cap = StrokeCap.Round
        )

        // Bunny torso (subtle outline + fur fill)
        drawOval(
            color = outlineColor,
            topLeft = Offset(centerX - 10f * unit, groundY - 12.5f * unit),
            size = Size(16.5f * unit, 10.5f * unit)
        )
        drawOval(
            color = furColor,
            topLeft = Offset(centerX - 9f * unit, groundY - 11.5f * unit),
            size = Size(14.5f * unit, 8.5f * unit)
        )

        // Back ear (tilts dynamically while running)
        val earAngle = if (isRunning) -26f + stride * 12f else -10f
        rotate(degrees = earAngle, pivot = Offset(centerX + 3.5f * unit, groundY - 15f * unit)) {
            drawOval(
                color = outlineColor,
                topLeft = Offset(centerX + 1.5f * unit, groundY - 26f * unit),
                size = Size(4.4f * unit, 11.5f * unit)
            )
            drawOval(
                color = furColor,
                topLeft = Offset(centerX + 2.2f * unit, groundY - 25.2f * unit),
                size = Size(3.0f * unit, 10.0f * unit)
            )
        }

        // Front ear
        val frontEarAngle = if (isRunning) -16f + stride * 9f else 6f
        rotate(degrees = frontEarAngle, pivot = Offset(centerX + 5.5f * unit, groundY - 15f * unit)) {
            drawOval(
                color = outlineColor,
                topLeft = Offset(centerX + 3.8f * unit, groundY - 26.8f * unit),
                size = Size(4.6f * unit, 12.2f * unit)
            )
            drawOval(
                color = furColor,
                topLeft = Offset(centerX + 4.5f * unit, groundY - 26.0f * unit),
                size = Size(3.2f * unit, 10.6f * unit)
            )
            drawOval(
                color = earInnerColor.copy(alpha = 0.85f),
                topLeft = Offset(centerX + 5.2f * unit, groundY - 24.4f * unit),
                size = Size(1.7f * unit, 7.2f * unit)
            )
        }

        // Bunny head
        drawCircle(
            color = outlineColor,
            radius = 5.4f * unit,
            center = Offset(centerX + 6.2f * unit, groundY - 12.2f * unit)
        )
        drawCircle(
            color = furColor,
            radius = 4.4f * unit,
            center = Offset(centerX + 6.2f * unit, groundY - 12.2f * unit)
        )

        // Eye & pink nose
        drawCircle(
            color = eyeColor,
            radius = 1.05f * unit,
            center = Offset(centerX + 7.8f * unit, groundY - 12.9f * unit)
        )
        drawCircle(
            color = earInnerColor,
            radius = 1.15f * unit,
            center = Offset(centerX + 10.4f * unit, groundY - 11.6f * unit)
        )
    }
}
