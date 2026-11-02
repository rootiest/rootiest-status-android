package dev.rootiest.status

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.rootiest.status.ui.RootiestApp
import dev.rootiest.status.ui.theme.RootiestTheme
import dev.rootiest.status.widget.RefreshWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.schedule(this)
        setContent {
            RootiestTheme {
                RootiestApp()
            }
        }
    }
}
