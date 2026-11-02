package dev.rootiest.status

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.rootiest.status.ui.RootiestApp
import dev.rootiest.status.ui.theme.RootiestTheme
import dev.rootiest.status.widget.RefreshWorker

class MainActivity : ComponentActivity() {
    private val vm: StatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.schedule(this)
        if (savedInstanceState == null) handleSignIn(intent)
        setContent {
            RootiestTheme {
                RootiestApp(vm)
            }
        }
    }

    // singleTop: the browser's return to dev.rootiest.status:/signed-in reaches the running activity here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSignIn(intent)
    }

    private fun handleSignIn(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "dev.rootiest.status" && uri.path == "/signed-in") {
            uri.getQueryParameter("token")?.let(vm::completeSignIn)
        }
    }
}
