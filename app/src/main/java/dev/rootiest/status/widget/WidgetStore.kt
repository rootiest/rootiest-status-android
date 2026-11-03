package dev.rootiest.status.widget

import android.content.Context
import androidx.core.content.edit
import dev.rootiest.status.data.WidgetScope

// Each widget's own choice of what it shows, by app widget id.
class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun scope(widgetId: Int): WidgetScope = WidgetScope.of(prefs.getString("scope-$widgetId", null))

    fun setScope(widgetId: Int, scope: WidgetScope) = prefs.edit { putString("scope-$widgetId", scope.key) }

    fun forget(widgetIds: IntArray) = prefs.edit { widgetIds.forEach { remove("scope-$it") } }
}
