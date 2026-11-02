package dev.rootiest.status.data

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
    val Note: String = "",
    val Vitals: String = "",
    val Planned: Boolean = false,
)

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
    val State: String, // up, down, standby
    val Uptime: String = "",
    val Bar: List<BarCell> = emptyList(),
    val Detail: String = "",
    val Planned: Boolean = false,
)

@Serializable
data class BarCell(
    val Day: String,
    val Class: String, // ok, warn, bad, none
    val Title: String,
)

@Serializable
data class Event(
    val At: String,
    val Kind: String, // edge, service, host
    val Subject: String, // the service's or server's name; "Edge" for an edge move
    val From: String = "",
    val To: String = "",
    val Detail: String = "",
)
