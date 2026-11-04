package dev.rootiest.status.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.SupportUiState
import dev.rootiest.status.data.MAX_SUBJECT
import dev.rootiest.status.data.NewTicket

// Opening a ticket: the web desk's form, with its choices from the desk (GET /api/form) so the services are only the ones this person may
// see. A prefill (Settings > About > Report a problem) only fills it in; the person still reads it and presses Send. The desk checks it
// again and its refusals are shown above the form.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTicketScreen(
    state: SupportUiState,
    prefill: NewTicket,
    onSignIn: () -> Unit,
    onSend: (NewTicket) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New ticket") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val form = state.form
            when {
                !state.signedIn -> SignInPrompt(state.error, onSignIn)
                form != null -> TicketForm(state, form, prefill, onSend)
                state.formErrors.isNotEmpty() -> ErrorList(state.formErrors.joinToString("\n"))
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun TicketForm(state: SupportUiState, form: dev.rootiest.status.data.TicketForm, prefill: NewTicket, onSend: (NewTicket) -> Unit) {
    // a prefilled service the person may not see is left out, as the web form does
    val services = form.Services
    var category by rememberSaveable { mutableStateOf(prefill.Category.takeIf { c -> form.Categories.any { it.ID == c } } ?: "") }
    var service by rememberSaveable { mutableStateOf(prefill.Service.takeIf { s -> services.any { it.ID == s } } ?: "") }
    var importance by rememberSaveable { mutableStateOf(prefill.Importance.takeIf { i -> form.Importances.any { it.ID == i } } ?: "normal") }
    var subject by rememberSaveable { mutableStateOf(prefill.Subject.take(MAX_SUBJECT)) }
    var body by rememberSaveable { mutableStateOf(prefill.Body.take(form.MaxBody)) }
    var notifyEmail by rememberSaveable { mutableStateOf(prefill.NotifyEmail) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.formErrors.forEach { ErrorBanner(it) }
        Chooser("Category", form.Categories.map { it.ID to it.Label }, category, placeholder = "Choose a category") { category = it }
        Chooser(
            "Service",
            listOf("" to "Not about a specific service") + services.map { it.ID to "${it.Name} (${it.Group})" },
            service,
        ) { service = it }
        Chooser("Importance", form.Importances.map { it.ID to it.Label }, importance) { importance = it }
        OutlinedTextField(
            value = subject,
            onValueChange = { if (it.length <= MAX_SUBJECT) subject = it.replace('\n', ' ') },
            label = { Text("Subject") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body,
            onValueChange = { if (it.length <= form.MaxBody) body = it },
            label = { Text("Message") },
            supportingText = { Text("Markdown works: **bold**, *italics*, `code`, lists and links.") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().clickable { notifyEmail = !notifyEmail }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = notifyEmail, onCheckedChange = { notifyEmail = it })
            Text("Email me when support replies")
        }
        Button(
            onClick = { onSend(NewTicket(category, service, importance, subject.trim(), body.trim(), notifyEmail)) },
            enabled = !state.sending,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.sending) "Sending" else "Send") }
    }
}

// A labelled choice from a fixed list, as a read-only field that opens a menu.
@Composable
private fun Chooser(label: String, options: List<Pair<String, String>>, selected: String, placeholder: String = "", onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // the field itself is read-only: this covers it, so a tap anywhere opens the menu
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = if (id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    onClick = { onSelect(id); open = false },
                )
            }
        }
    }
}
