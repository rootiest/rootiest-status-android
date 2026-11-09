package dev.rootiest.status.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")

    private fun message(ref: String, at: String = "2026-11-09T11:00:00Z", notify: Boolean = false) =
        Event(At = at, Kind = "message", Subject = "Warning", Detail = "text $ref", Ref = ref, Severity = "warn", Notify = notify)

    private fun view(vararg events: Event) = StatusView(Now = now.toString(), Level = "ok", Headline = "All good", Events = events.toList())

    private val serviceEvent = Event(At = "2026-11-09T11:00:00Z", Kind = "service", Subject = "Gitea", From = "up", To = "down")

    @Test fun onlyMessagesAreMessages() {
        assertEquals(listOf("a"), view(serviceEvent, message("a"), message("")).messages().map { it.Ref })
    }

    @Test fun theListShowsTheNewestFewOfTheLastWeek() {
        val old = message("old", at = "2026-11-01T11:00:00Z")
        val many = (1..7).map { message("m$it") }.toTypedArray()
        assertEquals(listOf("m1", "m2", "m3", "m4", "m5"), view(*many, old).recentMessages().map { it.Ref })
        assertEquals(emptyList<Event>(), view(old).recentMessages())
    }

    @Test fun nothingIsNotifiedOnTheFirstRun() {
        assertEquals(emptyList<Event>(), view(message("a", notify = true)).newNotices(null, now))
    }

    @Test fun onlyAMessagePostedToBeNotifiedIsNotified() {
        val notices = view(message("quiet"), message("loud", notify = true)).newNotices(emptySet(), now)
        assertEquals(listOf("loud"), notices.map { it.Ref })
    }

    @Test fun aMessageIsNotifiedOnce() {
        val v = view(message("a", notify = true))
        assertEquals(emptyList<Event>(), v.newNotices(v.messageRefs(), now))
    }

    @Test fun aMessageNoLongerSentIsGone() {
        assertEquals(setOf("withdrawn"), view(message("kept")).goneMessages(setOf("kept", "withdrawn")))
    }

    @Test fun nothingIsGoneOnTheFirstRun() {
        assertEquals(emptySet<String>(), view(message("a")).goneMessages(null))
    }

    @Test fun oldNewsIsNotNotified() {
        val old = message("a", at = "2026-11-07T11:00:00Z", notify = true)
        assertEquals(emptyList<Event>(), view(old).newNotices(emptySet(), now))
    }
}
