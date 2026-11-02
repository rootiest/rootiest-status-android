package dev.rootiest.status.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.rootiest.status.Notifier
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.changesSince
import dev.rootiest.status.data.stateMap
import java.util.concurrent.TimeUnit

// The background job: fetch the status, notify what changed since the last fetch, and redraw the widgets.
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cache = StatusCache(applicationContext)
        // a failed fetch skips this run; the next one compares against the last states seen
        runCatching { StatusApi().fetch() }.getOrNull()?.let { view ->
            Notifier.notify(applicationContext, view.changesSince(cache.loadStates()))
            cache.saveStates(view.stateMap())
            cache.save(view)
        }
        StatusWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "status-widget-refresh"

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
