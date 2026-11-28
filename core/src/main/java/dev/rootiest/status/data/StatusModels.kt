package dev.rootiest.status.data

import java.time.Instant
import kotlinx.serialization.Serializable

// The JSON from rootiest-hub's GET /api/status (internal/status/view.go, type View). Field names match Go's
// exported names. Only what the app shows is declared; the parser ignores the rest.

@Serializable
data class StatusView(
    val Now: String,
    val Level: String, // ok, warn, bad
    val Headline: String,
    val Failover: Boolean = false,
    val Hosts: List<HostView>? = null, // null unless the viewer may see the Servers section
    val Groups: List<GroupView> = emptyList(),
    val Events: List<Event>? = null,
)

@Serializable
data class HostView(
    val Name: String,
    val State: String,
    val Note: String = "", // its location first, then the rest: "MO · hosts Gitea and Mattermost"
    val Vitals: String = "",
    val Planned: Boolean = false,
    val Location: String = "", // where it is, as a code ("MO"); "" from older hubs
    val Place: String = "", // the location in full ("St. Louis, Missouri (Central US)", or the state's name); "" from older hubs
) {
    // Note without the location at its start, for the line that shows the location as a tap target.
    val noteRest: String
        get() = if (Location.isNotEmpty() && Note.startsWith(Location)) Note.removePrefix(Location).removePrefix(" · ") else Note
}

@Serializable
data class GroupView(
    val Name: String,
    val Services: List<ServiceView> = emptyList(),
)

@Serializable
data class ServiceView(
    val Name: String,
    val Host: String = "",
    val Direct: Boolean = false, // probed by TCP connect, not through the edge: Host is a name, not a link
    val State: String, // up, down, standby, unhealthy (answers, but a health check fails)
    val Uptime: String = "",
    val Bar: List<BarCell> = emptyList(),
    val Detail: String = "",
    val Planned: Boolean = false,
    val Backup: BackupLine? = null, // only for services that publish a backup report
    val MailTest: MailTestLine? = null, // only for a service with an end-to-end mail test (Mail), and only for staff
    val Checks: List<CheckView> = emptyList(), // its sub-checks ("SMTP submission", "IMAP"); a down one makes Shown unhealthy; staff only
    val Health: String = "", // its containers' health checks: healthy, unhealthy, starting, or "" (none known)
    val Shown: String = "", // what the page's pill and dot say: State, or healthy/starting for one that answers; "" from older hubs
) {
    // What to show for the service: the hub's Shown, or its State from a hub too old to send one.
    val shownState: String get() = Shown.ifEmpty { State }

    // The sub-checks that are down, each with why: what the watch shows of them.
    val failingChecks: List<CheckView> get() = Checks.filter { it.State == "down" }
}

// One sub-check under a service, as rootiest-hub's CheckView encodes it. State is "" before its first check; Reason is sent only
// while it is down ("TLS: certificate expired on ...").
@Serializable
data class CheckView(
    val Name: String,
    val State: String = "",
    val Reason: String = "",
) {
    // The state as the page writes it: "unknown" before the first check.
    val shownState: String get() = State.ifEmpty { "unknown" }

    // "IMAP up", or "IMAP down: TLS: certificate expired on ..." while it is down.
    val text: String get() = "$Name $shownState" + if (Reason.isNotEmpty()) ": $Reason" else ""
}

// The line about a service's last backup, as the page shows it under the service ("Last backup 3 h ago (...)").
// At (ISO 8601) and Detail ("Atlas → Hestia, 2.2 MiB") are its parts, sent by newer hubs so the time can follow Settings.
@Serializable
data class BackupLine(
    val Text: String,
    val Level: String = "unknown", // ok, warn (older than the hub allows), unknown (no report)
    val At: String = "",
    val Detail: String = "",
) {
    // The line with the time written by Settings > Times; the hub's own text when it sent no parts.
    fun text(times: TimeFormat, now: Instant): String =
        if (At.isBlank() || Detail.isBlank()) Text else "Last backup ${times.format(At, now)} ($Detail)"
}

// The line about a service's last end-to-end mail test, the same shape as BackupLine (rootiest-hub's MailTestView): "Last mail test 4 h
// ago (failed: not delivered within 3 min)". At and Detail are empty while the test is off or has not run; Text then says so.
@Serializable
data class MailTestLine(
    val Text: String,
    val Level: String = "unknown", // ok, warn (failed, or older than the hub allows), unknown (off or not run yet)
    val At: String = "",
    val Detail: String = "",
) {
    // The line with the time written by Settings > Times; the hub's own text when it sent no parts.
    fun text(times: TimeFormat, now: Instant): String =
        if (At.isBlank() || Detail.isBlank()) Text else "Last mail test ${times.format(At, now)} ($Detail)"
}

@Serializable
data class BarCell(
    val Day: String,
    val Class: String, // ok, warn, bad, none
    val Title: String,
)

@Serializable
data class Event(
    val At: String,
    val Kind: String, // edge, service, host, message
    val Subject: String, // the service's or server's name; "Edge" for an edge move; a message's severity, as it reads ("Warning")
    val From: String = "",
    val To: String = "",
    val Detail: String = "", // a message's text
    val Ref: String = "", // a message's id, the same on every host
    val Severity: String = "", // a message's: info, notice, warn, critical
    val Audience: List<String> = emptyList(), // who a message is for; sent only to staff
    val Notify: Boolean = false, // a message posted to be notified
)
