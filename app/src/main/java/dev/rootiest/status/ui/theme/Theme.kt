package dev.rootiest.status.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The Catppuccin colours the app uses, as on the status page: Mocha when dark, Latte when light.
class Palette(
    val Base: Color,
    val Mantle: Color,
    val Surface0: Color,
    val Surface1: Color,
    val Overlay0: Color,
    val Subtext0: Color,
    val Text: Color,
    val Mauve: Color,
    val Blue: Color,
    val Green: Color,
    val Yellow: Color,
    val Red: Color,
)

val Mocha = Palette(
    Base = Color(0xFF1E1E2E),
    Mantle = Color(0xFF181825),
    Surface0 = Color(0xFF313244),
    Surface1 = Color(0xFF45475A),
    Overlay0 = Color(0xFF6C7086),
    Subtext0 = Color(0xFFA6ADC8),
    Text = Color(0xFFCDD6F4),
    Mauve = Color(0xFFCBA6F7),
    Blue = Color(0xFF89B4FA),
    Green = Color(0xFFA6E3A1),
    Yellow = Color(0xFFF9E2AF),
    Red = Color(0xFFF38BA8),
)

val Latte = Palette(
    Base = Color(0xFFEFF1F5),
    Mantle = Color(0xFFE6E9EF),
    Surface0 = Color(0xFFCCD0DA),
    Surface1 = Color(0xFFBCC0CC),
    Overlay0 = Color(0xFF9CA0B0),
    Subtext0 = Color(0xFF6C6F85),
    Text = Color(0xFF4C4F69),
    Mauve = Color(0xFF8839EF),
    Blue = Color(0xFF1E66F5),
    Green = Color(0xFF40A02B),
    Yellow = Color(0xFFDF8E1D),
    Red = Color(0xFFD20F39),
)

// Mocha unless RootiestTheme says otherwise: the widget draws outside it and stays dark.
private val LocalPalette = staticCompositionLocalOf { Mocha }

// The palette of the theme around the caller.
val palette: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

// A level or state from the API to its colour: ok/up/healthy green, warn/standby yellow, bad/down red.
@Composable
@ReadOnlyComposable
fun toneOf(state: String): Color = when (state) {
    "ok", "up", "healthy" -> palette.Green
    "warn", "standby", "degraded" -> palette.Yellow
    "bad", "down", "unhealthy" -> palette.Red
    else -> palette.Surface1
}

@Composable
fun RootiestTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) Mocha else Latte
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme.copy(
                primary = p.Mauve,
                onPrimary = p.Base,
                secondary = p.Blue,
                background = p.Base,
                surface = p.Base,
                surfaceContainer = p.Mantle,
                surfaceContainerHigh = p.Surface0,
                onBackground = p.Text,
                onSurface = p.Text,
                onSurfaceVariant = p.Subtext0,
                outline = p.Overlay0,
                error = p.Red,
            ),
            content = content,
        )
    }
}
