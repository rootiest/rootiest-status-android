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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.SupportUiState
import dev.rootiest.status.data.MAX_SUBJECT
import dev.rootiest.status.data.NewTicket

// Opening a ticket: the web desk's form, with its choices from the desk (GET /api/form) so the services are only the ones this person may
// see. A prefill (Settings > About > Report an issue) only fills it in; the person still reads it and presses Send. The desk checks it
// again and its refusals are shown above the form.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTicketScreen(
    state: SupportUiState,
    prefill: NewTicket,
    onSignIn: () -> Unit,
    onSend: (NewTicket) -> Unit,
    onBack: () -> Unit,
    onGuest: () -> Unit = {},
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
                !state.signedIn -> SignInPrompt(state.error, onSignIn, onGuest)
                form != null -> TicketForm(form, prefill, state.formErrors, state.sending, replyEmail = state.email.takeIf { state.guest }) { t, _, _ -> onSend(t) }
                state.formErrors.isNotEmpty() -> ErrorList(state.formErrors.joinToString("\n"))
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

// The ticket form. With contact, it first asks for an email address and a name (the email path, GuestSupportScreen); with replyEmail (a
// guest's sign-in) replies always go to that address, so there is no choice to make. A form with no services has no service chooser.
@Composable
internal fun TicketForm(
    form: dev.rootiest.status.data.TicketForm,
    prefill: NewTicket,
    errors: List<String>,
    sending: Boolean,
    contact: Boolean = false,
    replyEmail: String? = null,
    scroll: Boolean = true, // false inside a screen that scrolls itself
    onSend: (NewTicket, email: String, name: String) -> Unit,
) {
    // a prefilled service the person may not see is left out, as the web form does
    val services = form.Services
    var category by rememberSaveable { mutableStateOf(prefill.Category.takeIf { c -> form.Categories.any { it.ID == c } } ?: "") }
    var service by rememberSaveable { mutableStateOf(prefill.Service.takeIf { s -> services.any { it.ID == s } } ?: "") }
    var importance by rememberSaveable { mutableStateOf(prefill.Importance.takeIf { i -> form.Importances.any { it.ID == i } } ?: "normal") }
    var subject by rememberSaveable { mutableStateOf(prefill.Subject.take(MAX_SUBJECT)) }
    var body by rememberSaveable { mutableStateOf(prefill.Body.take(form.MaxBody)) }
    var notifyEmail by rememberSaveable { mutableStateOf(prefill.NotifyEmail) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        (if (scroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding() else Modifier.fillMaxWidth()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        errors.forEach { ErrorBanner(it) }
        if (contact) {
            OutlinedTextField(
                value = email,
                onValueChange = { if (it.length <= 254) email = it.trim() },
                label = { Text("Your email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it.replace('\n', ' ') },
                label = { Text("Your name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Chooser("Category", form.Categories.map { it.ID to it.Label }, category, placeholder = "Choose a category") { category = it }
        if (services.isNotEmpty()) {
            Chooser(
                "Service",
                listOf("" to "Not about a specific service") + services.map { it.ID to "${it.Name} (${it.Group})" },
                service,
            ) { service = it }
        }
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
        when {
            contact -> Text(
                "We email you a link to confirm the address: your ticket is sent when you open it. Replies come by email.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            replyEmail != null -> Text(
                "Replies are emailed to $replyEmail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Row(Modifier.fillMaxWidth().clickable { notifyEmail = !notifyEmail }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = notifyEmail, onCheckedChange = { notifyEmail = it })
                Text("Email me when support replies")
            }
        }
        Button(
            onClick = { onSend(NewTicket(category, service, importance, subject.trim(), body.trim(), notifyEmail || replyEmail != null), email, name.trim()) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (sending) "Sending" else if (contact) "Email me the link" else "Send") }
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
