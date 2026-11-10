package dev.rootiest.status.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.rootiest.status.data.BackupLine
import dev.rootiest.status.data.Event
import dev.rootiest.status.data.ServiceView
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.eventsFor
import dev.rootiest.status.data.service
import java.time.Instant

// One service on the watch: its state, uptime, detail, last backup and recent changes, from the same saved snapshot as the list.
// An activity of its own, so the watch's swipe and back button leave it with no navigation library.
class ServiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val group = intent.getStringExtra(GROUP).orEmpty()
        val name = intent.getStringExtra(NAME).orEmpty()
        setContent {
            var view by remember { mutableStateOf<StatusView?>(null) }
            var now by remember { mutableStateOf(Instant.now()) }
            LifecycleResumeEffect(Unit) {
                view = StatusCache(this@ServiceActivity).load()
                now = Instant.now()
                onPauseOrDispose {}
            }
            MaterialTheme { AppScaffold { view?.let { ServiceDetail(name, it.service(group, name), it.eventsFor(name), now) } } }
        }
    }

    companion object {
        private const val GROUP = "group"
        private const val NAME = "name"

        fun open(context: Context, group: String, name: String) {
            context.startActivity(Intent(context, ServiceActivity::class.java).putExtra(GROUP, group).putExtra(NAME, name))
        }
    }
}

// [service] is null when a newer snapshot no longer has it.
@Composable
internal fun ServiceDetail(name: String, service: ServiceView?, events: List<Event>, now: Instant) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item {
                Text(
                    name,
                    color = Color(levelColor(service?.let { levelOf(it.shownState) })),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (service == null) {
                item { Note("No longer on the status page", Muted) }
                return@TransformingLazyColumn
            }
            item { Note(if (service.Planned) "${service.shownState} (planned)" else service.shownState, Color(levelColor(levelOf(service.shownState)))) }
            if (service.Uptime.isNotEmpty()) item { Note(service.Uptime, Muted) }
            if (service.Detail.isNotEmpty()) item { Note(service.Detail, Color.Unspecified) }
            service.Backup?.let { backup -> item { Note(backup.text(WatchTimes, now), backupColor(backup)) } }
            if (service.Host.isNotEmpty()) item { Note(service.Host, Muted) }
            item { Section("Recent changes") }
            if (events.isEmpty()) {
                item { Note("No changes in the recent history.", Muted) }
            } else {
                events.forEach { event -> item { EventRow(event, now) } }
            }
        }
    }
}

// As the phone's BackupText: yellow when older than the hub allows, dimmer when unknown.
private fun backupColor(backup: BackupLine): Color = when (backup.Level) {
    "warn" -> Color(levelColor("warn"))
    "ok" -> Muted
    else -> Color(levelColor(null))
}

@Composable
private fun EventRow(event: Event, now: Instant) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${event.From} → ${event.To}", color = Color(levelColor(levelOf(event.To))), textAlign = TextAlign.Center)
        if (event.Detail.isNotEmpty()) Text(event.Detail, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(WatchTimes.format(event.At, now), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
