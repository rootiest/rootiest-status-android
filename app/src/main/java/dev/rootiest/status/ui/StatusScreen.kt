package dev.rootiest.status.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rootiest.status.StatusViewModel
import dev.rootiest.status.data.BarCell
import dev.rootiest.status.data.GroupView
import dev.rootiest.status.data.HostView
import dev.rootiest.status.data.ServiceView
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.ui.theme.Mocha
import dev.rootiest.status.ui.theme.toneOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(vm: StatusViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Rootiest Status") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.view != null,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val view = state.view
            when {
                view != null -> StatusList(view, state.error)
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> ErrorList(state.error ?: "No data")
            }
        }
    }
}

@Composable
private fun StatusList(view: StatusView, error: String?) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (error != null) item { ErrorBanner("Refresh failed: $error") }
        item { Headline(view) }
        view.Hosts?.takeIf { it.isNotEmpty() }?.let { hosts ->
            item { SectionTitle("Servers") }
            items(hosts, key = { "host:" + it.Name }) { HostRow(it) }
        }
        view.Groups.forEach { group ->
            item(key = "group:" + group.Name) { GroupCard(group) }
        }
    }
}

// A list, not a plain Box, so pull-to-refresh still works after a failed first load.
@Composable
private fun ErrorList(message: String) {
    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
        item { ErrorBanner("Could not load status: $message\nPull down to try again.") }
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Text(
        text,
        color = Mocha.Base,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Mocha.Red).padding(12.dp),
    )
}

@Composable
private fun Headline(view: StatusView) {
    Card(colors = CardDefaults.cardColors(containerColor = toneOf(view.Level))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(view.Headline, color = Mocha.Base, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (view.Failover) Text("Failover active", color = Mocha.Base)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Mocha.Mauve)
}

@Composable
private fun HostRow(host: HostView) {
    Card(colors = CardDefaults.cardColors(containerColor = Mocha.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(toneOf(host.State))
                Spacer(Modifier.width(8.dp))
                Text(host.Name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(host.State, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (host.Vitals.isNotEmpty()) Text(host.Vitals, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (host.Note.isNotEmpty()) Text(host.Note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GroupCard(group: GroupView) {
    Card(colors = CardDefaults.cardColors(containerColor = Mocha.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(group.Name)
            group.Services.forEach { ServiceRow(it) }
        }
    }
}

@Composable
private fun ServiceRow(service: ServiceView) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(toneOf(service.State))
            Spacer(Modifier.width(8.dp))
            Text(service.Name, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(service.Uptime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (service.Detail.isNotEmpty()) Text(service.Detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        UptimeBar(service.Bar)
    }
}

// One cell per day, oldest first, like the strip on the web page.
@Composable
private fun UptimeBar(cells: List<BarCell>) {
    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        cells.forEach { cell ->
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp))
                    .background(if (cell.Class == "none") Mocha.Surface0 else toneOf(cell.Class)),
            )
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}
