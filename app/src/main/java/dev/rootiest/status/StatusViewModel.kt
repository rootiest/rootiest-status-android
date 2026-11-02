package dev.rootiest.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rootiest.status.data.AuthStore
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatusUiState(
    val view: StatusView? = null, // the last good answer, kept on screen while refreshing or after an error
    val loading: Boolean = false,
    val error: String? = null,
    val signedInAs: String? = null, // the RootiestID display name, or null when signed out (or the sign-in expired)
)

class StatusViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = AuthStore(app)
    private val api = StatusApi(auth::credentials)
    private val _state = MutableStateFlow(StatusUiState(signedInAs = auth.signedInAs()))
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    private var fetching: Job? = null

    // A new refresh cancels the one in flight, so an answer fetched before a sign-in (or sign-out) never lands last.
    fun refresh() {
        fetching?.cancel()
        _state.update { it.copy(loading = true, error = null, signedInAs = auth.signedInAs()) }
        fetching = viewModelScope.launch {
            try {
                val view = api.fetch()
                _state.update { it.copy(view = view, loading = false) }
            } catch (e: CancellationException) {
                throw e // replaced by a newer refresh, which owns the state now
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    // The address to open in the browser to sign in.
    fun signInUrl(): String = auth.start()

    // The token from dev.rootiest.status:/signed-in; refreshes so the signed-in view shows at once.
    fun completeSignIn(token: String) {
        if (!auth.complete(token)) {
            _state.update { it.copy(error = "Sign-in did not finish. Try again.") }
            return
        }
        refresh()
    }

    fun signOut() {
        auth.signOut()
        refresh()
    }
}
