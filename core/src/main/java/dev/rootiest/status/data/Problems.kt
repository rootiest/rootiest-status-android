package dev.rootiest.status.data

// What the widget lists under the headline: every service that is down and every server that is not healthy.
// A standby copy is down on purpose, so it is not a problem.
fun StatusView.problems(): List<String> {
    val services = Groups.flatMap { it.Services }
        .filter { it.isProblem }
        .map { if (it.Planned) "${it.Name}: ${it.State} (planned)" else "${it.Name}: ${it.State}" }
    val hosts = Hosts.orEmpty()
        .filter { it.isProblem }
        .map { if (it.Planned) "${it.Name}: ${it.State} (planned)" else "${it.Name}: ${it.State}" }
    return hosts + services
}

val ServiceView.isProblem: Boolean get() = State != "up" && State != "standby"
val HostView.isProblem: Boolean get() = State != "healthy"

// The list as the user chose to see it (Settings > Service list). Problems first keeps each group in place and moves
// its problems to the top, in their order; problems only also drops healthy rows and groups left empty.
fun StatusView.arranged(mode: ServiceListMode): StatusView = when (mode) {
    ServiceListMode.All -> this
    ServiceListMode.ProblemsFirst -> copy(
        Hosts = Hosts?.sortedBy { !it.isProblem },
        Groups = Groups.map { g -> g.copy(Services = g.Services.sortedBy { !it.isProblem }) },
    )
    ServiceListMode.ProblemsOnly -> copy(
        Hosts = Hosts?.filter { it.isProblem },
        Groups = Groups.map { g -> g.copy(Services = g.Services.filter { it.isProblem }) }.filter { it.Services.isNotEmpty() },
    )
}
