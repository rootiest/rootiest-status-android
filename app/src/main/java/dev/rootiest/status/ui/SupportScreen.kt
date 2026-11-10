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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.SupportUiState
import dev.rootiest.status.data.ROOTIESTID_URL
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
    onGuest: () -> Unit = {},
    onSignOut: () -> Unit = {},
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
                state.usingLink -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                !state.signedIn -> SignInPrompt(state.error, onSignIn, onGuest)
                else -> PullToRefreshBox(isRefreshing = state.loading && state.tickets != null, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    val tickets = state.tickets
                    when {
                        tickets != null -> TicketList(tickets, state.error, identity(state), onOpen, onNew, onSignOut)
                        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        else -> ErrorList(state.error ?: "No data")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SignInPrompt(error: String?, onSignIn: () -> Unit, onGuest: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (error != null) ErrorBanner(error)
        Text("Your support tickets", style = MaterialTheme.typography.titleMedium)
        Text(
            "The support desk has its own sign-in, with your RootiestID. If RootiestID remembers you, it only takes a moment.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSignIn) { Text("Sign in to the support desk") }
        SignUpHint()
        GuestHint(onGuest)
    }
}

// For someone who cannot sign in at all (no RootiestID, a lost passkey): the desk's email path.
@Composable
private fun GuestHint(onGuest: () -> Unit) {
    Column {
        Text(
            "Can't sign in at all? Write to us from your email address instead. We email you a link to confirm it, and replies come by email.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onGuest, contentPadding = PaddingValues(0.dp)) { Text("Contact us by email") }
    }
}

// Who the desk's sign-in names, for the top of the list: a RootiestID, or an email address confirmed by a link. Empty when unknown.
internal fun identity(state: SupportUiState): String = when {
    state.guest -> "Signed in with your email address, ${state.email}. Replies are emailed to you too."
    state.name.isNotEmpty() -> "Signed in with RootiestID as ${state.name}"
    else -> ""
}

// The RootiestID page for creating an account, as the desk's own landing page links it.
internal const val SIGN_UP_URL = "$ROOTIESTID_URL/signup"

// For someone without a RootiestID yet: where to make one, then come back and sign in.
@Composable
private fun SignUpHint() {
    val uri = LocalUriHandler.current
    Column {
        Text(
            "New here? Create a free RootiestID in the browser, then come back and sign in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { uri.openUri(SIGN_UP_URL) }, contentPadding = PaddingValues(0.dp)) { Text("Create a free RootiestID") }
    }
}

@Composable
private fun TicketList(tickets: List<Ticket>, error: String?, identity: String, onOpen: (Long) -> Unit, onNew: () -> Unit, onSignOut: () -> Unit) {
    val now = remember { Instant.now() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (error != null) item { ErrorBanner("Refresh failed: $error") }
        // who is signed in, and signing out of the desk alone: an email sign-in has no account menu entry, since the status page knows nothing of it
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(identity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        }
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
