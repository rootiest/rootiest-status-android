package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupsTest {
    private val view = StatusApi().parse(javaClass.getResource("/status-sample.json")!!.readText())

    @Test
    fun findsServiceByGroupAndName() {
        val group = view.Groups.first()
        val service = group.Services.first()
        assertEquals(service, view.service(group.Name, service.Name))
        assertNull(view.service(group.Name, "no such service"))
        assertNull(view.service("no such group", service.Name))
    }

    @Test
    fun eventsForKeepsOnlyThatServicesChanges() {
        val events = view.eventsFor("Notes")
        assertTrue(events.isNotEmpty()) // the fixture has Notes going down and back up on 2026-10-27
        assertTrue(events.all { it.Kind == "service" && it.Subject == "Notes" })
        assertTrue(view.eventsFor("Edge").isEmpty()) // edge moves are not a service's
    }

    @Test
    fun summaryDropsTheDatePrefix() {
        assertEquals("100.00% up", BarCell("2026-11-01", "ok", "2026-11-01: 100.00% up").summary())
        assertEquals("odd title", BarCell("2026-11-01", "ok", "odd title").summary())
    }
}
