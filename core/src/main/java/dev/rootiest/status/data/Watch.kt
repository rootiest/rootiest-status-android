package dev.rootiest.status.data

import java.time.Instant
import kotlinx.serialization.json.Json

// How the phone app hands each snapshot to the watch app: one Wearable Data Layer item at PATH holding the snapshot as JSON
// under KEY. The phone replaces it after each good fetch; the watch saves it in its own StatusCache and redraws its tile.
// A Data Layer item holds at most 100 KB and a signed-in snapshot is larger (mostly each service's 90-day uptime bar), so only
// what the watch shows is sent: no bars, no server notes or vitals, and of the events only the recent messages and the services' changes.
object WatchSnapshot {
    const val PATH = "/status"
    const val KEY = "view"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(view: StatusView): String = json.encodeToString(view.forWatch())

    private fun StatusView.forWatch(): StatusView {
        val messages = recentMessages().toSet()
        return copy(
            Events = Events?.filter { it in messages || it.Kind == "service" },
            Hosts = Hosts?.map { it.copy(Note = "", Vitals = "") },
            Groups = Groups.map { g -> g.copy(Services = g.Services.map { it.copy(Bar = emptyList()) }) },
        )
    }

    fun decode(text: String): StatusView? = runCatching { json.decodeFromString<StatusView>(text) }.getOrNull()
}

// What the watch's tile shows: the Everything widget's level and headline, its first few rows of what is wrong, and the recent
// messages: the newest in full when all is well, how many there are while something is wrong.
data class WatchSummary(val level: String, val headline: String, val rows: List<String>, val more: Int, val latest: String? = null, val messages: Int = 0) {
    // the lines under the headline: the rows and how many more the list has, or the message or where the rest is when all is well
    fun detail(): String {
        if (rows.isEmpty()) return latest ?: "Tap for every service"
        val more = "and $more more".takeIf { more > 0 }
        val messages = when (messages) {
            0 -> null
            1 -> "1 message"
            else -> "$messages messages"
        }
        return (rows + listOfNotNull(more, messages)).joinToString("\n")
    }
}

fun StatusView.watchSummary(maxRows: Int = 3): WatchSummary {
    val content = widgetContent(WidgetScope.All)
    val messages = recentMessages()
    // the count of messages takes one of the tile's few lines
    val shown = content.rows.take(if (messages.isEmpty()) maxRows else maxRows - 1).map { it.text }
    return WatchSummary(
        content.level,
        content.headline,
        shown,
        (content.rows.size - shown.size).coerceAtLeast(0),
        messages.firstOrNull()?.let { "${it.Subject}: ${it.Detail}" },
        messages.size,
    )
}

// How old the watch's copy is ("Updated 5 min ago"), from the time in the snapshot. Null when that does not parse.
fun StatusView.updatedAgo(now: Instant): String? =
    runCatching { Instant.parse(Now) }.getOrNull()?.let { "Updated " + TimeFormat(style = TimeStyle.Relative).format(it, now) }

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
