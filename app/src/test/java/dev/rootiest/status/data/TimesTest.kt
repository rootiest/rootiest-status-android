package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TimesTest {
    private val now = Instant.parse("2026-11-02T20:00:00Z")
    private val newYork = ZoneId.of("America/New_York")
    private fun TimeFormat.at(iso: String) = format(Instant.parse(iso), now, newYork, Locale.US)

    @Test
    fun relativeSteps() {
        val f = TimeFormat(TimeStyle.Relative)
        assertEquals("just now", f.at("2026-11-02T19:59:30Z"))
        assertEquals("just now", f.at("2026-11-02T20:05:00Z")) // a clock a little ahead is not "in 5 min"
        assertEquals("42 min ago", f.at("2026-11-02T19:18:00Z"))
        assertEquals("3 h ago", f.at("2026-11-02T16:30:00Z"))
        assertEquals("1 day ago", f.at("2026-11-01T19:00:00Z"))
        assertEquals("6 days ago", f.at("2026-10-27T20:00:00Z"))
    }

    @Test
    fun relativeTurnsIntoTheClockAfterAWeek() {
        assertEquals(TimeFormat(TimeStyle.Clock).at("2026-10-20T15:00:00Z"), TimeFormat(TimeStyle.Relative).at("2026-10-20T15:00:00Z"))
    }

    @Test
    fun clockInTheLocalZoneOrUtc() {
        assertEquals("Nov 2, 2026, 11:00 AM", TimeFormat(TimeStyle.Clock).at("2026-11-02T16:00:00Z").replace(' ', ' '))
        assertEquals("Nov 2, 2026, 4:00 PM UTC", TimeFormat(TimeStyle.Clock, utc = true).at("2026-11-02T16:00:00Z").replace(' ', ' '))
    }

    @Test
    fun timeOfDayForTheWidget() {
        assertEquals("11:00 AM", TimeFormat().timeOfDay("2026-11-02T16:00:00Z", newYork, Locale.US).replace(' ', ' '))
        assertEquals("4:00 PM UTC", TimeFormat(utc = true).timeOfDay("2026-11-02T16:00:00Z", newYork, Locale.US).replace(' ', ' '))
        assertEquals("not a time", TimeFormat().timeOfDay("not a time"))
    }
}
