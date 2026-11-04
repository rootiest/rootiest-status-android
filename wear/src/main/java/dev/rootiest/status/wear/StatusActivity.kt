package dev.rootiest.status.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.rootiest.status.data.ServiceListMode
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.arranged
import dev.rootiest.status.data.isProblem

// The full list on the watch: the headline, then the servers (when the phone is signed in) and each group, problems first.
// It shows the watch's copy of the phone's last snapshot, read again each time the screen comes back.
class StatusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var view by remember { mutableStateOf<StatusView?>(null) }
            LifecycleResumeEffect(Unit) {
                view = StatusCache(this@StatusActivity).load()
                onPauseOrDispose {}
            }
            MaterialTheme { AppScaffold { StatusList(view) } }
        }
    }
}

@Composable
private fun StatusList(view: StatusView?) {
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
            val shown = view?.arranged(ServiceListMode.ProblemsFirst) ?: return@TransformingLazyColumn
            shown.Hosts?.takeIf { it.isNotEmpty() }?.let { hosts ->
                item { ListHeader { Text("Servers") } }
                hosts.forEach { h -> item { StatusRow(h.Name, h.State, h.isProblem) } }
            }
            shown.Groups.forEach { g ->
                item { ListHeader { Text(g.Name) } }
                g.Services.forEach { s -> item { StatusRow(s.Name, s.State, s.isProblem) } }
            }
        }
    }
}

@Composable
private fun StatusRow(name: String, state: String, problem: Boolean) {
    Text(
        if (problem) "$name: $state" else name,
        color = if (problem) Color(levelColor(if (state == "down" || state == "unhealthy") "bad" else "warn")) else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    )
}
