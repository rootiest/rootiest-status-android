package dev.rootiest.status.data

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

// Settings > Notifications > Notify about an outage: at once, or once it has lasted a while. rootiest-hub waits 4 minutes
// before its own alerts (ALERT_AFTER), so a blip it never alerts about would otherwise still buzz the phone.
enum class NotifyAfter(val minutes: Long) { AtOnce(0), Two(2), Five(5), Fifteen(15) }

// An outage seen but not notified yet: the state it left, the one it is in, and when it began (epoch millis).
@Serializable
data class Pending(val from: String, val to: String, val since: Long)

// What to notify now, and the outages still waiting.
data class Damped(val notify: List<Change>, val pending: Map<String, Pending>)

// Holds each outage until it has lasted [hold], measured from [began] (when the hub saw it start; this run when unknown).
// A recovery before then drops it and is not notified either, as nothing was. A recovery after a notified outage, and a
// change while one waits (down to degraded), are handled as the hub does: the first is sent at once, the second keeps
// waiting from the start. [states] are the current states, so an outage still waiting from an earlier run matures too.
fun damp(
    changes: List<Change>,
    states: Map<String, String>,
    pending: Map<String, Pending>,
    began: (String) -> Instant?,
    now: Instant,
    hold: Duration,
): Damped {
    val waiting = pending.toMutableMap()
    val notify = mutableListOf<Change>()
    for (c in changes) {
        if (c.recovered) {
            if (waiting.remove(c.name) == null) notify += c
            continue
        }
        val earlier = waiting[c.name]
        val since = earlier?.since ?: (began(c.name) ?: now).toEpochMilli()
        waiting[c.name] = Pending(earlier?.from ?: c.from, c.to, since)
    }
    for ((name, p) in waiting.toMap()) {
        val state = states[name]
        when {
            state == null -> waiting.remove(name) // left the catalog
            Duration.between(Instant.ofEpochMilli(p.since), now) >= hold -> {
                notify += Change(name, p.from, state)
                waiting.remove(name)
            }
        }
    }
    return Damped(notify, waiting)
}

// When the next waiting outage is due, or null when none waits.
fun Map<String, Pending>.nextDue(hold: Duration): Instant? = values.minOfOrNull { it.since }?.let { Instant.ofEpochMilli(it) + hold }

// When the hub saw [name] enter its current state: its latest service or server event, if the snapshot has one.
fun StatusView.began(name: String): Instant? =
    Events.orEmpty()
        .filter { it.Subject == name && (it.Kind == "service" || it.Kind == "host") }
        .mapNotNull { runCatching { Instant.parse(it.At) }.getOrNull() }
        .maxOrNull()
