package dev.rootiest.status.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// Settings > Times: relative ("3 h ago") or clock times; the clock's format, hours and zone.
enum class TimeStyle { Relative, Clock }

// The clock's format: the locale's ("Nov 2, 2026, 4:05 PM"), ISO-like ("2026-11-02 16:05"), or the user's own pattern.
enum class DateStyle { Default, Iso, Custom }

// 12- or 24-hour clock; Phone follows the phone's own "Use 24-hour format", which phone24 carries.
enum class ClockHours { Phone, H12, H24 }

const val DEFAULT_PATTERN = "%Y-%m-%d %H:%M"

data class TimeFormat(
    val style: TimeStyle = TimeStyle.Clock,
    val utc: Boolean = false,
    val hours: ClockHours = ClockHours.Phone,
    val phone24: Boolean = false,
    val date: DateStyle = DateStyle.Default,
    val pattern: String = DEFAULT_PATTERN,
) {
    val hour24: Boolean get() = when (hours) {
        ClockHours.Phone -> phone24
        ClockHours.H12 -> false
        ClockHours.H24 -> true
    }

    // A moment in the app's lists. Relative falls back to the clock after a week, when "23 days ago" says less than a date.
    fun format(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        if (style == TimeStyle.Relative) {
            val ago = Duration.between(at, now).coerceAtLeast(Duration.ZERO)
            when {
                ago.toMinutes() < 1 -> return "just now"
                ago.toMinutes() < 60 -> return "${ago.toMinutes()} min ago"
                ago.toHours() < 24 -> return "${ago.toHours()} h ago"
                ago.toDays() < 7 -> return if (ago.toDays() == 1L) "1 day ago" else "${ago.toDays()} days ago"
            }
        }
        return clock(at, withDate = true, zone, locale)
    }

    // An API timestamp (ISO 8601), or the text as it came when it does not parse.
    fun format(at: String, now: Instant): String = runCatching { format(Instant.parse(at), now) }.getOrDefault(at)

    // The widget's time: always the clock, as a relative time would go stale between redraws. The time of day only,
    // except with a custom pattern, which is shown as written.
    fun timeOfDay(at: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        runCatching { clock(Instant.parse(at), withDate = false, zone, locale) }.getOrDefault(at)

    private fun clock(at: Instant, withDate: Boolean, zone: ZoneId, locale: Locale): String {
        val moment = at.atZone(if (utc) ZoneOffset.UTC else zone)
        // a custom pattern says everything itself, the zone too (%Z)
        if (date == DateStyle.Custom) return strftime(pattern, moment, locale)
        val time = DateTimeFormatter.ofPattern(if (hour24) "HH:mm" else "h:mm a", locale).format(moment)
        val text = when {
            !withDate -> time
            date == DateStyle.Iso -> DateTimeFormatter.ISO_LOCAL_DATE.format(moment) + " " + time
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(moment) + ", " + time
        }
        return if (utc) "$text UTC" else text
    }
}

// The strftime fields Settings lists, with what each gives for 2026-11-02 16:05:09 UTC.
val STRFTIME_FIELDS = listOf(
    "%Y" to "2026", "%y" to "26", "%m" to "11", "%d" to "02", "%e" to "2", "%b" to "Nov", "%B" to "November",
    "%a" to "Mon", "%A" to "Monday", "%H" to "16", "%I" to "04", "%l" to "4", "%M" to "05", "%S" to "09",
    "%p" to "PM", "%Z" to "UTC", "%z" to "+0000", "%%" to "%",
)

// A strftime-style pattern: %-fields from the list above, everything else as written (an unknown field too).
fun strftime(pattern: String, t: ZonedDateTime, locale: Locale = Locale.getDefault()): String = buildString {
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c != '%' || i == pattern.lastIndex) {
            append(c); i++; continue
        }
        val f = pattern[i + 1]
        val hour12 = (t.hour + 11) % 12 + 1
        append(
            when (f) {
                'Y' -> t.year.toString()
                'y' -> "%02d".format(t.year % 100)
                'm' -> "%02d".format(t.monthValue)
                'd' -> "%02d".format(t.dayOfMonth)
                'e' -> t.dayOfMonth.toString()
                'b' -> t.month.getDisplayName(TextStyle.SHORT, locale)
                'B' -> t.month.getDisplayName(TextStyle.FULL, locale)
                'a' -> t.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                'A' -> t.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                'H' -> "%02d".format(t.hour)
                'I' -> "%02d".format(hour12)
                'l' -> hour12.toString()
                'M' -> "%02d".format(t.minute)
                'S' -> "%02d".format(t.second)
                'p' -> if (t.hour < 12) "AM" else "PM"
                'Z' -> if (t.zone == ZoneOffset.UTC) "UTC" else DateTimeFormatter.ofPattern("zzz", locale).format(t)
                'z' -> t.offset.id.let { if (it == "Z") "+0000" else it.replace(":", "") }
                '%' -> "%"
                else -> "%$f"
            },
        )
        i += 2
    }
}
