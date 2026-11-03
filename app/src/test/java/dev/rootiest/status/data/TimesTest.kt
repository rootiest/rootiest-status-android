package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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

    private fun clean(s: String) = s.replace('\u202f', ' ')

    @Test
    fun twelveOrTwentyFourHours() {
        val at = "2026-11-02T16:05:00Z"
        assertEquals("Nov 2, 2026, 16:05 UTC", TimeFormat(utc = true, hours = ClockHours.H24).at(at))
        assertEquals("Nov 2, 2026, 4:05 PM UTC", clean(TimeFormat(utc = true, hours = ClockHours.H12, phone24 = true).at(at)))
        assertEquals("16:05 UTC", TimeFormat(utc = true, phone24 = true).timeOfDay(at, newYork, Locale.US))
        assertEquals("4:05 PM UTC", clean(TimeFormat(utc = true).timeOfDay(at, newYork, Locale.US)))
    }

    @Test
    fun isoDates() {
        assertEquals("2026-11-02 11:05", TimeFormat(date = DateStyle.Iso, hours = ClockHours.H24).at("2026-11-02T16:05:00Z"))
        assertEquals("2026-11-02 16:05 UTC", TimeFormat(date = DateStyle.Iso, hours = ClockHours.H24, utc = true).at("2026-11-02T16:05:00Z"))
    }

    @Test
    fun customPatterns() {
        val t = Instant.parse("2026-11-02T16:05:09Z").atZone(ZoneOffset.UTC)
        // every field Settings lists gives what it says
        STRFTIME_FIELDS.forEach { (field, want) -> assertEquals(field, want, strftime(field, t, Locale.US)) }
        assertEquals("16:05:09 on Mon 2 Nov", strftime("%H:%M:%S on %a %e %b", t, Locale.US))
        assertEquals("%Q stays, and so does a trailing %", strftime("%Q stays, and so does a trailing %", t, Locale.US))
        assertEquals("EST", strftime("%Z", t.withZoneSameInstant(newYork), Locale.US))
        assertEquals("-0500", strftime("%z", t.withZoneSameInstant(newYork), Locale.US))
        // the pattern is used as written: no " UTC" added, and the zone setting still picks the zone
        val f = TimeFormat(date = DateStyle.Custom, pattern = "%H:%M %Z", utc = true)
        assertEquals("16:05 UTC", f.at("2026-11-02T16:05:00Z"))
        assertEquals("11:05 EST", f.copy(utc = false).at("2026-11-02T16:05:00Z"))
        assertEquals("16:05 UTC", f.timeOfDay("2026-11-02T16:05:00Z"))
    }
}
