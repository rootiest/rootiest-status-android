package dev.rootiest.status.data

// What the widget lists under the headline: every service that is down and every server that is not healthy.
// A standby copy is down on purpose, so it is not a problem.
fun StatusView.problems(): List<String> {
    val services = Groups.flatMap { it.Services }
        .filter { it.State != "up" && it.State != "standby" }
        .map { if (it.Planned) "${it.Name}: ${it.State} (planned)" else "${it.Name}: ${it.State}" }
    val hosts = Hosts.orEmpty()
        .filter { it.State != "healthy" }
        .map { if (it.Planned) "${it.Name}: ${it.State} (planned)" else "${it.Name}: ${it.State}" }
    return hosts + services
}
