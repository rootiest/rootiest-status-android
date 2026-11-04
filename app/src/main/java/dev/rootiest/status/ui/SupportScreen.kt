package dev.rootiest.status.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.SupportUiState
import dev.rootiest.status.data.Ticket
import dev.rootiest.status.data.TicketMessage
import dev.rootiest.status.data.statusLabel
import dev.rootiest.status.ui.theme.palette
import java.time.Instant

// Support: the person's tickets on the support desk (rootiest-hub): the list, a ticket's thread with a reply box, and (NewTicketScreen.kt)
// opening one. Everything goes through the desk's own checks; what it refuses is shown in its words.

// open waits for support, answered waits for you, closed is done
@Composable
private fun statusColor(status: String) = when (status) {
    "open" -> palette.Yellow
    "answered" -> palette.Green
    else -> palette.Surface1
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    state: SupportUiState,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Support") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
                actions = { if (state.signedIn) TextButton(onClick = onNew) { Text("New ticket") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.signedIn -> SignInPrompt(state.error, onSignIn)
                else -> PullToRefreshBox(isRefreshing = state.loading && state.tickets != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    val tickets = state.tickets
                    when {
                        tickets != null -> TicketList(tickets, state.error, onOpen, onNew)
                        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        else -> ErrorList(state.error ?: "No data")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SignInPrompt(error: String?, onSignIn: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (error != null) ErrorBanner(error)
        Text("Your support tickets", style = MaterialTheme.typography.titleMedium)
        Text(
            "The support desk has its own sign-in, with your RootiestID. If RootiestID remembers you, it only takes a moment.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSignIn) { Text("Sign in to the support desk") }
    }
}

@Composable
private fun TicketList(tickets: List<Ticket>, error: String?, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val now = remember { Instant.now() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (error != null) item { ErrorBanner("Refresh failed: $error") }
        if (tickets.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No tickets yet.", style = MaterialTheme.typography.titleMedium)
                    Text("Questions, problems and requests go to the support desk; your tickets will show here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onNew) { Text("Open a ticket") }
                }
            }
        }
        items(tickets, key = { it.ID }) { TicketRow(it, now) { onOpen(it.ID) } }
    }
}

@Composable
private fun TicketRow(t: Ticket, now: Instant, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(statusColor(t.Status))
                Spacer(Modifier.width(8.dp))
                Text(t.Subject, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("#${t.ID}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOf(statusLabel(t.Status), t.ServiceName.ifEmpty { t.CategoryLabel }, LocalTimeFormat.current.format(t.Updated, now))
                    .filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScreen(
    state: SupportUiState,
    id: Long,
    onRefresh: () -> Unit,
    onLink: (String) -> Unit,
    onReply: (String) -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
) {
    val thread = state.thread?.takeIf { it.Ticket.ID == id }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ticket #$id") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
                // the one change the owner can make; a reply reopens it
                actions = { if (thread != null && thread.Ticket.Status != "closed") TextButton(onClick = onClose, enabled = !state.sending) { Text("Close ticket") } },
            )
        },
        bottomBar = { if (thread != null) ReplyBox(state, id, closed = thread.Ticket.Status == "closed", onReply) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.threadLoading && thread != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                thread != null -> {
                    val now = remember { Instant.now() }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        if (state.threadError != null) item { ErrorBanner("Refresh failed: ${state.threadError}") }
                        item { TicketSummary(thread.Ticket, now) }
                        items(thread.Messages, key = { it.ID }) { MessageCard(it, now, onLink) }
                    }
                }
                state.threadLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> ErrorList(state.threadError ?: "No data")
            }
        }
    }
}

@Composable
private fun TicketSummary(t: Ticket, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t.Subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(statusColor(t.Status))
            Spacer(Modifier.width(8.dp))
            Text(statusLabel(t.Status))
        }
        Text(
            listOf(t.CategoryLabel, t.ServiceName, t.ImportanceLabel.takeIf { it.isNotEmpty() }?.let { "$it importance" } ?: "")
                .filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Opened ${LocalTimeFormat.current.format(t.Created, now)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(m: TicketMessage, now: Instant, onLink: (String) -> Unit) {
    val staff = m.Role == "staff"
    val colors = MarkdownColors(link = palette.Blue, code = palette.Surface0, quote = MaterialTheme.colorScheme.onSurfaceVariant)
    val body = remember(m.Body, colors) { markdown(m.Body, colors, onLink) }
    Card(colors = CardDefaults.cardColors(containerColor = if (staff) palette.Surface0 else palette.Mantle), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.Author, fontWeight = FontWeight.SemiBold, color = if (staff) palette.Mauve else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(LocalTimeFormat.current.format(m.Created, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(body)
        }
    }
}

// The reply box under a thread. Its text is kept until the desk takes the reply (state.sent goes up), so a failed send loses nothing.
@Composable
private fun ReplyBox(state: SupportUiState, id: Long, closed: Boolean, onReply: (String) -> Unit) {
    var text by rememberSaveable(id) { mutableStateOf("") }
    var seen by rememberSaveable(id) { mutableIntStateOf(state.sent) }
    LaunchedEffect(state.sent) {
        if (state.sent != seen) {
            text = ""
            seen = state.sent
        }
    }
    Surface(color = palette.Mantle, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.sendErrors.forEach { ErrorBanner(it) }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_BODY) text = it },
                    placeholder = { Text(if (closed) "Reply (reopens the ticket)" else "Reply") },
                    maxLines = 6,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onReply(text.trim()) }, enabled = text.isNotBlank() && !state.sending) { Text(if (state.sending) "Sending" else "Send") }
            }
        }
    }
}

private const val MAX_BODY = 5000
