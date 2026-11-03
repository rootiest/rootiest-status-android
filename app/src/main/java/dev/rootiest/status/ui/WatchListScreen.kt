package dev.rootiest.status.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.ui.theme.palette
import dev.rootiest.status.ui.theme.toneOf

// One switch per service and server (servers only when the viewer may see them): on means notify about it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchListScreen(view: StatusView?, muted: Set<String>, onMute: (String, Boolean) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Services to notify about") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Text(
                    "Turn one off to stop its notifications. Services added later are on until you turn them off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.Subtext0,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (view == null) {
                item { Text("Loading the list…", color = palette.Subtext0, modifier = Modifier.padding(top = 16.dp)) }
                return@LazyColumn
            }
            view.Hosts?.takeIf { it.isNotEmpty() }?.let { hosts ->
                item { SectionHeader("Servers") }
                item { WatchCard(hosts.map { it.Name to it.State }, muted, onMute) }
            }
            view.Groups.forEach { group ->
                item { SectionHeader(group.Name) }
                item { WatchCard(group.Services.map { it.Name to it.State }, muted, onMute) }
            }
        }
    }
}

@Composable
private fun WatchCard(rows: List<Pair<String, String>>, muted: Set<String>, onMute: (String, Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            rows.forEach { (name, state) ->
                val on = name !in muted
                // the whole row is the target, as with the appearance choice
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = on, role = Role.Switch, onValueChange = { onMute(name, !it) })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Dot(toneOf(state))
                        Spacer(Modifier.width(12.dp))
                        Text(name, color = if (on) palette.Text else palette.Subtext0)
                    }
                    Switch(checked = on, onCheckedChange = null)
                }
            }
        }
    }
}
