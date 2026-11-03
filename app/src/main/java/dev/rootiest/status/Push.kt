package dev.rootiest.status

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dev.rootiest.status.widget.RefreshWorker
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

// Push through UnifiedPush, with the ntfy app as distributor: rootiest-hub posts "refresh" to this phone's address after each alert
// (its docs/README.md, "Waking the app"), and the app then runs the refresh job at once instead of at its next 15 minutes.
class StatusPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        savePushAddress(this, endpoint.url)
        RefreshWorker.runNow(this) // sends the address to the hub
    }

    // The message says nothing; it only means the status changed.
    override fun onMessage(message: PushMessage, instance: String) {
        prefs(this).edit { putLong("last-push", System.currentTimeMillis()) }
        RefreshWorker.runNow(this)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w("StatusPush", "registration failed: $reason")
        savePushAddress(this, null)
    }

    override fun onUnregistered(instance: String) {
        savePushAddress(this, null)
    }
}

private fun prefs(context: Context) = context.getSharedPreferences("push", Context.MODE_PRIVATE)

fun pushAddress(context: Context): String? = prefs(context).getString("address", null)

fun savePushAddress(context: Context, address: String?) {
    prefs(context).edit { if (address == null) remove("address") else putString("address", address) }
}

// When the last push arrived (epoch millis), or null before the first.
fun lastPushAt(context: Context): Long? = prefs(context).getLong("last-push", 0).takeIf { it > 0 }

// Where push stands, for the settings screen.
sealed interface PushStatus {
    data object NoDistributor : PushStatus // no ntfy (or other distributor) on the phone
    data class NotRegistered(val distributor: String) : PushStatus // installed, but no address yet or it refused
    data class On(val distributor: String, val lastPushAt: Long?) : PushStatus
}

fun pushStatus(context: Context): PushStatus {
    val pm = context.packageManager
    // the distributor's app name ("ntfy"), or its package name if it has none
    fun label(pkg: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    val distributor = UnifiedPush.getAckDistributor(context) ?: UnifiedPush.getDistributors(context).firstOrNull()
        ?: return PushStatus.NoDistributor
    return if (pushAddress(context) == null) PushStatus.NotRegistered(label(distributor))
    else PushStatus.On(label(distributor), lastPushAt(context))
}
