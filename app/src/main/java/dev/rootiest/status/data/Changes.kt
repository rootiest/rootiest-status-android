package dev.rootiest.status.data

// A service or server whose state differs from the previous fetch.
data class Change(val name: String, val from: String, val to: String)

// Every service's and server's state by name, as saved between background fetches.
// shortcut: a server and a service with the same name would share a key; none do today, prefix them if that changes
fun StatusView.stateMap(): Map<String, String> =
    Groups.flatMap { it.Services }.associate { it.Name to it.State } + Hosts.orEmpty().associate { it.Name to it.State }

// What changed since [old]. Nothing on the first run (no [old] yet), nothing for a name that is new, and nothing a
// planned window expects, as rootiest-hub holds its own alerts for those.
fun StatusView.changesSince(old: Map<String, String>): List<Change> {
    val planned = (Groups.flatMap { it.Services }.filter { it.Planned }.map { it.Name } +
        Hosts.orEmpty().filter { it.Planned }.map { it.Name }).toSet()
    return stateMap().mapNotNull { (name, state) ->
        val before = old[name]
        if (before == null || before == state || name in planned) null else Change(name, before, state)
    }
}
