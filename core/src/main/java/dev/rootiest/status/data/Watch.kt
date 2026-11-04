package dev.rootiest.status.data

import kotlinx.serialization.json.Json

// How the phone app hands each snapshot to the watch app: one Wearable Data Layer item at PATH holding the snapshot as JSON
// under KEY. The phone replaces it after each good fetch; the watch saves it in its own StatusCache and redraws its tile.
// A Data Layer item holds at most 100 KB and a signed-in snapshot is larger (mostly each service's 90-day uptime bar), so only
// what the watch shows is sent: the level, headline, and each server's and service's name and state.
object WatchSnapshot {
    const val PATH = "/status"
    const val KEY = "view"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(view: StatusView): String = json.encodeToString(view.forWatch())

    private fun StatusView.forWatch() = copy(
        Events = null,
        Hosts = Hosts?.map { it.copy(Note = "", Vitals = "") },
        Groups = Groups.map { g -> g.copy(Services = g.Services.map { it.copy(Bar = emptyList(), Detail = "", Backup = null, Uptime = "") }) },
    )

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

// What a watch-face complication shows: "OK" or how many are down beside the level's icon, and the services up out of all of them
// for a ranged (gauge) slot.
data class WatchComplication(val level: String, val text: String, val up: Int, val total: Int)

fun StatusView.watchComplication(): WatchComplication {
    val problems = problems().size
    val services = Groups.flatMap { it.Services }
    return WatchComplication(
        level = Level,
        text = if (problems == 0) "OK" else "$problems down",
        up = services.count { !it.isProblem },
        total = services.size,
    )
}
