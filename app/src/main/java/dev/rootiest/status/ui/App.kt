package dev.rootiest.status.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.rootiest.status.Home
import dev.rootiest.status.ServiceDetail
import dev.rootiest.status.StatusViewModel

// One ViewModel for the whole app, above the navigation: every screen shows the same snapshot.
@Composable
fun RootiestApp(vm: StatusViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Home)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> {
                StatusScreen(state, onRefresh = vm::refresh, onOpenService = { g, s -> backStack.add(ServiceDetail(g, s)) })
            }
            entry<ServiceDetail> { key ->
                ServiceScreen(state, key.group, key.name, onRefresh = vm::refresh, onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
