package dev.rootiest.status.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.rootiest.status.StatusUiState
import dev.rootiest.status.data.Event
import dev.rootiest.status.data.GroupView
import dev.rootiest.status.data.HostView
import dev.rootiest.status.data.ServiceListMode
import dev.rootiest.status.data.ServiceView
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.ui.theme.RootiestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The status list as people see it, run on the JVM under Robolectric: StatusScreen takes its whole state as an
// argument, so each test sets a StatusUiState and checks what is on screen.
@RunWith(RobolectricTestRunner::class)
class StatusScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // the public view: rootiest-hub's real answer to a signed-out request, which has no Servers section
    private val public = StatusApi().parse(javaClass.getResource("/status-sample.json")!!.readText())

    // the same snapshot as staff see it, with the Servers section above the groups
    private val staff = public.copy(Hosts = listOf(HostView("Atlas", "healthy"), HostView("Heracles", "healthy")))

    private fun show(state: StatusUiState, listMode: ServiceListMode = ServiceListMode.All) {
        compose.setContent {
            RootiestTheme(dark = true) {
                StatusScreen(state, listMode, onRefresh = {}, onOpenService = { _, _ -> }, onSignIn = {}, onSignOut = {}, onOpenSettings = {})
            }
        }
    }

    private fun showing(start: StatusUiState): (StatusUiState) -> Unit {
        var state by mutableStateOf(start)
        compose.setContent {
            RootiestTheme(dark = true) {
                StatusScreen(state, ServiceListMode.All, onRefresh = {}, onOpenService = { _, _ -> }, onSignIn = {}, onSignOut = {}, onOpenSettings = {})
            }
        }
        return { state = it }
    }

    private val withMessage = public.copy(
        Events = listOf(Event(At = public.Now, Kind = "message", Subject = "Info", Detail = "A message just posted", Ref = "m1", Severity = "info")),
    )

    // The list kept its place on the first section it showed, so a message arriving above it was out of sight until scrolled back to.
    @Test
    fun aNewMessageShowsAtTheTopOfTheList() {
        val set = showing(StatusUiState(view = public))
        compose.onNodeWithText(public.Groups.first().Name).assertIsDisplayed()
        set(StatusUiState(view = withMessage))
        compose.waitForIdle()
        compose.onNodeWithText("A message just posted").assertIsDisplayed()
    }

    // Someone reading further down keeps their place.
    @Test
    fun aNewMessageLeavesAScrolledListWhereItIs() {
        val set = showing(StatusUiState(view = public))
        val last = public.Groups.last().Services.last().Name
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(last))
        compose.onNodeWithText(last).assertIsDisplayed()
        set(StatusUiState(view = withMessage))
        compose.waitForIdle()
        compose.onNodeWithText(last).assertIsDisplayed()
    }

    // #32: the list kept its place on the first group it showed, so the Servers section that signing in adds above
    // that group opened scrolled out of sight.
    @Test
    fun signingInShowsTheServersSectionAtTheTop() {
        var state by mutableStateOf(StatusUiState(view = public))
        compose.setContent {
            RootiestTheme(dark = true) {
                StatusScreen(state, ServiceListMode.All, onRefresh = {}, onOpenService = { _, _ -> }, onSignIn = {}, onSignOut = {}, onOpenSettings = {})
            }
        }
        compose.onNodeWithText(public.Groups.first().Name).assertIsDisplayed()

        // as StatusViewModel does it: the name first, while the public list is still shown, then the staff list
        state = state.copy(loading = true, signedInAs = "Chris")
        compose.waitForIdle()
        state = state.copy(loading = false, view = staff, viewFor = "Chris")
        compose.waitForIdle()

        compose.onNodeWithText("Servers").assertIsDisplayed()
        compose.onNodeWithText("Atlas").assertIsDisplayed()
    }

    @Test
    fun problemsOnlyWithNothingDownSaysSo() {
        show(StatusUiState(view = public), ServiceListMode.ProblemsOnly)
        compose.onNodeWithText("Nothing is down", substring = true).assertIsDisplayed()
    }

    // A section's title stays at the top of the list while its rows scroll under it.
    @Test
    fun sectionTitleSticksWhileItsRowsScroll() {
        val long = StatusView(
            Now = public.Now, Level = "ok", Headline = "All systems operational",
            Groups = listOf(GroupView("Long group", (1..40).map { ServiceView(Name = "Service $it", State = "up") })),
        )
        show(StatusUiState(view = long))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Service 35"))
        compose.onNodeWithText("Service 1").assertDoesNotExist() // the title's own row has long scrolled away
        compose.onNodeWithText("Long group").assertIsDisplayed()
    }

    @Test
    fun failedRefreshKeepsTheListAndSaysWhy() {
        show(StatusUiState(view = public, error = "timeout"))
        compose.onNodeWithText("Refresh failed: timeout").assertIsDisplayed()
        compose.onNodeWithText(public.Groups.first().Services.first().Name).assertIsDisplayed()
    }

    // A server's location is a code ("MO"); a tap on it shows where that is, as a hover or a tap does on the status page.
    @Test
    fun tappingAServersLocationShowsWhereItIs() {
        val prometheus = HostView(
            "Prometheus", "healthy", Note = "MO · hosts Gitea and Mattermost", Location = "MO", Place = "St. Louis, Missouri (Central US)",
        )
        show(StatusUiState(view = public.copy(Hosts = listOf(prometheus)), signedInAs = "Chris", viewFor = "Chris"))
        compose.onNodeWithText("· hosts Gitea and Mattermost", substring = true).assertIsDisplayed()
        compose.onNodeWithText("St. Louis, Missouri (Central US)").assertDoesNotExist()

        compose.onNodeWithText("MO").performClick()
        compose.onNodeWithText("St. Louis, Missouri (Central US)").assertIsDisplayed()
    }
}
