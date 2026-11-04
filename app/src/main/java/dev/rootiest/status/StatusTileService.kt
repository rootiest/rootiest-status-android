package dev.rootiest.status

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.rootiest.status.data.StatusCache
import dev.rootiest.status.data.tileContent

// The Quick Settings tile: the overall status from the last saved snapshot (the widget's), so opening the shade never waits on the
// network. Unavailable until the first good fetch. A tap opens the app; a long-press opens its Settings (MainActivity, QS_TILE_PREFERENCES).
class StatusTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val content = StatusCache(this).load()?.tileContent()
        tile.label = getString(R.string.app_name)
        tile.subtitle = content?.subtitle
        tile.state = when {
            content == null -> Tile.STATE_UNAVAILABLE
            content.active -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, iconFor(content?.level))
        tile.updateTile()
    }

    override fun onClick() {
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
        } else {
            // before Android 14 only the Intent form exists; the check above keeps it off 14+, where it throws
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(open)
        }
    }

    companion object {
        // A new snapshot was saved: redraw the tile if it is showing (it reads the snapshot again each time the shade opens anyway).
        fun update(context: Context) = requestListeningState(context, ComponentName(context, StatusTileService::class.java))

        fun iconFor(level: String?): Int = when (level) {
            "ok" -> R.drawable.ic_tile_ok
            "warn" -> R.drawable.ic_tile_warn
            "bad" -> R.drawable.ic_tile_bad
            else -> R.drawable.ic_stat_status
        }
    }
}
