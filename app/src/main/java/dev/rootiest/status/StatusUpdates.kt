package dev.rootiest.status

import dev.rootiest.status.data.StatusView
import java.time.Instant
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// What the background job just fetched, for a screen that is open: without it the list shows a push's news only after a pull to refresh, though the notification is already out.
object StatusUpdates {
    data class Update(val view: StatusView, val viewer: String?) // viewer: who it was fetched as

    private val updates = MutableSharedFlow<Update>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val latest: SharedFlow<Update> = updates.asSharedFlow()

    fun publish(view: StatusView, viewer: String?) {
        updates.tryEmit(Update(view, viewer))
    }
}

// The state with the background job's answer in it, unless it was fetched as someone else (a sign-in or sign-out since) or is no newer than what is on screen.
fun StatusUiState.withBackground(update: StatusUpdates.Update, signedIn: String?): StatusUiState {
    if (update.viewer != signedIn) return this
    val shown = view?.let { runCatching { Instant.parse(it.Now) }.getOrNull() }
    val fetched = runCatching { Instant.parse(update.view.Now) }.getOrNull()
    if (shown != null && fetched != null && !fetched.isAfter(shown)) return this
    return copy(view = update.view, viewFor = update.viewer, error = null)
}
