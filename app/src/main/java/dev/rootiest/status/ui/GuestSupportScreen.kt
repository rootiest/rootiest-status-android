package dev.rootiest.status.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.rootiest.status.R
import dev.rootiest.status.SupportUiState
import dev.rootiest.status.data.GuestTicket
import dev.rootiest.status.data.NewTicket

// The email path, for people who cannot sign in (rootiest-hub's /guest): the ticket form with an email address, and below it a way back in for
// an address that has written before. The desk emails a link either way; opened on this phone, Android hands it to the app (MainActivity),
// which signs in as that address. Until then, nothing reaches the desk's queue.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestSupportScreen(
    state: SupportUiState,
    onSend: (GuestTicket) -> Unit,
    onSignInLink: (String) -> Unit,
    onAgain: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact us by email") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val form = state.guestForm
            val sent = state.guestSent
            when {
                sent != null -> CheckInbox(sent, onAgain)
                form != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
                    Text(
                        "No RootiestID, or can't sign in? Questions about your account or anything general can come from your email address.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                    // the form scrolls with the rest of this screen, so it does not scroll by itself
                    Box(Modifier.fillMaxWidth()) {
                        TicketForm(form, NewTicket(), state.guestErrors, state.guestSending, contact = true, scroll = false) { t, email, name ->
                            onSend(GuestTicket(email, name, t.Category, t.Importance, t.Subject, t.Body))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SignInLink(state.guestSending, onSignInLink)
                }
                state.guestErrors.isNotEmpty() -> ErrorList(state.guestErrors.joinToString("\n"))
                else -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

// For an address that has sent tickets before: a link that signs it in, to see them and the replies.
@Composable
private fun SignInLink(sending: Boolean, onSignInLink: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Written to us before?", style = MaterialTheme.typography.titleMedium)
        Text(
            "We email a link that shows your tickets and our replies here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { if (it.length <= 254) email = it.trim() },
            label = { Text("Your email address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { onSignInLink(email) }, enabled = email.isNotBlank() && !sending, modifier = Modifier.fillMaxWidth()) {
            Text("Email me a sign-in link")
        }
    }
}

// The desk took the request: the link is on its way (the desk says the same whether or not it sent one, so this does too).
@Composable
private fun CheckInbox(email: String, onAgain: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Check your inbox", style = MaterialTheme.typography.titleMedium)
        Text("If we can send to $email, it now has an email from Rootiest Support with a link. Open it on this phone to continue here.")
        Text(
            "The link works for 24 hours. Nothing is sent to support until you open it. No email? Check spam, or try again in a few minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAgain) { Text("Back to the form") }
    }
}
