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
import dev.rootiest.status.data.began
import dev.rootiest.status.data.changesSince
import dev.rootiest.status.data.damp
import dev.rootiest.status.data.nextDue
import dev.rootiest.status.data.stateMap
import dev.rootiest.status.data.unmuted
import dev.rootiest.status.pushAddress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

// The background job: fetch the status, notify what changed since the last fetch (outages once they have lasted the
// Settings delay, see damp), redraw the widgets, and renew the push address.
// It runs every 15 minutes (or the interval set in Settings, or never: push only) and at once when a push arrives.
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // one run at a time: two runs comparing against the same saved states would notify a change twice
    override suspend fun doWork(): Result = running.withLock {
        val cache = StatusCache(applicationContext)
        val api = StatusApi(AuthStore(applicationContext)::credentials)
        // a failed fetch skips this run; the next one compares against the last states seen, and a waiting outage is
        // checked again in a minute rather than at the next interval
        var due: Instant? = cache.loadPending().takeIf { it.isNotEmpty() }?.let { Instant.now().plusSeconds(60) }
        runCatching { api.fetch() }.getOrNull()?.let { view ->
            val settings = SettingsStore(applicationContext)
            val hold = Duration.ofMinutes(settings.notifyAfter.minutes)
            val states = view.stateMap()
            val damped = damp(view.changesSince(cache.loadStates()), states, cache.loadPending(), view::began, Instant.now(), hold)
            Notifier.notify(applicationContext, damped.notify.unmuted(settings.muted))
            cache.saveStates(states)
            cache.savePending(damped.pending)
            cache.save(view)
            due = damped.pending.nextDue(hold)
        }
        StatusWidget().updateAll(applicationContext)
        // renewed on every run, so the host serving the page learns it soon after a failover (each host keeps its own list)
        pushAddress(applicationContext)?.let { address ->
            runCatching { api.registerPush(address) }.onFailure { Log.w("StatusPush", "${it.message} for $address") }
        }
        // last: when this run is the recheck, replacing it cancels it
        due?.let { recheckAt(applicationContext, it) }
        Result.success()
    }

    companion object {
        private const val NAME = "status-widget-refresh"
        private const val NOW = "status-refresh-now"
        private const val RECHECK = "status-recheck"
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

        // A run when the next waiting outage is due, so it is notified on time whatever the interval (push only too). One left
        // over after the outage recovered is only an extra fetch, so it is never cancelled (which would also cancel a run from inside).
        private fun recheckAt(context: Context, due: Instant) {
            val delay = Duration.between(Instant.now(), due).coerceAtLeast(Duration.ZERO) + Duration.ofSeconds(5)
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delay)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(RECHECK, ExistingWorkPolicy.REPLACE, request)
        }

        // Called on every app start, when a widget is added and when the interval setting changes. UPDATE changes the period
        // of a scheduled job without restarting its clock; push only cancels it (pushes still run the job through runNow).
        fun schedule(context: Context) {
            val minutes = SettingsStore(context).refreshInterval.minutes
            val work = WorkManager.getInstance(context)
            if (minutes == null) {
                work.cancelUniqueWork(NAME)
                return
            }
            // 15 minutes is the shortest period WorkManager allows.
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
