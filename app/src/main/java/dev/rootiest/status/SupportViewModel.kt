package dev.rootiest.status

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rootiest.status.data.AuthStore
import dev.rootiest.status.data.DeskRefused
import dev.rootiest.status.data.NewTicket
import dev.rootiest.status.data.TicketForm
import dev.rootiest.status.data.SignInNeeded
import dev.rootiest.status.data.SupportApi
import dev.rootiest.status.data.Ticket
import dev.rootiest.status.data.TicketThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupportUiState(
    val signedIn: Boolean = false, // to the support desk: its own sign-in, separate from the status page's
    val tickets: List<Ticket>? = null, // the last good list, kept on screen while refreshing or after an error
    val loading: Boolean = false,
    val error: String? = null,
    val thread: TicketThread? = null, // the ticket open on screen, if any
    val threadLoading: Boolean = false,
    val threadError: String? = null,
    val sending: Boolean = false, // a reply or a close on its way; the reply's text stays in the box until the desk takes it
    val sendErrors: List<String> = emptyList(), // why the desk refused it, in its words
    val sent: Int = 0, // counts replies the desk took, so the screen knows to clear the box
    val form: TicketForm? = null, // the new-ticket form's choices
    val formErrors: List<String> = emptyList(),
    val created: Long? = null, // the ticket just opened, for the screen to go to once
    val openRequest: Long? = null, // a ticket whose notification was tapped, for the navigation to open once
)

// The Support screens: the person's tickets on the support desk and one ticket's thread. The desk has its own sign-in (rootiest-hub
// docs/README.md, "Support desk"), started the first time Support opens; RootiestID usually remembers the person, so it is a brief browser visit.
class SupportViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = AuthStore.forSupport(app)
    private val api = SupportApi(auth::credentials)
    private val _state = MutableStateFlow(SupportUiState(signedIn = auth.credentials() != null))
    val state: StateFlow<SupportUiState> = _state.asStateFlow()

    // Whether this run of the app already sent the person to sign in on its own, so a cancelled sign-in is not reopened every time
    var autoSignInTried = false
        private set

    private var listing: Job? = null
    private var reading: Job? = null

    fun refresh() {
        if (!checkSignedIn()) return
        listing?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        listing = viewModelScope.launch {
            try {
                val tickets = api.tickets()
                _state.update { it.copy(tickets = tickets, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e) { msg -> copy(loading = false, error = msg) }
            }
        }
    }

    fun open(id: Long) {
        if (!checkSignedIn()) return
        reading?.cancel()
        // keep showing this ticket while it reloads; another one starts blank
        _state.update { it.copy(thread = it.thread?.takeIf { t -> t.Ticket.ID == id }, threadLoading = true, threadError = null) }
        reading = viewModelScope.launch {
            try {
                val thread = api.thread(id)
                _state.update { it.copy(thread = thread, threadLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e) { msg -> copy(threadLoading = false, threadError = msg) }
            }
        }
    }

    // Replies to the open ticket. The thread comes back as it now is (a reply reopens a closed ticket).
    fun reply(id: Long, body: String) = send { api.reply(id, body).also { thread -> _state.update { it.copy(thread = thread, sent = it.sent + 1) } } }

    fun close(id: Long) = send { api.close(id).also { thread -> _state.update { it.copy(thread = thread) } } }

    private fun send(call: suspend () -> Unit) {
        if (!checkSignedIn() || _state.value.sending) return
        _state.update { it.copy(sending = true, sendErrors = emptyList()) }
        viewModelScope.launch {
            try {
                call()
                _state.update { it.copy(sending = false) }
                refresh() // the list's order and statuses changed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e) { msg -> copy(sending = false, sendErrors = (e as? DeskRefused)?.messages?.ifEmpty { null } ?: listOf(msg)) }
            }
        }
    }

    // The new-ticket form: its choices, once per sign-in.
    fun loadForm() {
        if (!checkSignedIn() || _state.value.form != null) return
        viewModelScope.launch {
            try {
                val form = api.form()
                _state.update { it.copy(form = form) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e) { msg -> copy(formErrors = listOf(msg)) }
            }
        }
    }

    fun create(ticket: NewTicket) {
        if (!checkSignedIn() || _state.value.sending) return
        _state.update { it.copy(sending = true, formErrors = emptyList()) }
        viewModelScope.launch {
            try {
                val thread = api.create(ticket)
                _state.update { it.copy(sending = false, thread = thread, created = thread.Ticket.ID) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e) { msg -> copy(sending = false, formErrors = (e as? DeskRefused)?.messages?.ifEmpty { null } ?: listOf(msg)) }
            }
        }
    }

    // The screen went to the new ticket.
    fun createdShown() = _state.update { it.copy(created = null) }

    fun clearSendErrors() = _state.update { it.copy(sendErrors = emptyList()) }

    // A refused sign-in is dropped, so the screen asks for a new one; anything else is shown.
    private fun failed(e: Exception, show: SupportUiState.(String) -> SupportUiState) {
        if (e is SignInNeeded) {
            auth.signOut()
            _state.update { SupportUiState(signedIn = false) }
        } else {
            _state.update { it.show(e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun checkSignedIn(): Boolean {
        val signedIn = auth.credentials() != null
        if (!signedIn && _state.value.signedIn) _state.update { SupportUiState(signedIn = false) }
        return signedIn
    }

    // The address to open in the browser to sign in to the desk.
    fun signInUrl(auto: Boolean = false): String {
        if (auto) autoSignInTried = true
        return auth.start()
    }

    // The token from dev.rootiest.status:/support-signed-in.
    fun completeSignIn(token: String) {
        if (!auth.complete(token)) {
            _state.update { it.copy(error = "Sign-in did not finish. Try again.") }
            return
        }
        _state.update { it.copy(signedIn = true, error = null) }
        refresh()
        registerPush()
    }

    // Gives the desk this phone's push address, so it can say when support replies. The background check renews it (RefreshWorker).
    private fun registerPush() {
        val address = pushAddress(getApplication()) ?: return
        viewModelScope.launch { runCatching { api.registerPush(address) }.onFailure { Log.w("SupportPush", "register: ${it.message}") } }
    }

    // Signing out of the app signs out of both: called with the status page's sign-out. The desk stops pushing to this phone first, with the
    // sign-in it still has; the sign-in is gone from the phone at once either way.
    fun signOut() {
        val credentials = auth.credentials()
        val address = pushAddress(getApplication())
        auth.signOut()
        listing?.cancel()
        reading?.cancel()
        _state.update { SupportUiState(signedIn = false) }
        if (credentials != null && address != null) {
            viewModelScope.launch { runCatching { SupportApi({ credentials }).unregisterPush(address) } }
        }
    }

    // A support reply's notification was tapped (MainActivity).
    fun requestOpen(id: Long) = _state.update { it.copy(openRequest = id) }

    fun openHandled() = _state.update { it.copy(openRequest = null) }
}
