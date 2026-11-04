package dev.rootiest.status.data

import kotlinx.serialization.json.Json

// How the phone app hands each snapshot to the watch app: one Wearable Data Layer item at PATH holding the snapshot as JSON
// under KEY. The phone replaces it after each good fetch; the watch saves it in its own StatusCache and redraws its tile.
object WatchSnapshot {
    const val PATH = "/status"
    const val KEY = "view"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(view: StatusView): String = json.encodeToString(view)

    fun decode(text: String): StatusView? = runCatching { json.decodeFromString<StatusView>(text) }.getOrNull()
}

// What the watch's tile shows: the Everything widget's level and headline, and its first few rows of what is wrong.
data class WatchSummary(val level: String, val headline: String, val rows: List<String>, val more: Int) {
    // the lines under the headline: the rows and how many more the list has, or where the rest is when all is well
    fun detail(): String = when {
        rows.isEmpty() -> "Tap for every service"
        more > 0 -> (rows + "and $more more").joinToString("\n")
        else -> rows.joinToString("\n")
    }
}

fun StatusView.watchSummary(maxRows: Int = 3): WatchSummary {
    val content = widgetContent(WidgetScope.All)
    return WatchSummary(content.level, content.headline, content.rows.take(maxRows).map { it.text }, (content.rows.size - maxRows).coerceAtLeast(0))
}
