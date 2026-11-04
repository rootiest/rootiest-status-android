package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetContentTest {
    private val view = StatusView(
        Now = "x", Level = "bad", Headline = "1 service down",
        Hosts = listOf(HostView("Atlas", "healthy"), HostView("Hestia", "degraded")),
        Groups = listOf(
            GroupView("Code", listOf(ServiceView(Name = "Gitea", State = "up"), ServiceView(Name = "Gists", State = "down"))),
            GroupView("Home", listOf(ServiceView(Name = "Photos", State = "up"), ServiceView(Name = "Files", State = "standby"))),
        ),
    )

    @Test
    fun everythingIsTheHeadlineAndWhatIsWrong() {
        assertEquals(
            WidgetContent("bad", "1 service down", listOf(WidgetRow("Hestia: degraded", "degraded"), WidgetRow("Gists: down", "down"))),
            view.widgetContent(WidgetScope.All),
        )
    }

    @Test
    fun aGroupListsEveryServiceProblemsFirst() {
        assertEquals(
            WidgetContent("bad", "Code: 1 problem", listOf(WidgetRow("Gists: down", "down"), WidgetRow("Gitea", "up"))),
            view.widgetContent(WidgetScope.Group("Code")),
        )
        assertEquals(
            WidgetContent("ok", "Home: all up", listOf(WidgetRow("Photos", "up"), WidgetRow("Files", "standby"))),
            view.widgetContent(WidgetScope.Group("Home")),
        )
    }

    @Test
    fun serversAreWarnWhenOnlyDegraded() {
        assertEquals("warn", view.widgetContent(WidgetScope.Servers).level)
        assertEquals("Servers: 1 problem", view.widgetContent(WidgetScope.Servers).headline)
    }

    @Test
    fun aMissingGroupOrHiddenServersSaySo() {
        assertEquals(WidgetContent("none", "Notes is not in the status", emptyList()), view.widgetContent(WidgetScope.Group("Notes")))
        assertEquals("Sign in to see the servers", view.copy(Hosts = null).widgetContent(WidgetScope.Servers).headline)
    }

    @Test
    fun scopesSurviveTheirKeys() {
        listOf(WidgetScope.All, WidgetScope.Servers, WidgetScope.Group("Smart home"), WidgetScope.Group("group:odd"))
            .forEach { assertEquals(it, WidgetScope.of(it.key)) }
        assertEquals(WidgetScope.All, WidgetScope.of(null))
    }
}
