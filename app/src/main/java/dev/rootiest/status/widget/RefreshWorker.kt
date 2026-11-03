package dev.rootiest.status.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.rootiest.status.Notifier
import dev.rootiest.status.data.AuthStore
import dev.rootiest.status.data.SettingsStore
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.changesSince
import dev.rootiest.status.data.stateMap
import dev.rootiest.status.data.unmuted
import dev.rootiest.status.pushAddress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

// The background job: fetch the status, notify what changed since the last fetch, redraw the widgets, and renew the push address.
// It runs every 15 minutes and at once when a push arrives.
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // one run at a time: two runs comparing against the same saved states would notify a change twice
    override suspend fun doWork(): Result = running.withLock {
        val cache = StatusCache(applicationContext)
        val api = StatusApi(AuthStore(applicationContext)::credentials)
        // a failed fetch skips this run; the next one compares against the last states seen
        runCatching { api.fetch() }.getOrNull()?.let { view ->
            Notifier.notify(applicationContext, view.changesSince(cache.loadStates()).unmuted(SettingsStore(applicationContext).muted))
            cache.saveStates(view.stateMap())
            cache.save(view)
        }
        StatusWidget().updateAll(applicationContext)
        // renewed on every run, so the host serving the page learns it soon after a failover (each host keeps its own list)
        pushAddress(applicationContext)?.let { address ->
            runCatching { api.registerPush(address) }.onFailure { Log.w("StatusPush", "${it.message} for $address") }
        }
        Result.success()
    }

    companion object {
        private const val NAME = "status-widget-refresh"
        private const val NOW = "status-refresh-now"
        private val running = Mutex()

        // A run as soon as there is a network: after a push, or a new push address. Expedited where WorkManager can do that without a
        // foreground notification (Android 12+), so an idle app is not left waiting.
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        // Called on every app start and when a widget is added; KEEP leaves a scheduled job alone.
        fun schedule(context: Context) {
            // 15 minutes is the shortest period WorkManager allows.
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
