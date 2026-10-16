package com.kostaking.raspored.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kostaking.raspored.data.ThemeMode

val NowRed = Color(0xFFFF3B4F)
val WarnAmber = Color(0xFFFFB020)
val OkGreen = Color(0xFF34D399)

@Immutable
data class ExtraColors(
    val isDark: Boolean,
    val cardBase: Color,
    val textStrong: Color,
    val textMuted: Color,
    val divider: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(true, Color(0xFF0E1122), Color.White, Color(0xFF9AA3C7), Color(0x22FFFFFF))
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8EA2FF),
    onPrimary = Color(0xFF0A0F2E),
    primaryContainer = Color(0xFF26306B),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFC59BFF),
    onSecondary = Color(0xFF1E0B3A),
    secondaryContainer = Color(0xFF3A2766),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFF5EE0C0),
    background = Color(0xFF04050B),
    onBackground = Color(0xFFE6E8F5),
    surface = Color(0xFF0B0E1C),
    onSurface = Color(0xFFE6E8F5),
    surfaceVariant = Color(0xFF161A30),
    onSurfaceVariant = Color(0xFFAAB1D1),
    surfaceContainer = Color(0xFF0F1326),
    surfaceContainerHigh = Color(0xFF151A31),
    surfaceContainerHighest = Color(0xFF1B2140),
    surfaceContainerLow = Color(0xFF0B0E1E),
    surfaceContainerLowest = Color(0xFF05060D),
    outline = Color(0xFF3A4166),
    outlineVariant = Color(0xFF252B48),
    error = NowRed,
    onError = Color.White,
    errorContainer = Color(0xFF4A1020),
    onErrorContainer = Color(0xFFFFD9DE),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F55D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF0A1450),
    secondary = Color(0xFF7A4BD1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDFF),
    onSecondaryContainer = Color(0xFF2A0F5C),
    tertiary = Color(0xFF0E9C7E),
    background = Color(0xFFF4F5FB),
    onBackground = Color(0xFF14172B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14172B),
    surfaceVariant = Color(0xFFE7E9F5),
    onSurfaceVariant = Color(0xFF4A5070),
    surfaceContainer = Color(0xFFF0F1F8),
    surfaceContainerHigh = Color(0xFFE9EBF5),
    surfaceContainerHighest = Color(0xFFE2E5F2),
    surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFFB4B9D3),
    outlineVariant = Color(0xFFD9DCEB),
    error = Color(0xFFD7263D),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410010),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    )
}

@Composable
fun isAppDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun RasporedTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isAppDark(mode)
    val scheme = if (dark) DarkScheme else LightScheme
    val extra = if (dark) {
        ExtraColors(true, Color(0xFF0D1020), Color.White, Color(0xFF9AA3C7), Color(0x22FFFFFF))
    } else {
        ExtraColors(false, Color.White, Color(0xFF14172B), Color(0xFF5B6185), Color(0x1A14172B))
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            // Screens draw on a transparent Scaffold over the space background, so the default
            // content colour must come from the theme (it would otherwise be black).
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

/** Card container colour for a subject colour. */
fun subjectContainer(subject: Color, extra: ExtraColors, strength: Float = 1f): Color =
    if (extra.isDark) lerp(extra.cardBase, subject, 0.20f * strength)
    else lerp(Color.White, subject, 0.16f * strength)

/** Readable text colour on top of [bg]. */
fun contentOn(bg: Color): Color {
    val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (l > 0.55f) Color(0xFF10131F) else Color.White
}

val BadgeTextStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.8.sp)
