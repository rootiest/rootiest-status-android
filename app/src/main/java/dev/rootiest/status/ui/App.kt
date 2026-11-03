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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.rootiest.status.Home
import dev.rootiest.status.Licenses
import dev.rootiest.status.Notifier
import dev.rootiest.status.ServiceDetail
import dev.rootiest.status.Settings
import dev.rootiest.status.StatusViewModel
import dev.rootiest.status.WatchList

// One ViewModel for the whole app, above the navigation: every screen shows the same snapshot.
@Composable
fun RootiestApp(vm: StatusViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
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
                    onSignOut = vm::signOut,
                    onOpenSettings = { backStack.add(Settings) },
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
                )
            }
            entry<WatchList> {
                WatchListScreen(state.view, muted, onMute = vm::setMuted, onBack = { backStack.removeLastOrNull() })
            }
            entry<Licenses> { LicensesScreen(onBack = { backStack.removeLastOrNull() }) }
        },
    ) }
}
