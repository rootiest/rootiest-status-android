package dev.rootiest.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.rootiest.status.data.SettingsStore

// A notification's Mute button: adds the name to the muted ones (as its switch in Settings does) and clears the
// notification. Settings and the service screen turn it back on.
class MuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val settings = SettingsStore(context)
        settings.muted += name
        NotificationManagerCompat.from(context).cancel(Notifier.idFor(name))
    }

    companion object {
        const val EXTRA_NAME = "name"
    }
}
