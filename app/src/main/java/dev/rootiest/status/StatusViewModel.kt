package dev.rootiest.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rootiest.status.data.Appearance
import dev.rootiest.status.data.AuthStore
import dev.rootiest.status.data.NotifyAfter
import dev.rootiest.status.data.RefreshInterval
import dev.rootiest.status.data.ServiceListMode
import dev.rootiest.status.data.SettingsStore
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.TimeFormat
import androidx.glance.appwidget.updateAll
import dev.rootiest.status.widget.StatusWidget
import dev.rootiest.status.widget.RefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val viewFor: String? = null, // who view was fetched as (signedInAs then), so the list can start over when that changes
)

class StatusViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = AuthStore(app)
    private val api = StatusApi(auth::credentials)
    private val _state = MutableStateFlow(StatusUiState(signedInAs = auth.signedInAs()))
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    private val settings = SettingsStore(app)
    private val _appearance = MutableStateFlow(settings.appearance)
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()
    private val _refreshInterval = MutableStateFlow(settings.refreshInterval)
    val refreshInterval: StateFlow<RefreshInterval> = _refreshInterval.asStateFlow()
    private val _notifyAfter = MutableStateFlow(settings.notifyAfter)
    val notifyAfter: StateFlow<NotifyAfter> = _notifyAfter.asStateFlow()
    private val _serviceList = MutableStateFlow(settings.serviceList)
    val serviceList: StateFlow<ServiceListMode> = _serviceList.asStateFlow()
    private val _timeFormat = MutableStateFlow(settings.timeFormat)
    val timeFormat: StateFlow<TimeFormat> = _timeFormat.asStateFlow()
    private val _muted = MutableStateFlow(settings.muted)
    val muted: StateFlow<Set<String>> = _muted.asStateFlow()
    // a notification's Mute button changes the setting while the app may be open
    private val mutedWatch = settings.watchMuted { _muted.value = settings.muted }

    init {
        refresh()
    }

    private var fetching: Job? = null

    // A new refresh cancels the one in flight, so an answer fetched before a sign-in (or sign-out) never lands last.
    fun refresh() {
        fetching?.cancel()
        val who = auth.signedInAs()
        _state.update { it.copy(loading = true, error = null, signedInAs = who) }
        fetching = viewModelScope.launch {
            try {
                val view = api.fetch()
                _state.update { it.copy(view = view, viewFor = who, loading = false) }
                // the widget and the tile show what the app just saw
                StatusCache(getApplication()).save(view)
                StatusWidget().updateAll(getApplication())
                StatusTileService.update(getApplication())
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

    fun setAppearance(value: Appearance) {
        settings.appearance = value
        _appearance.value = value
    }

    fun setServiceList(value: ServiceListMode) {
        settings.serviceList = value
        _serviceList.value = value
    }

    private var redrawWidgets: Job? = null

    // Saves the choice and redraws the widgets, whose "Updated" time follows it: once typing in the pattern pauses.
    fun setTimeFormat(value: TimeFormat) {
        settings.timeFormat = value
        _timeFormat.value = value
        redrawWidgets?.cancel()
        redrawWidgets = viewModelScope.launch {
            delay(1_000)
            StatusWidget().updateAll(getApplication())
        }
    }

    fun setRefreshInterval(value: RefreshInterval) {
        settings.refreshInterval = value
        _refreshInterval.value = value
        RefreshWorker.schedule(getApplication())
    }

    // Mute or unmute notifications about one service or server.
    // read by the next background run; an outage already waiting keeps the time it began, so it is judged by the new delay
    fun setNotifyAfter(value: NotifyAfter) {
        settings.notifyAfter = value
        _notifyAfter.value = value
    }

    override fun onCleared() = settings.stopWatching(mutedWatch)

    // The Quick Settings tile was long-pressed (MainActivity): the navigation opens Settings once.
    private val _settingsRequest = MutableStateFlow(false)
    val settingsRequest: StateFlow<Boolean> = _settingsRequest.asStateFlow()

    fun requestSettings() = _settingsRequest.update { true }

    fun settingsHandled() = _settingsRequest.update { false }

    fun setMuted(name: String, mute: Boolean) {
        val value = if (mute) _muted.value + name else _muted.value - name
        settings.muted = value
        _muted.value = value
    }
}
