package dev.rootiest.status.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The Catppuccin colors the app uses, as on the status page: Mocha when dark, Latte when light. Accent, OnAccent and
// AccentText are the Rootiest palette's accent roles (rootiest-icons themes/palette.toml), as on the support desk:
// green on Mocha; flamingo on Latte, too light for text on Base, so its accent text is a darker flamingo.
class Palette(
    val Base: Color,
    val Mantle: Color,
    val Surface0: Color,
    val Surface1: Color,
    val Overlay0: Color,
    val Subtext0: Color,
    val Text: Color,
    val Blue: Color,
    val Green: Color,
    val Yellow: Color,
    val Peach: Color,
    val Sapphire: Color,
    val Red: Color,
    val Accent: Color, // fills: buttons, highlights
    val OnAccent: Color, // text on an Accent fill
    val AccentText: Color, // accent-colored text on Base
)

val Mocha = Palette(
    Base = Color(0xFF1E1E2E),
    Mantle = Color(0xFF181825),
    Surface0 = Color(0xFF313244),
    Surface1 = Color(0xFF45475A),
    Overlay0 = Color(0xFF6C7086),
    Subtext0 = Color(0xFFA6ADC8),
    Text = Color(0xFFCDD6F4),
    Blue = Color(0xFF89B4FA),
    Green = Color(0xFFA6E3A1),
    Yellow = Color(0xFFF9E2AF),
    Peach = Color(0xFFFAB387),
    Sapphire = Color(0xFF74C7EC),
    Red = Color(0xFFF38BA8),
    Accent = Color(0xFFA6E3A1), // green
    OnAccent = Color(0xFF11111B), // crust
    AccentText = Color(0xFFA6E3A1), // green
)

val Latte = Palette(
    Base = Color(0xFFEFF1F5),
    Mantle = Color(0xFFE6E9EF),
    Surface0 = Color(0xFFCCD0DA),
    Surface1 = Color(0xFFBCC0CC),
    Overlay0 = Color(0xFF9CA0B0),
    Subtext0 = Color(0xFF6C6F85),
    Text = Color(0xFF4C4F69),
    Blue = Color(0xFF1E66F5),
    Green = Color(0xFF40A02B),
    Yellow = Color(0xFFDF8E1D),
    Peach = Color(0xFFFE640B),
    Sapphire = Color(0xFF209FB5),
    Red = Color(0xFFD20F39),
    Accent = Color(0xFFDD7878), // flamingo: 2.6:1 on Base, so for fills only
    OnAccent = Color(0xFF11111B), // Mocha crust: 6.3:1 on flamingo
    AccentText = Color(0xFF9B5454), // flamingo darkened 30%: 4.9:1 on Base
)

// Mocha unless RootiestTheme says otherwise: the widget draws outside it and stays dark.
private val LocalPalette = staticCompositionLocalOf { Mocha }

// The palette of the theme around the caller.
val palette: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

// A level or state from the API to its colour, as on the status page: ok/up/healthy green, warn/starting yellow,
// unhealthy peach, standby sapphire, bad/down red.
@Composable
@ReadOnlyComposable
fun toneOf(state: String): Color = when (state) {
    "ok", "up", "healthy" -> palette.Green
    "warn", "starting", "degraded" -> palette.Yellow
    "unhealthy" -> palette.Peach
    "standby" -> palette.Sapphire
    "bad", "down" -> palette.Red
    else -> palette.Surface1
}

@Composable
fun RootiestTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) Mocha else Latte
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            // Material draws primary both as text (text buttons, the selected choice) and as fills behind onPrimary, so it is
            // the readable accent text; the container roles take the accent fill.
            colorScheme = scheme.copy(
                primary = p.AccentText,
                onPrimary = if (dark) p.OnAccent else p.Base,
                primaryContainer = p.Accent,
                onPrimaryContainer = p.OnAccent,
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
