package dev.rootiest.status.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// Refreshing runs only while at least one widget is on a home screen.
class StatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancel(context)
    }
}
