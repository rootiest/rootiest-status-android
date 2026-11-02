package dev.rootiest.status.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Catppuccin Mocha, as on the status page.
object Mocha {
    val Base = Color(0xFF1E1E2E)
    val Mantle = Color(0xFF181825)
    val Surface0 = Color(0xFF313244)
    val Surface1 = Color(0xFF45475A)
    val Overlay0 = Color(0xFF6C7086)
    val Subtext0 = Color(0xFFA6ADC8)
    val Text = Color(0xFFCDD6F4)
    val Mauve = Color(0xFFCBA6F7)
    val Blue = Color(0xFF89B4FA)
    val Green = Color(0xFFA6E3A1)
    val Yellow = Color(0xFFF9E2AF)
    val Red = Color(0xFFF38BA8)
}

// A level or state from the API to its colour: ok/up/healthy green, warn/standby yellow, bad/down red.
fun toneOf(state: String): Color = when (state) {
    "ok", "up", "healthy" -> Mocha.Green
    "warn", "standby", "degraded" -> Mocha.Yellow
    "bad", "down", "unhealthy" -> Mocha.Red
    else -> Mocha.Surface1
}

@Composable
fun RootiestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mocha.Mauve,
            secondary = Mocha.Blue,
            background = Mocha.Base,
            surface = Mocha.Base,
            surfaceContainer = Mocha.Mantle,
            surfaceContainerHigh = Mocha.Surface0,
            onBackground = Mocha.Text,
            onSurface = Mocha.Text,
            onSurfaceVariant = Mocha.Subtext0,
            outline = Mocha.Overlay0,
            error = Mocha.Red,
        ),
        content = content,
    )
}
