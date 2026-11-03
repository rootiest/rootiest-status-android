package dev.rootiest.status.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.rootiest.status.data.BackupLine
import dev.rootiest.status.data.BarCell
import dev.rootiest.status.data.TimeFormat
import dev.rootiest.status.ui.theme.palette
import dev.rootiest.status.ui.theme.toneOf

// Small pieces shared by the screens.

@Composable
internal fun ErrorBanner(text: String) {
    Text(
        text,
        color = palette.Base,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.Red).padding(12.dp),
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = palette.Mauve)
}

// A list section's title as a sticky header: opaque, so the rows scroll under it, and pushed off by the next one.
@Composable
internal fun SectionHeader(text: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 16.dp, bottom = 8.dp)) {
        SectionTitle(text)
    }
}

@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

// One cell per day, oldest first, like the strip on the web page.
@Composable
internal fun UptimeBar(cells: List<BarCell>) {
    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        cells.forEach { cell ->
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp))
                    .background(if (cell.Class == "none") palette.Surface0 else toneOf(cell.Class)),
            )
        }
    }
}

// A service's last backup, as the page shows it: yellow when older than the hub allows, grey when unknown.
@Composable
internal fun BackupText(backup: BackupLine) {
    val color = when (backup.Level) {
        "ok" -> MaterialTheme.colorScheme.onSurfaceVariant
        "warn" -> palette.Yellow
        else -> palette.Overlay0
    }
    Text(backup.Text, style = MaterialTheme.typography.bodySmall, color = color)
}

// The Times setting, for every screen; App provides it.
internal val LocalTimeFormat = staticCompositionLocalOf { TimeFormat() }
