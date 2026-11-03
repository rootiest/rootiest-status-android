package dev.rootiest.status.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Relative ("3 h ago") or clock times ("Nov 2, 2026, 4:05 PM"), in the phone's zone or in UTC (Settings > Times).
enum class TimeStyle { Relative, Clock }

data class TimeFormat(val style: TimeStyle = TimeStyle.Clock, val utc: Boolean = false) {
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
        return clock(at, FormatStyle.MEDIUM, zone, locale)
    }

    // An API timestamp (ISO 8601), or the text as it came when it does not parse.
    fun format(at: String, now: Instant): String = runCatching { format(Instant.parse(at), now) }.getOrDefault(at)

    // The widget's time of day: always the clock, as a relative time would go stale between redraws.
    fun timeOfDay(at: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        runCatching { clock(Instant.parse(at), null, zone, locale) }.getOrDefault(at)

    private fun clock(at: Instant, date: FormatStyle?, zone: ZoneId, locale: Locale): String {
        val formatter = (if (date == null) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        else DateTimeFormatter.ofLocalizedDateTime(date, FormatStyle.SHORT)).withLocale(locale)
        return if (utc) formatter.format(at.atZone(ZoneOffset.UTC)) + " UTC" else formatter.format(at.atZone(zone))
    }
}
