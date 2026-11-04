package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DampingTest {
    private val t0 = Instant.parse("2026-11-03T12:00:00Z")
    private val five = Duration.ofMinutes(5)
    private fun at(minutes: Long) = t0.plusSeconds(minutes * 60)
    private val unknown: (String) -> Instant? = { null }

    @Test
    fun atOnceNotifiesEverything() {
        val d = damp(listOf(Change("A", "up", "down")), mapOf("A" to "down"), emptyMap(), unknown, t0, Duration.ZERO)
        assertEquals(listOf(Change("A", "up", "down")), d.notify)
        assertEquals(emptyMap<String, Pending>(), d.pending)
    }

    @Test
    fun anOutageWaitsThenMatures() {
        val first = damp(listOf(Change("A", "up", "down")), mapOf("A" to "down"), emptyMap(), unknown, t0, five)
        assertEquals(emptyList<Change>(), first.notify)
        assertEquals(mapOf("A" to Pending("up", "down", t0.toEpochMilli())), first.pending)
        assertEquals(at(5), first.pending.nextDue(five))
        // a later run sees no change (the state was saved) but the wait is over
        val later = damp(emptyList(), mapOf("A" to "down"), first.pending, unknown, at(5), five)
        assertEquals(listOf(Change("A", "up", "down")), later.notify)
        assertEquals(emptyMap<String, Pending>(), later.pending)
    }

    @Test
    fun aBlipIsNeverNotifiedNorItsRecovery() {
        val first = damp(listOf(Change("A", "up", "down")), mapOf("A" to "down"), emptyMap(), unknown, t0, five)
        val back = damp(listOf(Change("A", "down", "up")), mapOf("A" to "up"), first.pending, unknown, at(2), five)
        assertEquals(emptyList<Change>(), back.notify)
        assertEquals(emptyMap<String, Pending>(), back.pending)
    }

    @Test
    fun aRecoveryAfterANotifiedOutageIsSentAtOnce() {
        val d = damp(listOf(Change("A", "down", "up")), mapOf("A" to "up"), emptyMap(), unknown, t0, five)
        assertEquals(listOf(Change("A", "down", "up")), d.notify)
    }

    @Test
    fun theHubsStartTimeCounts() {
        // seen first by a run 20 minutes in: it has lasted long enough already
        val d = damp(listOf(Change("A", "up", "down")), mapOf("A" to "down"), emptyMap(), { at(-20) }, t0, five)
        assertEquals(listOf(Change("A", "up", "down")), d.notify)
    }

    @Test
    fun gettingWorseKeepsWaitingFromTheStart() {
        val first = damp(listOf(Change("A", "up", "degraded")), mapOf("A" to "degraded"), emptyMap(), unknown, t0, five)
        val worse = damp(listOf(Change("A", "degraded", "down")), mapOf("A" to "down"), first.pending, unknown, at(3), five)
        assertEquals(mapOf("A" to Pending("up", "down", t0.toEpochMilli())), worse.pending)
        val due = damp(emptyList(), mapOf("A" to "down"), worse.pending, unknown, at(5), five)
        assertEquals(listOf(Change("A", "up", "down")), due.notify)
    }

    @Test
    fun aNameThatLeftIsDropped() {
        val first = damp(listOf(Change("A", "up", "down")), mapOf("A" to "down"), emptyMap(), unknown, t0, five)
        assertEquals(Damped(emptyList(), emptyMap()), damp(emptyList(), emptyMap(), first.pending, unknown, at(9), five))
    }

    @Test
    fun beganIsTheLatestEventForTheName() {
        val view = StatusView(
            Now = "x", Level = "ok", Headline = "h",
            Events = listOf(
                Event("2026-11-03T11:00:00Z", "service", "A", "up", "down"),
                Event("2026-11-03T11:50:00Z", "service", "A", "down", "up"),
                Event("2026-11-03T11:55:00Z", "service", "A", "up", "down"),
                Event("2026-11-03T11:58:00Z", "service", "B", "up", "down"),
                Event("2026-11-03T11:59:00Z", "edge", "Edge"),
            ),
        )
        assertEquals(Instant.parse("2026-11-03T11:55:00Z"), view.began("A"))
        assertEquals(null, view.began("C"))
    }
}
