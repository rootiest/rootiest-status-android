package dev.rootiest.status.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.rootiest.status.Home
import dev.rootiest.status.Licenses
import dev.rootiest.status.Notifier
import dev.rootiest.status.ServiceDetail
import dev.rootiest.status.Settings
import dev.rootiest.status.StatusViewModel
import dev.rootiest.status.BuildConfig
import dev.rootiest.status.NewSupportTicket
import dev.rootiest.status.SupportTicket
import dev.rootiest.status.data.NewTicket
import dev.rootiest.status.data.reportProblemTicket
import dev.rootiest.status.SupportTickets
import dev.rootiest.status.SupportViewModel
import dev.rootiest.status.WatchList

// One ViewModel for the whole app, above the navigation: every screen shows the same snapshot.
@Composable
fun RootiestApp(vm: StatusViewModel, support: SupportViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val supportState by support.state.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val refreshInterval by vm.refreshInterval.collectAsStateWithLifecycle()
    val serviceList by vm.serviceList.collectAsStateWithLifecycle()
    val notifyAfter by vm.notifyAfter.collectAsStateWithLifecycle()
    val timeFormat by vm.timeFormat.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Home)
    // Ask once for notifications (Android 13+); a refusal only means no change notifications.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // a support reply's notification was tapped: its ticket, over Support's list so back goes there
    LaunchedEffect(supportState.openRequest) {
        val id = supportState.openRequest ?: return@LaunchedEffect
        support.openHandled()
        openTicket(backStack, id)
    }
    // the launcher icon's Support shortcut
    LaunchedEffect(supportState.listRequest) {
        if (!supportState.listRequest) return@LaunchedEffect
        support.listHandled()
        openSupport(backStack)
    }
    // the Quick Settings tile was long-pressed
    val settingsRequest by vm.settingsRequest.collectAsStateWithLifecycle()
    LaunchedEffect(settingsRequest) {
        if (!settingsRequest) return@LaunchedEffect
        vm.settingsHandled()
        openSettings(backStack)
    }
    CompositionLocalProvider(LocalTimeFormat provides timeFormat) { NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Horizontal slides, as in other Android apps: Navigation 3 defaults to fades, and to shrinking the screen on a back gesture.
        transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } },
        popTransitionSpec = { slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it } },
        predictivePopTransitionSpec = { _ -> slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it } },
        entryProvider = entryProvider {
            entry<Home> {
                StatusScreen(
                    state,
                    listMode = serviceList,
                    onRefresh = vm::refresh,
                    onOpenService = { g, s -> backStack.add(ServiceDetail(g, s)) },
                    onSignIn = { CustomTabsIntent.Builder().build().launchUrl(context, vm.signInUrl().toUri()) },
                    onSignOut = { vm.signOut(); support.signOut() }, // both sign-ins: the status page's and the support desk's
                    onOpenSettings = { backStack.add(Settings) },
                    onOpenSupport = { backStack.add(SupportTickets) },
                )
            }
            entry<ServiceDetail> { key ->
                ServiceScreen(
                    state,
                    key.group,
                    key.name,
                    muted = key.name in muted,
                    onMute = { vm.setMuted(key.name, it) },
                    onRefresh = vm::refresh,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Settings> {
                SettingsScreen(
                    appearance,
                    onAppearance = vm::setAppearance,
                    serviceList = serviceList,
                    onServiceList = vm::setServiceList,
                    timeFormat = timeFormat,
                    onTimeFormat = vm::setTimeFormat,
                    mutedCount = muted.size,
                    notifyAfter = notifyAfter,
                    onNotifyAfter = vm::setNotifyAfter,
                    refreshInterval = refreshInterval,
                    onRefreshInterval = vm::setRefreshInterval,
                    onOpenWatchList = { backStack.add(WatchList) },
                    onOpenLicenses = { backStack.add(Licenses) },
                    onBack = { backStack.removeLastOrNull() },
                    onReportProblem = {
                        val t = reportProblemTicket(
                            app = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                            phone = "${Build.MANUFACTURER} ${Build.MODEL}",
                        )
                        backStack.add(NewSupportTicket(t.Category, t.Service, t.Subject, t.Body))
                    },
                )
            }
            entry<WatchList> {
                WatchListScreen(state.view, muted, onMute = vm::setMuted, onBack = { backStack.removeLastOrNull() })
            }
            entry<Licenses> { LicensesScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<SupportTickets> {
                val browse = { url: String -> CustomTabsIntent.Builder().build().launchUrl(context, url.toUri()) }
                // the first time Support opens without the desk's sign-in, start it; once per run, so backing out of it does not loop
                LaunchedEffect(supportState.signedIn) {
                    if (supportState.signedIn) support.refresh()
                    else if (!support.autoSignInTried) browse(support.signInUrl(auto = true))
                }
                SupportScreen(
                    supportState,
                    onSignIn = { browse(support.signInUrl()) },
                    onRefresh = support::refresh,
                    onOpen = { backStack.add(SupportTicket(it)) },
                    onNew = { backStack.add(NewSupportTicket()) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<NewSupportTicket> { key ->
                val browse = { url: String -> CustomTabsIntent.Builder().build().launchUrl(context, url.toUri()) }
                LaunchedEffect(supportState.signedIn) {
                    if (supportState.signedIn) support.loadForm()
                    else if (!support.autoSignInTried) browse(support.signInUrl(auto = true))
                }
                // once the desk has taken it, go to the new ticket in place of the form
                LaunchedEffect(supportState.created) {
                    supportState.created?.let { id ->
                        support.createdShown()
                        backStack.removeLastOrNull()
                        backStack.add(SupportTicket(id))
                    }
                }
                NewTicketScreen(
                    supportState,
                    prefill = NewTicket(Category = key.category, Service = key.service, Subject = key.subject, Body = key.body),
                    onSignIn = { browse(support.signInUrl()) },
                    onSend = support::create,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<SupportTicket> { key ->
                LaunchedEffect(key.id) { support.open(key.id) }
                TicketScreen(
                    supportState,
                    key.id,
                    onRefresh = { support.open(key.id) },
                    onLink = { CustomTabsIntent.Builder().build().launchUrl(context, it.toUri()) },
                    onReply = { support.reply(key.id, it) },
                    onClose = { support.close(key.id) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    ) }
}

// Opens Settings from the tile's long-press: over whatever is open, unless Settings is already on screen.
internal fun openSettings(backStack: MutableList<NavKey>) {
    if (backStack.lastOrNull() != Settings) backStack.add(Settings)
}

// Opens Support's list from the shortcut: back from it goes to the status list; nothing if it is already on screen.
internal fun openSupport(backStack: MutableList<NavKey>) {
    val at = backStack.indexOf(SupportTickets)
    if (at >= 0) repeat(backStack.size - at - 1) { backStack.removeAt(backStack.lastIndex) } else backStack.add(SupportTickets)
}

// Opens a ticket from its notification: over Support's list, so back goes there; nothing if it is already on screen.
internal fun openTicket(backStack: MutableList<NavKey>, id: Long) {
    if (backStack.lastOrNull() == SupportTicket(id)) return
    if (SupportTickets !in backStack) backStack.add(SupportTickets)
    backStack.add(SupportTicket(id))
}
