package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppLightColorScheme = lightColorScheme(
    primary = AudioPrimary,
    onPrimary = Color.White,
    primaryContainer = AudioPrimaryLight,
    onPrimaryContainer = AudioPrimary,
    secondary = AudioSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = AudioSecondary,
    tertiary = AudioAmber,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // White background as requested
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppLightColorScheme,
        typography = Typography,
        content = content
    )
}
