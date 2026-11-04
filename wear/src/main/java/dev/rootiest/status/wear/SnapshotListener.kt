package dev.rootiest.status.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import dev.rootiest.status.data.WatchSnapshot

// The phone sent a new snapshot (its background job or its own refresh): keep it and redraw the tile.
class SnapshotListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchSnapshot.PATH }
            .mapNotNull { DataMapItem.fromDataItem(it.dataItem).dataMap.getString(WatchSnapshot.KEY)?.let(WatchSnapshot::decode) }
            .lastOrNull()
            ?.let { Snapshots.save(this, it) }
    }
}
