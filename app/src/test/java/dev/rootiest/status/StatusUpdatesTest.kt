package dev.rootiest.status

import dev.rootiest.status.data.StatusView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusUpdatesTest {
    private fun view(now: String, headline: String = "h") = StatusView(Now = now, Level = "ok", Headline = headline)

    private val older = view("2026-11-09T12:00:00Z", "older")
    private val newer = view("2026-11-09T12:01:00Z", "newer")

    @Test fun aNewerAnswerGoesOnScreen() {
        val state = StatusUiState(view = older, viewFor = "Chris", error = "Refresh failed")
        val after = state.withBackground(StatusUpdates.Update(newer, "Chris"), "Chris")
        assertEquals("newer", after.view?.Headline)
        assertNull(after.error)
    }

    @Test fun itFillsAnEmptyScreen() {
        assertEquals("newer", StatusUiState().withBackground(StatusUpdates.Update(newer, null), null).view?.Headline)
    }

    @Test fun anAnswerForSomeoneElseIsIgnored() {
        val state = StatusUiState(view = older)
        assertEquals(state, state.withBackground(StatusUpdates.Update(newer, "Chris"), null))
    }

    @Test fun anAnswerNoNewerThanTheScreenIsIgnored() {
        val state = StatusUiState(view = newer)
        assertEquals(state, state.withBackground(StatusUpdates.Update(older, null), null))
        assertEquals(state, state.withBackground(StatusUpdates.Update(newer.copy(Headline = "same time"), null), null))
    }

    @Test fun aRefreshInProgressIsLeftToFinish() {
        assertEquals(true, StatusUiState(view = older, loading = true).withBackground(StatusUpdates.Update(newer, null), null).loading)
    }
}
