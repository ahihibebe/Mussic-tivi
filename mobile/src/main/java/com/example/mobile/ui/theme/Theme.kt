package com.example.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

val DeepSlateBg = Color(0xFF0B1324)
val SlateCardSurface = Color(0xFF131E33)
val SlateElevatedSurface = Color(0xFF1B2945)
val SlateBorder = Color(0xFF283A5E)
val CyanSignal = Color(0xFF38BDF8)
val CyanSignalGlow = Color(0xFF0EA5E9)
val EmeraldActive = Color(0xFF10B981)
val EmeraldSurface = Color(0xFF0D3328)
val AmberNotice = Color(0xFFF59E0B)
val TextPrimaryLight = Color(0xFFF8FAFC)
val TextSecondarySlate = Color(0xFF94A3B8)

private val MobileServerColorScheme = darkColorScheme(
    primary = CyanSignal,
    onPrimary = Color(0xFF05101F),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = EmeraldActive,
    onSecondary = Color(0xFF042016),
    secondaryContainer = EmeraldSurface,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberNotice,
    onTertiary = Color(0xFF1E1302),
    background = DeepSlateBg,
    onBackground = TextPrimaryLight,
    surface = SlateCardSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateElevatedSurface,
    onSurfaceVariant = TextSecondarySlate,
    outline = SlateBorder
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
        colorScheme = MobileServerColorScheme,
        typography = MobileTypography,
        content = content
    )
}
