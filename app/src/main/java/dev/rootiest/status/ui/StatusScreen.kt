package dev.rootiest.status.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.StatusUiState
import dev.rootiest.status.data.Event
import dev.rootiest.status.data.HostView
import dev.rootiest.status.data.recentMessages
import dev.rootiest.status.data.ServiceView
import dev.rootiest.status.data.ServiceListMode
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.arranged
import dev.rootiest.status.ui.theme.palette
import dev.rootiest.status.ui.theme.toneOf
import java.time.Instant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    state: StatusUiState,
    listMode: ServiceListMode,
    onRefresh: () -> Unit,
    onOpenService: (group: String, name: String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rootiest Status") },
                actions = { AccountMenu(state.signedInAs, onSignIn, onSignOut, onOpenSettings, onOpenSupport) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.view != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val view = state.view
            when {
                view != null -> StatusList(view, state.viewFor, listMode, state.error, onOpenService)
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> ErrorList(state.error ?: "No data")
            }
        }
    }
}

// The account and settings menu: under the name when signed in, under an account icon when not.
@Composable
private fun AccountMenu(name: String?, onSignIn: () -> Unit, onSignOut: () -> Unit, onOpenSettings: () -> Unit, onOpenSupport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        if (name != null) {
            TextButton(onClick = { open = true }) {
                Text(name, color = MaterialTheme.colorScheme.onSurface)
                Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_account_circle), contentDescription = "Account and settings") }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onOpenSettings() })
            DropdownMenuItem(text = { Text("Support") }, onClick = { open = false; onOpenSupport() })
            HorizontalDivider()
            if (name != null) {
                DropdownMenuItem(text = { Text("Sign out") }, onClick = { open = false; onSignOut() })
            } else {
                DropdownMenuItem(text = { Text("Sign in") }, onClick = { open = false; onSignIn() })
            }
        }
    }
}

// The headline sits above the list and never scrolls; inside the list each section's title sticks until the next
// section's title pushes it off. (Sticky headers all stick to the list's top edge, so the headline cannot be one.)
// Signing in or out starts the list over at the top: otherwise it keeps its place on the first section it showed, and
// the Servers section that signing in adds above it opens scrolled out of sight.
@Composable
private fun StatusList(full: StatusView, viewer: String?, listMode: ServiceListMode, error: String?, onOpenService: (String, String) -> Unit) {
    val view = full.arranged(listMode)
    val listState = key(viewer) { rememberLazyListState() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) { Headline(view) }
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
            // The list keeps its place on the item at its top, so anything that appears above that item would land out of sight. With this one always first, a list at the
            // top stays at the top and shows what arrived, and one scrolled further down is left where it is. It needs a height: the list skips an empty first item.
            item(key = "top") { Spacer(Modifier.height(1.dp)) }
            if (error != null) item { Box(Modifier.padding(top = 12.dp)) { ErrorBanner("Refresh failed: $error") } }
            // problems only, with none: say so rather than show an empty list
            if (view.Groups.isEmpty() && view.Hosts.isNullOrEmpty() && full.Groups.isNotEmpty()) item {
                Text(
                    "Nothing is down. Settings > Service list shows every service again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            // the newest message above the status, the older ones after it
            val messages = full.recentMessages()
            messages.firstOrNull()?.let { latest ->
                stickyHeader(key = "latest-message") { SectionHeader("Latest message") }
                item(key = "message:" + latest.Ref) { MessageRow(latest) }
            }
            view.Hosts?.takeIf { it.isNotEmpty() }?.let { hosts ->
                stickyHeader(key = "servers") { SectionHeader("Servers") }
                items(hosts, key = { "host:" + it.Name }) { Box(Modifier.padding(bottom = 8.dp)) { HostRow(it) } }
            }
            view.Groups.forEach { group ->
                stickyHeader(key = "group:" + group.Name) { SectionHeader(group.Name) }
                itemsIndexed(group.Services, key = { _, s -> "service:" + group.Name + "/" + s.Name }) { i, service ->
                    // the group's rows together look like one card: round only its first and last corners
                    val first = i == 0
                    val last = i == group.Services.lastIndex
                    val corner = 12.dp
                    Box(
                        Modifier.fillMaxWidth()
                            .background(
                                palette.Mantle,
                                RoundedCornerShape(
                                    topStart = if (first) corner else 0.dp, topEnd = if (first) corner else 0.dp,
                                    bottomStart = if (last) corner else 0.dp, bottomEnd = if (last) corner else 0.dp,
                                ),
                            )
                            .padding(start = 4.dp, end = 4.dp, top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp),
                    ) { ServiceRow(service) { onOpenService(group.Name, service.Name) } }
                }
            }
            messages.drop(1).takeIf { it.isNotEmpty() }?.let { earlier ->
                stickyHeader(key = "earlier-messages") { SectionHeader("Earlier messages") }
                items(earlier, key = { "message:" + it.Ref }) { Box(Modifier.padding(bottom = 8.dp)) { MessageRow(it) } }
            }
        }
    }
}

// A message someone wrote: its severity, when, the text, and for staff who it is for.
@Composable
private fun MessageRow(message: Event) {
    val color = when (message.Severity) {
        "critical" -> palette.Red
        "warn" -> palette.Yellow
        "notice" -> palette.Blue
        else -> palette.Overlay0
    }
    Column(Modifier.fillMaxWidth().background(palette.Mantle, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Spacer(Modifier.width(8.dp))
            Text(message.Subject, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(LocalTimeFormat.current.format(message.At, Instant.now()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(message.Detail, modifier = Modifier.padding(top = 4.dp))
        if (message.Audience.isNotEmpty()) {
            Text("For " + message.Audience.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// A list, not a plain Box, so pull-to-refresh still works after a failed first load.
@Composable
internal fun ErrorList(message: String) {
    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
        item { ErrorBanner("Could not load status: $message\nPull down to try again.") }
    }
}

@Composable
private fun Headline(view: StatusView) {
    Card(colors = CardDefaults.cardColors(containerColor = toneOf(view.Level))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                view.Headline,
                color = palette.Base,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (view.Failover) Text("Failover active", color = palette.Base)
        }
    }
}

@Composable
private fun HostRow(host: HostView) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(toneOf(host.State))
                Spacer(Modifier.width(8.dp))
                Text(host.Name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(host.State, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (host.Vitals.isNotEmpty()) Text(host.Vitals, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HostNote(host)
        }
    }
}

// The server's note. Its location ("MO") is a tap target whose tooltip says where it is, as on the status page; a hub too old to
// send the place gets the note as it is.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostNote(host: HostView) {
    val style = MaterialTheme.typography.bodySmall
    if (host.Location.isEmpty() || host.Place.isEmpty()) {
        if (host.Note.isNotEmpty()) Text(host.Note, style = style)
        return
    }
    val tip = rememberTooltipState(isPersistent = true) // stays until a tap elsewhere: a place takes a moment to read
    val scope = rememberCoroutineScope()
    val underline = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(host.Place) } },
            state = tip,
        ) {
            Text(
                host.Location,
                style = style,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClickLabel = "Show where it is") { scope.launch { tip.show() } }
                    .padding(vertical = 4.dp)
                    .dottedUnderline(underline)
                    .semantics { contentDescription = host.Place },
            )
        }
        val rest = host.noteRest
        if (rest.isNotEmpty()) Text(" · $rest", style = style)
    }
}

// A dotted line under the content, the page's mark for "tap or hover for more".
private fun Modifier.dottedUnderline(color: Color) = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(
        color,
        Offset(0f, y),
        Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 1.5.dp.toPx())),
    )
}

@Composable
private fun ServiceRow(service: ServiceView, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(toneOf(service.shownState))
            Spacer(Modifier.width(8.dp))
            Text(service.Name, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(service.Uptime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (service.Detail.isNotEmpty()) Text(service.Detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        service.Backup?.let { BackupText(it) }
        Spacer(Modifier.height(6.dp))
        UptimeBar(service.Bar)
    }
}
