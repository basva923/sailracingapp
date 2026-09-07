package com.sailracing.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A single high-contrast dark look: pure black (AMOLED pixels off) with white text and a few saturated
 * accents that read from the back of the boat in sunlight.
 */
object RaceColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF9AA0A6)
    val Dim = Color(0xFF3A3F44)
    val Surface = Color(0xFF141414)
    val Early = Color(0xFF3DDC84)
    val Late = Color(0xFFFF5252)
    val Warning = Color(0xFFFFC107)
    val Info = Color(0xFF4FC3F7)
    val Wind = Color(0xFF4FC3F7)
    val Estimated = Color(0xFFFFC107)
    val Port = Color(0xFFFF5252)
    val Starboard = Color(0xFF3DDC84)
}

private val ColorScheme = darkColorScheme(
    primary = RaceColors.Info,
    onPrimary = RaceColors.Black,
    secondary = RaceColors.Warning,
    onSecondary = RaceColors.Black,
    tertiary = RaceColors.Early,
    onTertiary = RaceColors.Black,
    background = RaceColors.Black,
    onBackground = RaceColors.White,
    surface = RaceColors.Black,
    onSurface = RaceColors.White,
    surfaceVariant = RaceColors.Surface,
    onSurfaceVariant = RaceColors.Muted,
    surfaceContainer = RaceColors.Surface,
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
    outline = RaceColors.Dim,
    error = RaceColors.Late,
    onError = RaceColors.Black,
)

private val RaceTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 96.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 64.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 44.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 1.sp),
)

@Composable
fun SailRacingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = RaceTypography, content = content)
}
