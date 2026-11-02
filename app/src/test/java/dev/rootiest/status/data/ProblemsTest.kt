package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProblemsTest {
    private fun service(name: String, state: String, planned: Boolean = false) =
        ServiceView(Name = name, State = state, Planned = planned)

    private fun view(vararg services: ServiceView, hosts: List<HostView>? = null) =
        StatusView(Now = "x", Level = "bad", Headline = "h", Hosts = hosts, Groups = listOf(GroupView("G", services.toList())))

    @Test
    fun listsDownServicesButNotStandby() {
        val v = view(service("A", "up"), service("B", "down"), service("C", "standby"), service("D", "down", planned = true))
        assertEquals(listOf("B: down", "D: down (planned)"), v.problems())
    }

    @Test
    fun serversComeFirstAndHealthyOnesAreLeftOut() {
        val v = view(service("B", "down"), hosts = listOf(HostView("Atlas", "healthy"), HostView("Heracles", "degraded")))
        assertEquals(listOf("Heracles: degraded", "B: down"), v.problems())
    }

    @Test
    fun nothingWrongInTheFixture() {
        val v = StatusApi().parse(javaClass.getResource("/status-sample.json")!!.readText())
        assertEquals(emptyList<String>(), v.problems())
    }
}
