package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusApiTest {
    private val api = StatusApi()

    // A copy of a signed-out answer from status.rootiest.dev; refresh it when the API changes.
    @Test
    fun parsesRealSnapshot() {
        val body = javaClass.getResource("/status-sample.json")!!.readText()
        val view = api.parse(body)
        assertTrue(view.Level in setOf("ok", "warn", "bad"))
        assertTrue(view.Groups.isNotEmpty())
        assertTrue(view.Groups.all { g -> g.Services.all { it.Bar.isNotEmpty() } })
        assertNull(view.Hosts) // signed out: no Servers section
    }

    @Test
    fun ignoresFieldsTheAppDoesNotDeclare() {
        val view = api.parse("""{"Now":"x","Level":"bad","Headline":"h","Novel":1,"Groups":[]}""")
        assertEquals("bad", view.Level)
    }
}
