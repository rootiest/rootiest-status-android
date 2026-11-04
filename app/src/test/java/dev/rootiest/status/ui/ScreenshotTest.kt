package dev.rootiest.status.ui

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.rootiest.status.StatusUiState
import dev.rootiest.status.data.Appearance
import dev.rootiest.status.data.ClockHours
import dev.rootiest.status.data.HostView
import dev.rootiest.status.data.NotifyAfter
import dev.rootiest.status.data.RefreshInterval
import dev.rootiest.status.data.ServiceListMode
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.TimeFormat
import dev.rootiest.status.data.WidgetScope
import dev.rootiest.status.ui.theme.RootiestTheme
import dev.rootiest.status.widget.StatusWidget
import dev.rootiest.status.widget.WidgetStore
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Screenshots of the screens and the widget, compared with the golden images in src/test/screenshots. They catch what
// the behaviour tests cannot see: colours, spacing, corners. ./gradlew recordRoborazziDebug saves new goldens after a
// deliberate change; tests/run-tests.sh verifies against them.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val public = StatusApi().parse(javaClass.getResource("/status-sample.json")!!.readText())

    // as staff see it, with something wrong, so the red and yellow states are in the picture too
    private val staff = public.copy(
        Level = "bad",
        Headline = "1 service down",
        Hosts = listOf(HostView("Atlas", "healthy", Vitals = "load 0.4 · mem 38%"), HostView("Heracles", "degraded", Note = "disk 91%")),
        Groups = public.Groups.map { g -> g.copy(Services = g.Services.map { if (it.Name == "Gists") it.copy(State = "down", Uptime = "97.1%") else it }) },
    )

    // the clock in UTC with 24 hours, so times read the same on every machine
    private val times = TimeFormat(utc = true, hours = ClockHours.H24)

    // the widget's "Updated" time follows the phone's zone: fix it, and put it back for the other tests in this JVM
    private val zone = TimeZone.getDefault()

    @Before
    fun fixedZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restoreZone() = TimeZone.setDefault(zone)

    private fun shoot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalTimeFormat provides times) { RootiestTheme(dark = dark, content = content) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun list(state: StatusUiState) =
        StatusScreen(state, ServiceListMode.All, onRefresh = {}, onOpenService = { _, _ -> }, onSignIn = {}, onSignOut = {}, onOpenSettings = {})

    @Test fun listPublicDark() = shoot("list-public-dark") { list(StatusUiState(view = public)) }

    @Test fun listPublicLight() = shoot("list-public-light", dark = false) { list(StatusUiState(view = public)) }

    @Test fun listStaffDark() = shoot("list-staff-dark") { list(StatusUiState(view = staff, signedInAs = "Chris", viewFor = "Chris")) }

    @Test fun listStaffLight() = shoot("list-staff-light", dark = false) { list(StatusUiState(view = staff, signedInAs = "Chris", viewFor = "Chris")) }

    @Test fun serviceDetail() = shoot("service-notes") {
        ServiceScreen(StatusUiState(view = public), "Personal", "Notes", muted = false, onMute = {}, onRefresh = {}, onBack = {})
    }

    @Test fun settings() = shoot("settings") {
        SettingsScreen(
            Appearance.System, onAppearance = {}, serviceList = ServiceListMode.All, onServiceList = {},
            timeFormat = times, onTimeFormat = {}, mutedCount = 0, notifyAfter = NotifyAfter.Five, onNotifyAfter = {},
            onOpenWatchList = {}, refreshInterval = RefreshInterval.Every15, onRefreshInterval = {}, onOpenLicenses = {}, onBack = {},
        )
    }

    private val supportApi = dev.rootiest.status.data.SupportApi({ null })

    @Test fun supportTickets() = shoot("support-tickets") {
        val tickets = supportApi.parseList(javaClass.getResource("/support-tickets.json")!!.readText()).Tickets
        SupportScreen(dev.rootiest.status.SupportUiState(signedIn = true, tickets = tickets), onSignIn = {}, onRefresh = {}, onOpen = {}, onNew = {}, onBack = {})
    }

    @Test fun supportThread() = shoot("support-thread") {
        val thread = supportApi.parseThread(javaClass.getResource("/support-thread.json")!!.readText())
        TicketScreen(dev.rootiest.status.SupportUiState(signedIn = true, thread = thread), 1, onRefresh = {}, onLink = {}, onReply = {}, onClose = {}, onBack = {})
    }

    @Test fun supportNewTicket() = shoot("support-new-ticket") {
        val form = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<dev.rootiest.status.data.TicketForm>(javaClass.getResource("/support-form.json")!!.readText())
        NewTicketScreen(
            dev.rootiest.status.SupportUiState(signedIn = true, form = form),
            dev.rootiest.status.data.reportProblemTicket("1.7.0 (10700)", "16 (API 36)", "Google Pixel 9 Pro"),
            onSignIn = {}, onSend = {}, onBack = {},
        )
    }

    // The widget, drawn by Glance into the RemoteViews a launcher would show, from a cached snapshot (no network).
    private fun widget(name: String, scope: WidgetScope, size: DpSize) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        StatusCache(context).save(staff)
        val id = 42
        WidgetStore(context).setScope(id, scope)
        val views = runBlocking { StatusWidget().compose(context, AppWidgetId(id), size = size) }
        val host = FrameLayout(context)
        val density = context.resources.displayMetrics.density
        val view = views.apply(context, host)
        host.addView(view, FrameLayout.LayoutParams((size.width.value * density).toInt(), (size.height.value * density).toInt()))
        host.measure(0, 0)
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        // drawn to a bitmap: the view has no activity to capture it from
        host.drawToBitmap().captureRoboImage("src/test/screenshots/$name.png")
    }

    private val large = DpSize(250.dp, 180.dp)

    @Test fun widgetAll() = widget("widget-all", WidgetScope.All, large)

    @Test fun widgetServers() = widget("widget-servers", WidgetScope.Servers, large)

    @Test fun widgetGroup() = widget("widget-group-code", WidgetScope.Group("Code"), large)

    @Test fun widgetSmall() = widget("widget-small", WidgetScope.All, DpSize(180.dp, 60.dp))
}
