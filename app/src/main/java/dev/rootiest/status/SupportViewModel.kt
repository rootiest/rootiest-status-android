package dev.rootiest.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rootiest.status.data.AuthStore
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
    }

    // Signing out of the app signs out of both: called with the status page's sign-out.
    fun signOut() {
        auth.signOut()
        listing?.cancel()
        reading?.cancel()
        _state.update { SupportUiState(signedIn = false) }
    }
}
