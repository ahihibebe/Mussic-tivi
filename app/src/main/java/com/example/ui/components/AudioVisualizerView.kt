package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppleTvAccent
import com.example.ui.theme.AudioEmeraldBright
import com.example.ui.theme.AudioPrimary
import com.example.ui.theme.AudioSecondary
import com.example.ui.theme.LocalTvStyle

@Composable
fun AudioVisualizerView(
    bands: List<Float>,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    height: Dp = 80.dp
) {
    val barValues = bands.take(barCount)
    val style = LocalTvStyle.current

    Canvas(
        modifier = modifier
            .height(height)
            .fillMaxSize()
    ) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barSpacing = 8.dp.toPx()
        val numBars = barValues.size.coerceAtLeast(1)
        val barWidth = ((totalWidth - (numBars - 1) * barSpacing) / numBars).coerceAtLeast(4f)

        val gradient = Brush.verticalGradient(
            colors = if (style.isGlassStyle) {
                listOf(AppleTvAccent, AudioEmeraldBright)
            } else {
                listOf(AudioPrimary, AudioSecondary)
            },
            startY = 0f,
            endY = totalHeight
        )
        val capColor = if (style.isGlassStyle) Color.White else Color(0xFF60A5FA)

        for (i in barValues.indices) {
            val level = barValues[i].coerceIn(0.06f, 1.0f)
            val barHeight = (totalHeight * level).coerceAtLeast(6f)
            val left = i * (barWidth + barSpacing)
            val top = totalHeight - barHeight

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            drawRoundRect(
                color = capColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, 3.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}
