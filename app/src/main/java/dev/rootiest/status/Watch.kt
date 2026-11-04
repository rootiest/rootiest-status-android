package dev.rootiest.status

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.rootiest.status.data.StatusView
import dev.rootiest.status.data.WatchSnapshot
import kotlinx.coroutines.tasks.await

// Hands a new snapshot to the watch app over the Wearable Data Layer (rootiest-status's :wear). With no watch the item just stays
// on the phone; with no Google Play services there is no Data Layer and nothing is sent.
object Watch {
    suspend fun send(context: Context, view: StatusView) {
        val request = PutDataMapRequest.create(WatchSnapshot.PATH)
            .apply { dataMap.putString(WatchSnapshot.KEY, WatchSnapshot.encode(view)) }
            .asPutDataRequest()
            .setUrgent() // a change of status should reach the watch now, not at the next sync
        runCatching { Wearable.getDataClient(context).putDataItem(request).await() }
            .onFailure { Log.w("Watch", "snapshot not sent: ${it.message}") }
    }
}
