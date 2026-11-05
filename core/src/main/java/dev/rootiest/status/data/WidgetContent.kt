package dev.rootiest.status.data

// What one widget shows (chosen per widget when it is added, or later from the home screen's widget settings):
// everything, one group of services, or the servers.
sealed interface WidgetScope {
    data object All : WidgetScope
    data object Servers : WidgetScope
    data class Group(val name: String) : WidgetScope

    // saved per widget as text: "all", "servers" or "group:<name>"
    val key: String get() = when (this) {
        All -> "all"
        Servers -> "servers"
        is Group -> "group:$name"
    }

    companion object {
        fun of(key: String?): WidgetScope = when {
            key == "servers" -> Servers
            key != null && key.startsWith("group:") -> Group(key.removePrefix("group:"))
            else -> All
        }
    }
}

// A row under the headline: its text and the state that colours its dot.
data class WidgetRow(val text: String, val state: String)

data class WidgetContent(val level: String, val headline: String, val rows: List<WidgetRow>)

// Everything: the overall level and headline, then what is wrong. A group or the servers: their name and how many have
// a problem, then every row with problems first, so a glance at a small widget shows the one that matters.
fun StatusView.widgetContent(scope: WidgetScope): WidgetContent = when (scope) {
    WidgetScope.All -> WidgetContent(
        Level,
        Headline,
        Hosts.orEmpty().filter { it.isProblem }.map { WidgetRow(label(it.Name, it.State, it.Planned), it.State) } +
            Groups.flatMap { it.Services }.filter { it.isProblem }.map { WidgetRow(label(it.Name, it.State, it.Planned), it.shownState) },
    )
    WidgetScope.Servers -> Hosts?.let { hosts ->
        section("Servers", hosts.map { it.isProblem to WidgetRow(rowText(it.Name, it.State, it.Planned, it.isProblem), it.State) })
    } ?: WidgetContent("none", "Sign in to see the servers", emptyList()) // only signed-in viewers may see them
    is WidgetScope.Group -> Groups.find { it.Name == scope.name }?.let { group ->
        section(group.Name, group.Services.map { it.isProblem to WidgetRow(rowText(it.Name, it.State, it.Planned, it.isProblem), it.shownState) })
    } ?: missing(scope.name)
}

private fun section(name: String, rows: List<Pair<Boolean, WidgetRow>>): WidgetContent {
    val problems = rows.filter { it.first }.map { it.second }
    val level = when {
        problems.any { it.state == "down" || it.state == "unhealthy" } -> "bad"
        problems.isNotEmpty() -> "warn"
        else -> "ok"
    }
    val summary = when (problems.size) {
        0 -> "all up"
        1 -> "1 problem"
        else -> "${problems.size} problems"
    }
    return WidgetContent(level, "$name: $summary", problems + rows.filterNot { it.first }.map { it.second })
}

// a group renamed or removed since the widget was set up
private fun missing(name: String) = WidgetContent("none", "$name is not in the status", emptyList())

private fun label(name: String, state: String, planned: Boolean) = if (planned) "$name: $state (planned)" else "$name: $state"

private fun rowText(name: String, state: String, planned: Boolean, problem: Boolean) = if (problem) label(name, state, planned) else name
