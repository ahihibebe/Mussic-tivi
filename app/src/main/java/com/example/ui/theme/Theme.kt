package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.model.AppUiStyle

@Immutable
data class TvStyleTokens(
    val uiStyle: AppUiStyle,
    val isGlassStyle: Boolean,
    val hasCustomBackground: Boolean,
    val backgroundBase: Color,
    val sidebarSurface: Color,
    val cardSurface: Color,
    val surfaceVariant: Color,
    val selectedPillSurface: Color,
    val focusedSurface: Color,
    val borderColor: Color,
    val selectedBorderColor: Color,
    val focusedBorderColor: Color,
    val glassSpecularTop: Color,
    val glassSpecularBottom: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentColor: Color,
    val accentContentColor: Color,
    val accentLightBg: Color,
    val accentLightBorder: Color,
    val amberColor: Color,
    val amberBg: Color,
    val amberBorder: Color,
    val redColor: Color,
    val redBg: Color,
    val redBorder: Color,
    val emeraldColor: Color,
    val emeraldBg: Color,
    val emeraldBorder: Color,
    val inactiveTrackColor: Color,
    val focusShadowColor: Color
)

fun buildTvStyleTokens(
    uiStyle: AppUiStyle,
    hasCustomBackground: Boolean
): TvStyleTokens {
    return when (uiStyle) {
        AppUiStyle.CLASSIC_LIGHT -> {
            val cardAlpha = if (hasCustomBackground) 0.86f else 1f
            val sidebarAlpha = if (hasCustomBackground) 0.90f else 1f
            val variantAlpha = if (hasCustomBackground) 0.82f else 1f
            TvStyleTokens(
                uiStyle = uiStyle,
                isGlassStyle = false,
                hasCustomBackground = hasCustomBackground,
                backgroundBase = LightBackground,
                sidebarSurface = LightSurface.copy(alpha = sidebarAlpha),
                cardSurface = LightSurfaceCard.copy(alpha = cardAlpha),
                surfaceVariant = LightSurfaceVariant.copy(alpha = variantAlpha),
                selectedPillSurface = AudioPrimaryLight.copy(alpha = if (hasCustomBackground) 0.90f else 1f),
                focusedSurface = FocusHoverBg,
                borderColor = LightBorder,
                selectedBorderColor = Color(0xFF93C5FD),
                focusedBorderColor = FocusBorder,
                glassSpecularTop = Color.Transparent,
                glassSpecularBottom = Color.Transparent,
                textPrimary = TextPrimary,
                textSecondary = TextSecondary,
                textMuted = TextMuted,
                accentColor = AudioPrimary,
                accentContentColor = Color.White,
                accentLightBg = AudioPrimaryLight,
                accentLightBorder = Color(0xFFBFDBFE),
                amberColor = AudioAmber,
                amberBg = Color(0xFFFEF3C7),
                amberBorder = Color(0xFFFDE68A),
                redColor = AudioRed,
                redBg = Color(0xFFFFE4E6),
                redBorder = Color(0xFFFDA4AF),
                emeraldColor = AudioEmerald,
                emeraldBg = Color(0xFFD1FAE5),
                emeraldBorder = Color(0xFFA7F3D0),
                inactiveTrackColor = Color(0xFFE2E8F0),
                focusShadowColor = FocusShadow
            )
        }

        AppUiStyle.APPLE_TV_GLASS -> {
            TvStyleTokens(
                uiStyle = uiStyle,
                isGlassStyle = true,
                hasCustomBackground = hasCustomBackground,
                backgroundBase = Color.Black,
                sidebarSurface = Color.Black.copy(alpha = 0.52f),
                cardSurface = Color.Black.copy(alpha = 0.44f),
                surfaceVariant = Color.Black.copy(alpha = 0.34f),
                selectedPillSurface = Color.White.copy(alpha = 0.16f),
                focusedSurface = Color.White.copy(alpha = 0.22f),
                borderColor = Color.White.copy(alpha = 0.16f),
                selectedBorderColor = Color.White.copy(alpha = 0.52f),
                focusedBorderColor = AppleTvFocusBorder,
                glassSpecularTop = Color.White.copy(alpha = 0.28f),
                glassSpecularBottom = Color.White.copy(alpha = 0.06f),
                textPrimary = GlassTextPrimary,
                textSecondary = GlassTextSecondary,
                textMuted = GlassTextMuted,
                accentColor = AppleTvAccent,
                accentContentColor = Color(0xFF050507),
                accentLightBg = Color(0x3338BDF8),
                accentLightBorder = Color(0x6638BDF8),
                amberColor = AudioAmberBright,
                amberBg = Color(0x33FBBF24),
                amberBorder = Color(0x66FBBF24),
                redColor = AudioRedBright,
                redBg = Color(0x33FB7185),
                redBorder = Color(0x66FB7185),
                emeraldColor = AudioEmeraldBright,
                emeraldBg = Color(0x3334D399),
                emeraldBorder = Color(0x6634D399),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                focusShadowColor = Color.White.copy(alpha = 0.24f)
            )
        }
    }
}

val LocalTvStyle = staticCompositionLocalOf {
    buildTvStyleTokens(AppUiStyle.CLASSIC_LIGHT, hasCustomBackground = false)
}

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

private val AppAppleTvColorScheme = darkColorScheme(
    primary = AppleTvAccent,
    onPrimary = Color(0xFF090D16),
    primaryContainer = AppleTvAccentLight,
    onPrimaryContainer = Color.White,
    secondary = AudioEmeraldBright,
    onSecondary = Color(0xFF090D16),
    tertiary = AudioAmberBright,
    onTertiary = Color(0xFF090D16),
    background = AppleTvDeepBg,
    onBackground = GlassTextPrimary,
    surface = AppleTvGlassSurface,
    onSurface = GlassTextPrimary,
    surfaceVariant = AppleTvGlassVariant,
    onSurfaceVariant = GlassTextSecondary,
    outline = AppleTvGlassBorder
)

@Composable
fun MyApplicationTheme(
    uiStyle: AppUiStyle = AppUiStyle.CLASSIC_LIGHT,
    hasCustomBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val tokens = buildTvStyleTokens(uiStyle, hasCustomBackground)
    val colorScheme = if (uiStyle == AppUiStyle.APPLE_TV_GLASS) {
        AppAppleTvColorScheme
    } else {
        AppLightColorScheme
    }

    CompositionLocalProvider(LocalTvStyle provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
