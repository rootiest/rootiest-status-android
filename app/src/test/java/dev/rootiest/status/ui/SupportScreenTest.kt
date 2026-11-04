package dev.rootiest.status.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.rootiest.status.SupportUiState
import dev.rootiest.status.data.ClockHours
import dev.rootiest.status.data.SupportApi
import dev.rootiest.status.data.TimeFormat
import dev.rootiest.status.ui.theme.RootiestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The Support screens with fixed states, from the support desk's own answers (support-tickets.json, support-thread.json).
@RunWith(RobolectricTestRunner::class)
class SupportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val api = SupportApi({ null })
    private val tickets = api.parseList(javaClass.getResource("/support-tickets.json")!!.readText()).Tickets
    private val thread = api.parseThread(javaClass.getResource("/support-thread.json")!!.readText())
    private val times = TimeFormat(utc = true, hours = ClockHours.H24)

    private fun list(state: SupportUiState, onSignIn: () -> Unit = {}, onOpen: (Long) -> Unit = {}, onWeb: (String) -> Unit = {}) =
        compose.setContent {
            CompositionLocalProvider(LocalTimeFormat provides times) {
                RootiestTheme(dark = true) { SupportScreen(state, onSignIn, onRefresh = {}, onOpen = onOpen, onWeb = onWeb, onBack = {}) }
            }
        }

    @Test
    fun signedOutAsksToSignInToTheDesk() {
        var asked = 0
        list(SupportUiState(signedIn = false), onSignIn = { asked++ })
        compose.onNodeWithText("Sign in to the support desk").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun noTicketsSaysSoAndOffersTheDesk() {
        var opened: String? = null
        list(SupportUiState(signedIn = true, tickets = emptyList()), onWeb = { opened = it })
        compose.onNodeWithText("No tickets yet.").assertIsDisplayed()
        compose.onNodeWithText("Open a ticket").performClick()
        assertEquals("https://support.rootiest.dev/new", opened)
    }

    @Test
    fun listsTicketsWithWhoseTurnItIs() {
        var opened: Long? = null
        list(SupportUiState(signedIn = true, tickets = tickets), onOpen = { opened = it })
        compose.onNodeWithText("Cannot push to Gitea").assertIsDisplayed()
        compose.onNodeWithText("Answered · Gitea", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Closed · General Questions", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cannot push to Gitea").performClick()
        assertEquals(1L, opened)
    }

    @Test
    fun aThreadShowsItsMessagesRenderedAndStaffAsSupport() {
        compose.setContent {
            CompositionLocalProvider(LocalTimeFormat provides times) {
                RootiestTheme(dark = true) { TicketScreen(SupportUiState(signedIn = true, thread = thread), 1, onRefresh = {}, onLink = {}, onBack = {}) }
            }
        }
        compose.onNodeWithText("Cannot push to Gitea").assertIsDisplayed()
        // the Markdown is drawn, not shown as written
        compose.onNodeWithText("Pushing to Gitea fails:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("**Gitea**", substring = true).assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Rootiest Support"))
        compose.onNodeWithText("Rootiest Support").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Reply on the web"))
        compose.onNodeWithText("Reply on the web").assertIsDisplayed()
    }
}
