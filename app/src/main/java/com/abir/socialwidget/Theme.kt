package com.abir.socialwidget

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** What the user picked in Appearance. SYSTEM follows the phone's own light/dark setting. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** Every colour the app uses, so a whole theme can be swapped at once. */
@Immutable
class AppPalette(
    val isDark: Boolean,
    val bg: Color,
    val panel1: Color,
    val panel2: Color,
    val line: Color,
    val accent: Color,
    /** Text and icons placed on top of [accent] (buttons, the switch thumb). */
    val onAccent: Color,
    val ytRed: Color,
    val fbBlue: Color,
    val textHi: Color,
    val textLo: Color,
    val good: Color,
    val warn: Color,
    val danger: Color,
    val heroGradient: List<Color>,
    val ytGradient: List<Color>,
    val fbGradient: List<Color>,
)

val DarkPalette = AppPalette(
    isDark = true,
    bg = Color(0xFF0D0E15),
    panel1 = Color(0xFF161926),
    panel2 = Color(0xFF1F2333),
    line = Color(0xFF2C3147),
    accent = Color(0xFF8C9EFF),
    onAccent = Color(0xFF10132A),
    ytRed = Color(0xFFFF5A4D),
    fbBlue = Color(0xFF5AA2FF),
    textHi = Color(0xFFF2F3F8),
    textLo = Color(0xFFA9AEC2),
    good = Color(0xFF59D68C),
    warn = Color(0xFFFFB454),
    danger = Color(0xFFFF8A80),
    heroGradient = listOf(Color(0xFF262B52), Color(0xFF121422)),
    ytGradient = listOf(Color(0xFF3A1B21), Color(0xFF171822)),
    fbGradient = listOf(Color(0xFF17315A), Color(0xFF171822)),
)

/** Text colours here are chosen to keep at least 4.5:1 contrast on the light surfaces. */
val LightPalette = AppPalette(
    isDark = false,
    bg = Color(0xFFF4F5FA),
    panel1 = Color(0xFFFFFFFF),
    panel2 = Color(0xFFECEEF6),
    line = Color(0xFFD8DCEA),
    accent = Color(0xFF4A5BD4),
    onAccent = Color(0xFFFFFFFF),
    ytRed = Color(0xFFC5221A),
    fbBlue = Color(0xFF1565C0),
    textHi = Color(0xFF15172A),
    textLo = Color(0xFF5A6079),
    good = Color(0xFF17804B),
    warn = Color(0xFFA85A00),
    danger = Color(0xFFC62F2F),
    heroGradient = listOf(Color(0xFFDDE2FF), Color(0xFFF4F5FA)),
    ytGradient = listOf(Color(0xFFFDE8E6), Color(0xFFFFFFFF)),
    fbGradient = listOf(Color(0xFFE1EDFD), Color(0xFFFFFFFF)),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

// The names the screens already use. Each reads the palette of the current theme.
internal val Bg: Color @Composable get() = LocalPalette.current.bg
internal val Panel1: Color @Composable get() = LocalPalette.current.panel1
internal val Panel2: Color @Composable get() = LocalPalette.current.panel2
internal val Line: Color @Composable get() = LocalPalette.current.line
internal val Accent: Color @Composable get() = LocalPalette.current.accent
internal val OnAccent: Color @Composable get() = LocalPalette.current.onAccent
internal val YtRed: Color @Composable get() = LocalPalette.current.ytRed
internal val FbBlue: Color @Composable get() = LocalPalette.current.fbBlue
internal val TextHi: Color @Composable get() = LocalPalette.current.textHi
internal val TextLo: Color @Composable get() = LocalPalette.current.textLo
internal val Good: Color @Composable get() = LocalPalette.current.good
internal val Warn: Color @Composable get() = LocalPalette.current.warn
internal val Danger: Color @Composable get() = LocalPalette.current.danger

fun appColorScheme(p: AppPalette): ColorScheme =
    if (p.isDark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.bg,
            onSurface = p.textHi, onBackground = p.textHi, surfaceVariant = p.panel2,
            onSurfaceVariant = p.textLo, outline = p.line, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.bg,
            onSurface = p.textHi, onBackground = p.textHi, surfaceVariant = p.panel2,
            onSurfaceVariant = p.textLo, outline = p.line, error = p.danger,
        )
    }

/** Whether the screen should be dark for this choice, given what the phone is currently set to. */
fun isDarkFor(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
