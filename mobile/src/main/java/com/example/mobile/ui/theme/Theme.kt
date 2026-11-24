package com.example.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mobile.R

val BeVietnamProFamily = FontFamily(
    Font(R.font.be_vietnam_pro, FontWeight.Normal),
    Font(R.font.be_vietnam_pro, FontWeight.Medium),
    Font(R.font.be_vietnam_pro, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val WhitePageBg = Color(0xFFFFFFFF)
val SoftWhiteCanvas = Color(0xFFF8FAFC)
val WhiteCardSurface = Color(0xFFFFFFFF)
val SoftSlateVariant = Color(0xFFF1F5F9)
val SoftSlateBorder = Color(0xFFE2E8F0)

val SkyBluePrimary = Color(0xFF0284C7)
val SkyBlueDeep = Color(0xFF0369A1)
val SkyBlueSoft = Color(0xFFE0F2FE)
val SkyBlueTint = Color(0xFFF0F9FF)

val EmeraldActive = Color(0xFF059669)
val EmeraldSoftBg = Color(0xFFECFDF5)
val EmeraldBorder = Color(0xFFA7F3D0)
val EmeraldTextDark = Color(0xFF065F46)

val AmberNotice = Color(0xFFD97706)
val AmberSoftBg = Color(0xFFFFFBEB)
val AmberBorder = Color(0xFFFDE68A)
val AmberTextDark = Color(0xFF92400E)

val CrimsonStop = Color(0xFFDC2626)

val TextPrimaryDark = Color(0xFF0F172A)
val TextSecondarySlate = Color(0xFF475569)
val TextMutedSlate = Color(0xFF64748B)

private val MobileServerLightColorScheme = lightColorScheme(
    primary = SkyBluePrimary,
    onPrimary = Color.White,
    primaryContainer = SkyBlueSoft,
    onPrimaryContainer = SkyBlueDeep,
    secondary = EmeraldActive,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoftBg,
    onSecondaryContainer = EmeraldTextDark,
    tertiary = AmberNotice,
    onTertiary = Color.White,
    background = WhitePageBg,
    onBackground = TextPrimaryDark,
    surface = WhiteCardSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SoftSlateVariant,
    onSurfaceVariant = TextSecondarySlate,
    outline = SoftSlateBorder
)

val MobileTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = BeVietnamProFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BeVietnamProFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BeVietnamProFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BeVietnamProFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BeVietnamProFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BeVietnamProFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun MobileServerWifiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MobileServerLightColorScheme,
        typography = MobileTypography,
        content = content
    )
}
