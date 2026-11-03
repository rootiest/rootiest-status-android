package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangesTest {
    private fun view(vararg services: ServiceView, hosts: List<HostView>? = null) =
        StatusView(Now = "x", Level = "ok", Headline = "h", Hosts = hosts, Groups = listOf(GroupView("G", services.toList())))

    @Test
    fun reportsStateChangesOnly() {
        val v = view(ServiceView(Name = "A", State = "down"), ServiceView(Name = "B", State = "up"))
        assertEquals(listOf(Change("A", "up", "down")), v.changesSince(mapOf("A" to "up", "B" to "up")))
    }

    @Test
    fun firstRunAndNewNamesStayQuiet() {
        val v = view(ServiceView(Name = "A", State = "down"))
        assertEquals(emptyList<Change>(), v.changesSince(emptyMap()))
        assertEquals(emptyList<Change>(), v.changesSince(mapOf("Other" to "up")))
    }

    @Test
    fun plannedChangesStayQuiet() {
        val v = view(ServiceView(Name = "A", State = "down", Planned = true))
        assertEquals(emptyList<Change>(), v.changesSince(mapOf("A" to "up")))
    }

    @Test
    fun recoveredMeansBackToGreen() {
        assertEquals(true, Change("A", "down", "up").recovered)
        assertEquals(true, Change("Atlas", "degraded", "healthy").recovered)
        assertEquals(false, Change("A", "up", "down").recovered)
        assertEquals(false, Change("A", "down", "degraded").recovered)
    }

    @Test
    fun includesServers() {
        val v = view(hosts = listOf(HostView("Atlas", "degraded")))
        assertEquals(listOf(Change("Atlas", "healthy", "degraded")), v.changesSince(mapOf("Atlas" to "healthy")))
    }
}
