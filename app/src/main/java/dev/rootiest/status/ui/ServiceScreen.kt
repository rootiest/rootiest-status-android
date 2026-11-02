package dev.rootiest.status.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.StatusUiState
import dev.rootiest.status.data.Event
import dev.rootiest.status.data.ServiceView
import dev.rootiest.status.data.eventsFor
import dev.rootiest.status.data.service
import dev.rootiest.status.data.summary
import dev.rootiest.status.ui.theme.Mocha
import dev.rootiest.status.ui.theme.toneOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(state: StatusUiState, group: String, name: String, onRefresh: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.view != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val view = state.view
            val service = view?.service(group, name)
            when {
                service != null -> ServiceDetailList(service, view.eventsFor(name), state.error)
                // the service left the catalog since the list was shown, or the app restarted here without data yet
                else -> ErrorList(state.error ?: "$name is not in the current status")
            }
        }
    }
}

@Composable
private fun ServiceDetailList(service: ServiceView, events: List<Event>, error: String?) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (error != null) item { ErrorBanner("Refresh failed: $error") }
        item { Summary(service) }
        item { SectionTitle("Recent changes") }
        if (events.isEmpty()) {
            item { Text("No changes in the recent history.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(events) { EventRow(it) }
        }
        item { SectionTitle("Daily uptime (UTC days)") }
        // newest first, unlike the strip; days before the history began are left out
        items(service.Bar.asReversed().filter { it.Class != "none" }, key = { it.Day }) { cell ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Dot(toneOf(cell.Class))
                Spacer(Modifier.width(8.dp))
                Text(cell.Day)
                Spacer(Modifier.weight(1f))
                Text(cell.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Summary(service: ServiceView) {
    val uri = LocalUriHandler.current
    Card(colors = CardDefaults.cardColors(containerColor = Mocha.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(toneOf(service.State))
                Spacer(Modifier.width(8.dp))
                Text(service.State, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (service.Planned) Text("  (planned)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(service.Uptime, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (service.Detail.isNotEmpty()) Text(service.Detail)
            // a Direct service is probed by TCP connect: its Host is a name, not a web page
            if (service.Host.isNotEmpty()) {
                if (service.Direct) {
                    Text(service.Host, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        service.Host,
                        color = Mocha.Blue,
                        modifier = Modifier.clickable { uri.openUri("https://${service.Host}") }.padding(vertical = 4.dp),
                    )
                }
            }
            UptimeBar(service.Bar)
        }
    }
}

@Composable
private fun EventRow(event: Event) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(toneOf(event.To))
            Spacer(Modifier.width(8.dp))
            Text("${event.From} → ${event.To}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(localTime(event.At), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (event.Detail.isNotEmpty()) Text(event.Detail, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(top = 8.dp), color = Mocha.Surface0)
    }
}

private val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun localTime(at: String): String =
    runCatching { timeFormat.format(Instant.parse(at).atZone(ZoneId.systemDefault())) }.getOrDefault(at)
