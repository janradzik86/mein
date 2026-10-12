package pl.terminownik.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.terminownik.app.domain.ThemeMode

// Motyw inspirowany spiżarnią i ziołami: ciepła zieleń + gliniany akcent.
private val Green40 = Color(0xFF2E6B3E)
private val Green80 = Color(0xFFA7D7AE)
private val Green90 = Color(0xFFC3E8C8)
private val Clay40 = Color(0xFF9A5B33)
private val Clay80 = Color(0xFFF2BE94)
private val Clay90 = Color(0xFFFFE0C7)
private val Warn40 = Color(0xFF9A4A17)
private val Warn80 = Color(0xFFFFB68A)
private val Warn90 = Color(0xFFFFDBCB)
private val Danger40 = Color(0xFFA63A2E)
private val Danger80 = Color(0xFFFFB4A8)
private val Danger90 = Color(0xFFFFDAD4)
private val NeutralLight = Color(0xFFFBF9F4)
private val SurfaceLight = Color(0xFFFCFAF6)
private val NeutralDark = Color(0xFF12140F)
private val SurfaceDark = Color(0xFF1A1C18)

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Clay40,
    onSecondary = Color.White,
    secondaryContainer = Clay90,
    onSecondaryContainer = Color(0xFF341100),
    tertiary = Warn40,
    tertiaryContainer = Warn90,
    error = Danger40,
    errorContainer = Danger90,
    onError = Color.White,
    background = NeutralLight,
    onBackground = Color(0xFF1A1C18),
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE7E3DA),
    onSurfaceVariant = Color(0xFF49473F),
    outline = Color(0xFF7A786E),
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Color(0xFF003916),
    primaryContainer = Color(0xFF14522A),
    onPrimaryContainer = Green90,
    secondary = Clay80,
    onSecondary = Color(0xFF4A2700),
    secondaryContainer = Color(0xFF663A00),
    onSecondaryContainer = Clay90,
    tertiary = Warn80,
    tertiaryContainer = Color(0xFF6B3200),
    error = Danger80,
    errorContainer = Color(0xFF7A2318),
    onError = Color(0xFF5C1A11),
    background = NeutralDark,
    onBackground = Color(0xFFE3E2DC),
    surface = SurfaceDark,
    onSurface = Color(0xFFE3E2DC),
    surfaceVariant = Color(0xFF49473F),
    onSurfaceVariant = Color(0xFFCAC5BA),
    outline = Color(0xFF949087),
)

/**
 * Typografia oparta na `sp`, żeby działało systemowe powiększanie czcionki
 * (wymóg dostępności z briefu: obsługa dużej czcionki).
 */
private val AppTypography = Typography(
    displaySmall = TextStyle(FontFamily.Default, FontWeight.SemiBold, 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(FontFamily.Default, FontWeight.SemiBold, 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(FontFamily.Default, FontWeight.SemiBold, 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(FontFamily.Default, FontWeight.SemiBold, 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(FontFamily.Default, FontWeight.Medium, 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(FontFamily.Default, FontWeight.Medium, 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(FontFamily.Default, FontWeight.Normal, 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(FontFamily.Default, FontWeight.Normal, 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(FontFamily.Default, FontWeight.Normal, 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(FontFamily.Default, FontWeight.Medium, 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(FontFamily.Default, FontWeight.Medium, 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(FontFamily.Default, FontWeight.Medium, 11.sp, lineHeight = 16.sp),
)

@Composable
fun TerminownikTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
