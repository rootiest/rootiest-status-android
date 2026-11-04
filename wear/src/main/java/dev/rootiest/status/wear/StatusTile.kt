package dev.rootiest.status.wear

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import dev.rootiest.status.data.WatchSummary
import dev.rootiest.status.data.watchSummary

// The watch tile: the overall headline in its level's colour and the first few problems. A tap opens the full list
// (StatusActivity). It is redrawn when a snapshot arrives from the phone (Snapshots.save), so it never fetches anything.
class StatusTile : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) = SuspendToFutureAdapter.launchFuture {
        val summary = Snapshots.latest(this@StatusTile)?.watchSummary()
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(this@StatusTile, requestParams.deviceConfiguration, summary)))
            .build()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) = SuspendToFutureAdapter.launchFuture {
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build()
    }

    companion object {
        private const val RESOURCES = "1"
    }
}

internal fun layout(context: Context, device: DeviceParameters, summary: WatchSummary?): LayoutElement =
    materialScope(context, device) {
        val open = clickable(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(StatusActivity::class.java.name)
                        .build(),
                )
                .build(),
            "open",
        )
        primaryLayout(
            titleSlot = { text(LayoutString("Rootiest Status")) },
            mainSlot = {
                titleCard(
                    onClick = open,
                    title = {
                        text(
                            LayoutString(summary?.headline ?: "Open Rootiest Status on the phone"),
                            maxLines = 2,
                            color = LayoutColor(levelColor(summary?.level)),
                        )
                    },
                    content = {
                        text(LayoutString(summary?.detail() ?: "The watch shows what the phone last fetched."), maxLines = 4, typography = Typography.BODY_SMALL)
                    },
                )
            },
        )
    }

// Catppuccin Mocha, as on the phone
internal fun levelColor(level: String?): Int = when (level) {
    "ok" -> 0xFFA6E3A1.toInt()
    "warn" -> 0xFFF9E2AF.toInt()
    "bad" -> 0xFFF38BA8.toInt()
    else -> 0xFFA6ADC8.toInt()
}

