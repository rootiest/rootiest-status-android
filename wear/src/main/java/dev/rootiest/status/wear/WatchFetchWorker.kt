package dev.rootiest.status.wear

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.rootiest.status.data.StatusApi
import dev.rootiest.status.data.phoneSnapshotStale
import java.time.Instant
import java.util.concurrent.TimeUnit

// The watch's own check, every 15 minutes (WorkManager's shortest period): when the phone's snapshot is older than
// WATCH_STALE_AFTER (out of Bluetooth range, the phone off, no Google Play services), fetch the public status, signed out, and
// show it until the phone's next snapshot. While the phone's is fresh it does nothing, so a phone in range costs no request.
class WatchFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!phoneSnapshotStale(Snapshots.phone(applicationContext), Instant.now())) return Result.success()
        // a failed fetch waits for the next period rather than retrying sooner, to spare the battery
        runCatching { StatusApi().fetch() }
            .onSuccess { Snapshots.saveFetched(applicationContext, it) }
            .onFailure { Log.w("WatchFetch", "public status: ${it.message}") }
        return Result.success()
    }

    companion object {
        private const val NAME = "watch-fetch"

        // kept when already scheduled, so it is cheap to call from each tile and complication request
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchFetchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
