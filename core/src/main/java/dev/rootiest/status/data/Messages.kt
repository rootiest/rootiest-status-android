package dev.rootiest.status.data

import java.time.Duration
import java.time.Instant

// Messages someone wrote for the page's Recent events list (rootiest-hub's docs/README.md). The hub sends each viewer only the ones they may read.

private const val SHOWN = 5
private val SHOWN_FOR: Duration = Duration.ofDays(7)
private val NOTIFIED_WITHIN: Duration = Duration.ofDays(1)

private fun Event.at(): Instant? = runCatching { Instant.parse(At) }.getOrNull()

// Every message in the snapshot, newest first (the API already sends them newest first).
fun StatusView.messages(): List<Event> = Events.orEmpty().filter { it.Kind == "message" && it.Ref.isNotEmpty() }

// The messages the list shows: the newest few of the last week, counted from the snapshot so a saved one reads as it did when fetched.
fun StatusView.recentMessages(): List<Event> {
    val now = runCatching { Instant.parse(Now) }.getOrNull() ?: return emptyList()
    return messages().filter { m -> m.at()?.let { Duration.between(it, now) <= SHOWN_FOR } == true }.take(SHOWN)
}

// What the background job remembers having seen, so each message is notified once.
fun StatusView.messageRefs(): Set<String> = messages().map { it.Ref }.toSet()

// The messages the last run saw that are no longer here: withdrawn, or too old for the hub to send, so a notification still open for one should go.
fun StatusView.goneMessages(seen: Set<String>?): Set<String> = seen.orEmpty() - messageRefs()

// The messages to post a notification for: posted to be notified, not seen by the last run, and recent, so that a phone back after a long time away
// or an account that has just signed in is not told about old news. Nothing on the first run (no [seen] yet).
fun StatusView.newNotices(seen: Set<String>?, now: Instant): List<Event> {
    if (seen == null) return emptyList()
    return messages().filter { m -> m.Notify && m.Ref !in seen && m.at()?.let { Duration.between(it, now) <= NOTIFIED_WITHIN } == true }
}
