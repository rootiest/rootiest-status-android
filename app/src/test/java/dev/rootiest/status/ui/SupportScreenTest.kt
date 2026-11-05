package dev.rootiest.status.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.rootiest.status.SupportUiState
import dev.rootiest.status.data.ClockHours
import dev.rootiest.status.data.GuestTicket
import dev.rootiest.status.data.NewTicket
import dev.rootiest.status.data.SupportApi
import dev.rootiest.status.data.TicketForm
import dev.rootiest.status.data.TimeFormat
import dev.rootiest.status.data.reportProblemTicket
import dev.rootiest.status.ui.theme.RootiestTheme
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The Support screens with fixed states, from the support desk's own answers (support-*.json).
@RunWith(RobolectricTestRunner::class)
class SupportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val api = SupportApi({ null })
    private fun resource(name: String) = javaClass.getResource("/$name")!!.readText()
    private val tickets = api.parseList(resource("support-tickets.json")).Tickets
    private val thread = api.parseThread(resource("support-thread.json"))
    private val form = Json { ignoreUnknownKeys = true }.decodeFromString<TicketForm>(resource("support-form.json"))
    private val guestForm = Json { ignoreUnknownKeys = true }.decodeFromString<TicketForm>(resource("support-guest-form.json"))
    private val times = TimeFormat(utc = true, hours = ClockHours.H24)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalTimeFormat provides times) { RootiestTheme(dark = true, content = content) }
    }

    private fun list(state: SupportUiState, onSignIn: () -> Unit = {}, onOpen: (Long) -> Unit = {}, onNew: () -> Unit = {}) =
        show { SupportScreen(state, onSignIn, onRefresh = {}, onOpen = onOpen, onNew = onNew, onBack = {}) }

    @Test
    fun signedOutAsksToSignInToTheDesk() {
        var asked = 0
        list(SupportUiState(signedIn = false), onSignIn = { asked++ })
        compose.onNodeWithText("Sign in to the support desk").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun signedOutOffersToCreateARootiestId() {
        val opened = mutableListOf<String>()
        val uri = object : UriHandler { override fun openUri(uri: String) { opened += uri } }
        show {
            CompositionLocalProvider(LocalUriHandler provides uri) {
                SupportScreen(SupportUiState(signedIn = false), onSignIn = {}, onRefresh = {}, onOpen = {}, onNew = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Create a free RootiestID").performClick()
        assertEquals(listOf("https://auth.rootiest.dev/signup"), opened)
    }

    @Test
    fun noTicketsSaysSoAndOffersANewOne() {
        var asked = 0
        list(SupportUiState(signedIn = true, tickets = emptyList()), onNew = { asked++ })
        compose.onNodeWithText("No tickets yet.").assertIsDisplayed()
        compose.onNodeWithText("Open a ticket").performClick()
        assertEquals(1, asked)
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

    private fun ticket(state: SupportUiState, onReply: (String) -> Unit = {}, onClose: () -> Unit = {}) =
        show { TicketScreen(state, 1, onRefresh = {}, onLink = {}, onReply = onReply, onClose = onClose, onBack = {}) }

    @Test
    fun aThreadShowsItsMessagesRenderedAndStaffAsSupport() {
        ticket(SupportUiState(signedIn = true, thread = thread))
        compose.onNodeWithText("Cannot push to Gitea").assertIsDisplayed()
        // the Markdown is drawn, not shown as written
        compose.onNodeWithText("Pushing to Gitea fails:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("**Gitea**", substring = true).assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Rootiest Support"))
        compose.onNodeWithText("Rootiest Support").assertIsDisplayed()
    }

    @Test
    fun aReplyIsSentAndTheBoxClearsOnlyOnceTheDeskTookIt() {
        val sent = mutableListOf<String>()
        var state by mutableStateOf(SupportUiState(signedIn = true, thread = thread))
        show { TicketScreen(state, 1, onRefresh = {}, onLink = {}, onReply = { sent += it }, onClose = {}, onBack = {}) }
        compose.onNodeWithText("Send").assertIsNotEnabled() // nothing written yet
        compose.onNode(hasSetTextAction()).performTextInput("  Works now, thanks!  ")
        compose.onNodeWithText("Send").performClick()
        assertEquals(listOf("Works now, thanks!"), sent)

        // refused: the words stay, with the desk's reason
        state = state.copy(sendErrors = listOf("You have sent a lot of messages in the last hour. Please wait a little."))
        compose.waitForIdle()
        compose.onNodeWithText("You have sent a lot of messages", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Works now, thanks!", substring = true).assertIsDisplayed()

        // taken: the box empties
        state = state.copy(sendErrors = emptyList(), sent = state.sent + 1)
        compose.waitForIdle()
        compose.onNodeWithText("Works now, thanks!", substring = true).assertDoesNotExist()
    }

    @Test
    fun anOpenTicketCanBeClosed() {
        var closed = 0
        ticket(SupportUiState(signedIn = true, thread = thread), onClose = { closed++ })
        compose.onNodeWithText("Close ticket").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun aClosedTicketSaysAReplyReopensIt() {
        ticket(SupportUiState(signedIn = true, thread = thread.copy(Ticket = thread.Ticket.copy(Status = "closed"))))
        compose.onNodeWithText("Close ticket").assertDoesNotExist()
        compose.onNodeWithText("Reply (reopens the ticket)").assertIsDisplayed()
    }

    private fun newTicket(state: SupportUiState, prefill: NewTicket = NewTicket(), onSend: (NewTicket) -> Unit = {}) =
        show { NewTicketScreen(state, prefill, onSignIn = {}, onSend = onSend, onBack = {}) }

    @Test
    fun reportAProblemFillsInTheFormForThePersonToSend() {
        val sent = mutableListOf<NewTicket>()
        val prefill = reportProblemTicket("1.7.0 (10700)", "16 (API 36)", "Pixel")
        newTicket(SupportUiState(signedIn = true, form = form), prefill, onSend = { sent += it })
        compose.onNodeWithText("Report a Problem").assertIsDisplayed()
        compose.onNodeWithText("Android app (Apps)").assertIsDisplayed()
        compose.onNodeWithText("Android app:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Send").performScrollTo().performClick()
        assertEquals(1, sent.size)
        assertEquals(prefill.copy(Subject = "Android app:", Body = prefill.Body.trim()), sent[0])
    }

    @Test
    fun theDesksRefusalsShowAboveTheForm() {
        newTicket(SupportUiState(signedIn = true, form = form, formErrors = listOf("Choose a category.", "Give the ticket a short subject (up to 150 characters).")))
        compose.onNodeWithText("Choose a category.").assertIsDisplayed()
        compose.onNodeWithText("Give the ticket a short subject", substring = true).assertIsDisplayed()
    }

    @Test
    fun aPrefilledServiceThePersonMayNotSeeIsLeftOut() {
        val sent = mutableListOf<NewTicket>()
        newTicket(SupportUiState(signedIn = true, form = form), NewTicket(Category = "problem", Service = "gitea-mcp", Subject = "x", Body = "y"), onSend = { sent += it })
        compose.onNodeWithText("Not about a specific service").assertIsDisplayed()
        compose.onNodeWithText("Send").performScrollTo().performClick()
        assertEquals("", sent.single().Service)
    }

    @Test
    fun signedOutOffersTheEmailPath() {
        var asked = 0
        show { SupportScreen(SupportUiState(signedIn = false), onSignIn = {}, onRefresh = {}, onOpen = {}, onNew = {}, onBack = {}, onGuest = { asked++ }) }
        compose.onNodeWithText("Contact us by email").performScrollTo().performClick()
        assertEquals(1, asked)
    }

    @Test
    fun theListSaysWhoIsSignedIn() {
        list(SupportUiState(signedIn = true, name = "Pat", email = "pat@example.com", guest = true, tickets = tickets))
        compose.onNodeWithText("Signed in with your email address, pat@example.com", substring = true).assertIsDisplayed()
    }

    @Test
    fun theListNamesARootiestId() {
        list(SupportUiState(signedIn = true, name = "Chris", email = "chris@example.com", tickets = tickets))
        compose.onNodeWithText("Signed in with RootiestID as Chris").assertIsDisplayed()
    }

    private fun guest(state: SupportUiState, onSend: (GuestTicket) -> Unit = {}, onSignInLink: (String) -> Unit = {}) =
        show { GuestSupportScreen(state, onSend = onSend, onSignInLink = onSignInLink, onAgain = {}, onBack = {}) }

    @Test
    fun theEmailFormSendsTheAddressWithTheTicket() {
        val sent = mutableListOf<GuestTicket>()
        guest(SupportUiState(guestForm = guestForm), onSend = { sent += it })
        compose.onNodeWithText("Service").assertDoesNotExist() // a guest's ticket is about no service
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("pat@example.com")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Pat")
        compose.onNodeWithText("Email me the link").performScrollTo().performClick()
        assertEquals(GuestTicket(Email = "pat@example.com", Name = "Pat"), sent.single())
    }

    @Test
    fun anAddressThatWroteBeforeCanAskForASignInLink() {
        val asked = mutableListOf<String>()
        guest(SupportUiState(guestForm = guestForm), onSignInLink = { asked += it })
        compose.onNodeWithText("Email me a sign-in link").performScrollTo().assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performTextInput("pat@example.com")
        compose.onNodeWithText("Email me a sign-in link").performScrollTo().performClick()
        assertEquals(listOf("pat@example.com"), asked)
    }

    @Test
    fun afterTheRequestItSaysToCheckTheInbox() {
        guest(SupportUiState(guestForm = guestForm, guestSent = "pat@example.com"))
        compose.onNodeWithText("Check your inbox").assertIsDisplayed()
        compose.onNodeWithText("pat@example.com", substring = true).assertIsDisplayed()
    }

    @Test
    fun aGuestsNewTicketAlwaysEmailsTheReplies() {
        val sent = mutableListOf<NewTicket>()
        newTicket(SupportUiState(signedIn = true, guest = true, email = "pat@example.com", form = guestForm), onSend = { sent += it })
        compose.onNodeWithText("Email me when support replies").assertDoesNotExist()
        compose.onNodeWithText("Replies are emailed to pat@example.com.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send").performScrollTo().performClick()
        assertEquals(true, sent.single().NotifyEmail)
    }
}
