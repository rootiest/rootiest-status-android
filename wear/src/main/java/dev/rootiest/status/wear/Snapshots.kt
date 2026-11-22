package dev.rootiest.status.wear

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.WatchShown
import dev.rootiest.status.data.WatchSnapshot
import dev.rootiest.status.data.watchShown
import java.io.File
import java.time.Instant
import kotlinx.coroutines.tasks.await

// The watch's copies: the phone's last snapshot, and the public status the watch fetched itself while the phone's was stale
// (WatchFetchWorker). SnapshotListener saves each phone snapshot as it arrives; before the first arrives (a new install), the
// Data Layer item the phone left is read once. Which of the two is shown is watchShown in data/Watch.kt.
object Snapshots {
    // what the tile and the complication show
    suspend fun latest(context: Context): StatusView? {
        WatchFetchWorker.schedule(context)
        return watchShown(phone(context), fetchedCache(context).load(), Instant.now())?.view
    }

    // what the list and a service's screen show, with where it came from; only what is saved, as they read it on the main thread
    fun saved(context: Context, now: Instant): WatchShown? = watchShown(StatusCache(context).load(), fetchedCache(context).load(), now)

    // the phone's snapshot, read from the Data Layer when none was saved yet
    suspend fun phone(context: Context): StatusView? = StatusCache(context).load() ?: fromDataLayer(context)

    // a snapshot from the phone: it replaces the watch's own fetch, which only stood in while the phone's was stale
    fun save(context: Context, view: StatusView) {
        StatusCache(context).save(view)
        File(fetchedDir(context), "last-status.json").delete()
        redraw(context)
    }

    // the public status the watch fetched itself
    fun saveFetched(context: Context, view: StatusView) {
        fetchedCache(context).save(view)
        redraw(context)
    }

    private fun redraw(context: Context) {
        TileService.getUpdater(context).requestUpdate(StatusTile::class.java)
        StatusComplication.update(context)
    }

    // its own directory, as StatusCache names its file the same everywhere
    private fun fetchedDir(context: Context) = File(context.filesDir, "watch-fetch").apply { mkdirs() }

    private fun fetchedCache(context: Context) = StatusCache(fetchedDir(context))

    private suspend fun fromDataLayer(context: Context): StatusView? = runCatching {
        Wearable.getDataClient(context).dataItems.await().use { items ->
            items.firstOrNull { it.uri.path == WatchSnapshot.PATH }
                ?.let { DataMapItem.fromDataItem(it).dataMap.getString(WatchSnapshot.KEY) }
                ?.let(WatchSnapshot::decode)
        }
    }.onFailure { Log.w("Snapshots", "no snapshot from the phone: ${it.message}") }.getOrNull()?.also { StatusCache(context).save(it) }
}
