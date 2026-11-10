package dev.rootiest.status.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.rootiest.status.data.Event
import dev.rootiest.status.data.ServiceListMode
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.TimeFormat
import dev.rootiest.status.data.TimeStyle
import dev.rootiest.status.data.arranged
import dev.rootiest.status.data.isProblem
import dev.rootiest.status.data.recentMessages
import dev.rootiest.status.data.updatedAgo
import java.time.Instant

// The full list on the watch: the headline and how old it is, the latest message, then the servers (when the phone is signed in)
// and each group, problems first, and the earlier messages. It shows the watch's copy of the phone's last snapshot, read again
// each time the screen comes back. A tap on a service opens it (ServiceActivity).
class StatusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var view by remember { mutableStateOf<StatusView?>(null) }
            var now by remember { mutableStateOf(Instant.now()) }
            LifecycleResumeEffect(Unit) {
                view = StatusCache(this@StatusActivity).load()
                now = Instant.now()
                onPauseOrDispose {}
            }
            MaterialTheme {
                AppScaffold {
                    StatusList(view, now) { group, name -> ServiceActivity.open(this@StatusActivity, group, name) }
                }
            }
        }
    }
}

@Composable
internal fun StatusList(view: StatusView?, now: Instant, onOpenService: (group: String, name: String) -> Unit) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item {
                Text(
                    view?.Headline ?: "Open Rootiest Status on the phone",
                    color = Color(levelColor(view?.Level)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (view?.Failover == true) item { Note("Failover active", Color(levelColor("warn"))) }
            view?.updatedAgo(now)?.let { updated -> item { Note(updated, Muted) } }

            val messages = view?.recentMessages().orEmpty()
            messages.firstOrNull()?.let { latest ->
                item { Section("Latest message") }
                item { MessageRow(latest, now) }
            }
            val shown = view?.arranged(ServiceListMode.ProblemsFirst)
            shown?.Hosts?.takeIf { it.isNotEmpty() }?.let { hosts ->
                item { Section("Servers") }
                hosts.forEach { h -> item { StatusRow(h.Name, h.State, h.isProblem, h.Planned) } }
            }
            shown?.Groups?.forEach { g ->
                item { Section(g.Name) }
                g.Services.forEach { s ->
                    item { StatusRow(s.Name, s.shownState, s.isProblem, s.Planned) { onOpenService(g.Name, s.Name) } }
                }
            }
            messages.drop(1).takeIf { it.isNotEmpty() }?.let { earlier ->
                item { Section("Earlier messages") }
                earlier.forEach { m -> item { MessageRow(m, now) } }
            }
        }
    }
}

// Times on the watch are relative: it has no Settings of its own, and "3 h ago" is the shortest thing that fits.
internal val WatchTimes = TimeFormat(style = TimeStyle.Relative)

// Catppuccin Mocha's Subtext, for what is secondary
internal val Muted = Color(0xFFA6ADC8)

// A section's title, in Catppuccin blue so it stands apart from the rows
@Composable
internal fun Section(name: String) {
    ListHeader { Text(name, color = Color(0xFF89B4FA), style = MaterialTheme.typography.titleMedium) }
}

// A short centred line of its own
@Composable
internal fun Note(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}

// A row as on the phone: a dot in its state's colour, the name, and the state when it is a problem. One with onClick is as tall
// as a finger needs.
@Composable
private fun StatusRow(name: String, state: String, problem: Boolean, planned: Boolean, onClick: (() -> Unit)? = null) {
    val tone = Color(levelColor(levelOf(state)))
    val row = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
    Box(row, contentAlignment = Alignment.Center) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = tone)) { append("● ") }
                append(name)
                if (problem) withStyle(SpanStyle(color = tone)) { append(if (planned) ": $state (planned)" else ": $state") }
            },
            textAlign = TextAlign.Center, // centred: a round screen is widest in the middle
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
    }
}

// A message someone wrote, as on the phone: its severity, the text, when, and for staff who it is for.
@Composable
private fun MessageRow(message: Event, now: Instant) {
    val tone = when (message.Severity) {
        "critical" -> Color(levelColor("bad"))
        "warn" -> Color(levelColor("warn"))
        "notice" -> Color(0xFF89B4FA)
        else -> Muted
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = tone)) { append("● ") }
                append(message.Subject)
            },
            textAlign = TextAlign.Center,
        )
        Text(message.Detail, textAlign = TextAlign.Center)
        Text(WatchTimes.format(message.At, now), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        if (message.Audience.isNotEmpty()) {
            Text("For " + message.Audience.joinToString(", "), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

// A server's or service's state to the level whose colour it takes, as the phone's toneOf: up and healthy green, starting and
// degraded yellow, unhealthy peach, standby sapphire, down red.
internal fun levelOf(state: String): String = when (state) {
    "ok", "up", "healthy" -> "ok"
    "warn", "starting", "degraded" -> "warn"
    "unhealthy" -> "unhealthy"
    "standby" -> "standby"
    "bad", "down" -> "bad"
    else -> "none"
}
