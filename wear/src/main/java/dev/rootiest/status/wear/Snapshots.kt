package dev.rootiest.status.wear

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.WatchSnapshot
import kotlinx.coroutines.tasks.await

// The watch's copy of the phone's last snapshot. SnapshotListener saves each one as it arrives; before the first arrives (a new
// install), the Data Layer item the phone left is read once.
object Snapshots {
    suspend fun latest(context: Context): StatusView? = StatusCache(context).load() ?: fromDataLayer(context)

    fun save(context: Context, view: StatusView) {
        StatusCache(context).save(view)
        TileService.getUpdater(context).requestUpdate(StatusTile::class.java)
        StatusComplication.update(context)
    }

    private suspend fun fromDataLayer(context: Context): StatusView? = runCatching {
        Wearable.getDataClient(context).dataItems.await().use { items ->
            items.firstOrNull { it.uri.path == WatchSnapshot.PATH }
                ?.let { DataMapItem.fromDataItem(it).dataMap.getString(WatchSnapshot.KEY) }
                ?.let(WatchSnapshot::decode)
        }
    }.onFailure { Log.w("Snapshots", "no snapshot from the phone: ${it.message}") }.getOrNull()?.also { StatusCache(context).save(it) }
}
