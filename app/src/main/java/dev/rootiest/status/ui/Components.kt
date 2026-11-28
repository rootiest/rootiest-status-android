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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.rootiest.status.data.BackupLine
import dev.rootiest.status.data.BarCell
import dev.rootiest.status.data.CheckView
import dev.rootiest.status.data.MailTestLine
import dev.rootiest.status.data.TimeFormat
import dev.rootiest.status.ui.theme.palette
import dev.rootiest.status.ui.theme.toneOf
import java.time.Instant

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
    // Muted, as the status page's headings: an accent here would sit right above the green of services that are up.
    Text(text, style = MaterialTheme.typography.titleMedium, color = palette.Subtext0)
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
internal fun BackupText(backup: BackupLine) = ReportText(backup.text(LocalTimeFormat.current, Instant.now()), backup.Level)

// A service's last mail test, coloured as the backup line: yellow when it failed or is overdue, grey when off or not run yet.
@Composable
internal fun MailTestText(test: MailTestLine) = ReportText(test.text(LocalTimeFormat.current, Instant.now()), test.Level)

// A report line under a service (backup, mail test) in its level's colour.
@Composable
private fun ReportText(text: String, level: String) {
    val color = when (level) {
        "ok" -> MaterialTheme.colorScheme.onSurfaceVariant
        "warn" -> palette.Yellow
        else -> palette.Overlay0
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

// A service's sub-checks on one line, as the page shows them: "SMTP submission up · IMAP up", each state in its colour, and why a
// check is down after its state.
@Composable
internal fun ChecksText(checks: List<CheckView>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unknown = palette.Overlay0
    val tones = checks.map { if (it.State.isEmpty()) unknown else toneOf(it.State) }
    val line = buildAnnotatedString {
        checks.forEachIndexed { i, check ->
            if (i > 0) append(" · ")
            append("${check.Name} ")
            withStyle(SpanStyle(color = tones[i])) { append(check.shownState) }
            if (check.Reason.isNotEmpty()) append(": ${check.Reason}")
        }
    }
    Text(line, style = MaterialTheme.typography.bodySmall, color = muted)
}

// The Times setting, for every screen; App provides it.
internal val LocalTimeFormat = staticCompositionLocalOf { TimeFormat() }
