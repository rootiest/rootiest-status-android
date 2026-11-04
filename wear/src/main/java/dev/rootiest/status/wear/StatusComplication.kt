package dev.rootiest.status.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.rootiest.status.data.WatchComplication
import dev.rootiest.status.data.watchComplication

// The watch-face complication: the level's icon with "OK" or how many are down, the services up out of all of them as a gauge,
// or the icon alone. It never polls: Snapshots.save asks for an update when the phone sends a new snapshot. A tap opens the list.
class StatusComplication : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData =
        Snapshots.latest(this)?.watchComplication()?.let { data(it, request.complicationType, open()) } ?: NoDataComplicationData()

    // what the watch face editor shows before a snapshot exists
    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(WatchComplication("ok", "OK", 24, 25), type, null)

    private fun open() = PendingIntent.getActivity(this, 0, Intent(this, StatusActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun data(c: WatchComplication, type: ComplicationType, tap: PendingIntent?): ComplicationData? {
        val icon = Icon.createWithResource(this, iconFor(c.level))
        val said = PlainComplicationText.Builder("Rootiest Status: ${c.text}, ${c.up} of ${c.total} services up").build()
        val mono = MonochromaticImage.Builder(icon).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder(c.text).build(), said)
                .setMonochromaticImage(mono).setTapAction(tap).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(c.up.toFloat(), 0f, c.total.coerceAtLeast(1).toFloat(), said)
                .setText(PlainComplicationText.Builder("${c.up}/${c.total}").build())
                .setMonochromaticImage(mono).setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(SmallImage.Builder(icon, SmallImageType.ICON).build(), said)
                .setTapAction(tap).build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(mono, said).setTapAction(tap).build()
            else -> null
        }
    }

    companion object {
        fun iconFor(level: String): Int = when (level) {
            "ok" -> R.drawable.ic_tile_ok
            "warn" -> R.drawable.ic_tile_warn
            "bad" -> R.drawable.ic_tile_bad
            else -> R.drawable.ic_stat_status
        }

        // a new snapshot: every watch face showing this complication asks for it again
        fun update(context: Context) =
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, StatusComplication::class.java)).requestUpdateAll()
    }
}
