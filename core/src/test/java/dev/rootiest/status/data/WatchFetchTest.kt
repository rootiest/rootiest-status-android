package dev.rootiest.status.data

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// When the watch fetches the public status itself, and which snapshot it then shows.
class WatchFetchTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun made(minutesAgo: Long, hosts: List<HostView>? = listOf(HostView("Atlas", "healthy"))) =
        StatusView(Now = now.minus(Duration.ofMinutes(minutesAgo)).toString(), Level = "ok", Headline = "All up", Hosts = hosts)
    private fun public(minutesAgo: Long) = made(minutesAgo, hosts = null)

    @Test
    fun aFreshPhoneSnapshotMeansNoFetch() {
        assertFalse(phoneSnapshotStale(made(0), now))
        assertFalse(phoneSnapshotStale(made(29), now))
        assertFalse(phoneSnapshotStale(made(30), now)) // exactly the threshold is still fresh
    }

    @Test
    fun anOldPhoneSnapshotMeansFetch() {
        assertTrue(phoneSnapshotStale(made(31), now))
        assertTrue(phoneSnapshotStale(made(24 * 60), now))
    }

    @Test
    fun noPhoneSnapshotOrAnUnreadableTimeMeansFetch() {
        assertTrue(phoneSnapshotStale(null, now))
        assertTrue(phoneSnapshotStale(made(0).copy(Now = "x"), now))
    }

    @Test
    fun aPhoneSnapshotFromTheFutureMeansFetch() {
        assertTrue(phoneSnapshotStale(made(-10), now))
    }

    @Test
    fun theThresholdCanBeGiven() {
        assertTrue(phoneSnapshotStale(made(11), now, Duration.ofMinutes(10)))
        assertFalse(phoneSnapshotStale(made(9), now, Duration.ofMinutes(10)))
    }

    @Test
    fun aFreshPhoneSnapshotAlwaysWins() {
        val phone = made(5)
        assertEquals(WatchShown(phone, WatchSource.Phone), watchShown(phone, public(1), now))
    }

    @Test
    fun aStalePhoneSnapshotGivesWayToANewerFetch() {
        val fetched = public(2)
        assertEquals(WatchShown(fetched, WatchSource.Watch), watchShown(made(45), fetched, now))
    }

    @Test
    fun aPhoneSnapshotNewerThanTheFetchWinsEvenWhenStale() {
        val phone = made(40)
        assertEquals(WatchShown(phone, WatchSource.Phone), watchShown(phone, public(50), now))
    }

    @Test
    fun eitherAloneIsShown() {
        assertNull(watchShown(null, null, now))
        assertEquals(WatchSource.Phone, watchShown(made(90), null, now)!!.source)
        assertEquals(WatchSource.Watch, watchShown(null, public(1), now)!!.source)
    }

    @Test
    fun aFetchWithAnUnreadableTimeNeverHidesThePhone() {
        val phone = made(90)
        assertEquals(WatchSource.Phone, watchShown(phone, public(1).copy(Now = "x"), now)!!.source)
    }

    @Test
    fun theListSaysAWatchFetchIsThePublicView() {
        assertNull(WatchShown(made(0), WatchSource.Phone).sourceNote)
        assertEquals("From the watch: public view, no servers", WatchShown(public(0), WatchSource.Watch).sourceNote)
    }
}
