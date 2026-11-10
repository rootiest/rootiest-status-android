package dev.rootiest.status.wear

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import com.github.takahirom.roborazzi.captureRoboImage
import dev.rootiest.status.data.BackupLine
import dev.rootiest.status.data.Event
import dev.rootiest.status.data.GroupView
import dev.rootiest.status.data.ServiceView
import dev.rootiest.status.data.StatusView
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The watch's screens at a Pixel Watch's size, without the scaffold's clock, which would differ on every run. ./gradlew recordRoborazziDebug saves new goldens after a deliberate change.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class WatchScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-11-09T12:10:00Z")
    private val gitea = ServiceView(
        Name = "Gitea", Host = "git.rootiest.dev", State = "down", Uptime = "97.10% over 60 days", Detail = "connection refused", Planned = true,
        Backup = BackupLine("Last backup 3 h ago", Level = "warn", At = "2026-11-09T09:10:00Z", Detail = "Atlas → Hestia, 2.2 MiB"),
    )
    private val change = Event(At = "2026-11-09T12:00:00Z", Kind = "service", Subject = "Gitea", From = "up", To = "down", Detail = "connection refused")
    private val message = Event(At = "2026-11-09T11:10:00Z", Kind = "message", Subject = "Notice", Detail = "Maintenance tonight", Ref = "a", Severity = "notice")
    private val view = StatusView(
        Now = "2026-11-09T12:05:00Z", Level = "bad", Headline = "1 service down", Failover = true,
        Groups = listOf(GroupView("Code", listOf(gitea, ServiceView(Name = "Pages", State = "up")))),
        Events = listOf(change, message),
    )

    @Test
    fun theListLeadsWithTheHeadlineItsAgeAndTheLatestMessage() {
        compose.setContent { MaterialTheme { AppScaffold(timeText = {}) { StatusList(view, now) { _, _ -> } } } }
        compose.onNodeWithText("Failover active").assertIsDisplayed()
        compose.onNodeWithText("Updated 5 min ago").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/watch-list.png")
    }

    @Test
    fun aTapOnAServiceOpensIt() {
        var opened: Pair<String, String>? = null
        compose.setContent { MaterialTheme { AppScaffold(timeText = {}) { StatusList(view.copy(Events = null, Failover = false), now) { g, n -> opened = g to n } } } }
        compose.onNodeWithText("Gitea: down (planned)", substring = true).performClick()
        assertEquals("Code" to "Gitea", opened)
    }

    @Test
    fun aServiceShowsItsStateBackupAndChanges() {
        compose.setContent { MaterialTheme { AppScaffold(timeText = {}) { ServiceDetail("Gitea", gitea, listOf(change), now) } } }
        compose.onNodeWithText("down (planned)").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/watch-service.png")
    }

    @Test
    @Config(qualifiers = "w227dp-h700dp-notround-watch-xhdpi")
    fun theWholeServiceFitsItsLines() {
        compose.setContent { MaterialTheme { AppScaffold(timeText = {}) { ServiceDetail("Gitea", gitea, listOf(change), now) } } }
        compose.onNodeWithText("Last backup 3 h ago (Atlas → Hestia, 2.2 MiB)").assertIsDisplayed()
        compose.onNodeWithText("up → down").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/watch-service-whole.png")
    }

    @Test
    @Config(qualifiers = "w227dp-h700dp-notround-watch-xhdpi")
    fun theWholeListKeepsTheOrder() {
        val older = message.copy(Ref = "b", Detail = "An earlier note", At = "2026-11-08T11:10:00Z")
        compose.setContent { MaterialTheme { AppScaffold(timeText = {}) { StatusList(view.copy(Events = listOf(change, message, older)), now) { _, _ -> } } } }
        compose.onNodeWithText("Earlier messages").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/watch-list-whole.png")
    }
}
