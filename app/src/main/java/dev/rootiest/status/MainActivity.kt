package dev.rootiest.status

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rootiest.status.data.Appearance
import dev.rootiest.status.ui.RootiestApp
import dev.rootiest.status.ui.theme.RootiestTheme
import dev.rootiest.status.widget.RefreshWorker
import org.unifiedpush.android.connector.UnifiedPush

class MainActivity : ComponentActivity() {

    private val vm: StatusViewModel by viewModels()
    private val support: SupportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.schedule(this)
        Notifier.createChannels(this)
        // push through the phone's UnifiedPush distributor (the ntfy app), if it has one; without it the periodic refresh still runs
        UnifiedPush.tryUseCurrentOrDefaultDistributor(this) { ok -> if (ok) UnifiedPush.register(this) }
        if (savedInstanceState == null) handleSignIn(intent)
        setContent {
            val appearance by vm.appearance.collectAsStateWithLifecycle()
            val dark = when (appearance) {
                Appearance.System -> isSystemInDarkTheme()
                Appearance.Light -> false
                Appearance.Dark -> true
            }
            LaunchedEffect(appearance) { applyNightMode(appearance) }
            // the bars' icons follow the app's choice, not the phone's
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            RootiestTheme(dark) {
                RootiestApp(vm, support)
            }
        }
    }

    // singleTop: the browser's return to dev.rootiest.status:/signed-in reaches the running activity here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSignIn(intent)
    }

    // Android 12+ also keeps the choice for the window shown before Compose draws (values-night), across restarts.
    // Older versions have no per-app setting, so that window follows the phone there.
    private fun applyNightMode(appearance: Appearance) {
        if (Build.VERSION.SDK_INT < 31) return
        getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (appearance) {
                Appearance.System -> UiModeManager.MODE_NIGHT_AUTO
                Appearance.Light -> UiModeManager.MODE_NIGHT_NO
                Appearance.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    private fun handleSignIn(intent: Intent) {
        // a support reply's notification: open that ticket
        intent.getLongExtra(EXTRA_TICKET, 0).takeIf { it > 0 }?.let {
            intent.removeExtra(EXTRA_TICKET) // once, not again after a rotation
            support.requestOpen(it)
        }
        val uri = intent.data ?: return
        if (uri.scheme != "dev.rootiest.status") return
        when (uri.path) {
            "/signed-in" -> uri.getQueryParameter("token")?.let(vm::completeSignIn)
            "/support-signed-in" -> uri.getQueryParameter("token")?.let(support::completeSignIn) // the support desk's own sign-in
        }
    }

    companion object {
        const val EXTRA_TICKET = "dev.rootiest.status.TICKET" // a support ticket to open (Notifier.ticket)

        // the scrims enableEdgeToEdge puts behind three-button navigation by default
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
