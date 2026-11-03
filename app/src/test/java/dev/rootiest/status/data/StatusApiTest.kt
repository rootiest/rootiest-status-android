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

    @Test
    fun parsesTheBackupLine() {
        // as rootiest-hub's BackupView encodes it; the live answer has none while no report is published
        val view = api.parse(
            """{"Now":"x","Level":"ok","Headline":"h","Groups":[{"Name":"G","Services":[""" +
                """{"Name":"Notes","State":"up","Backup":{"Text":"Last backup 3 h ago (notes → b2, 12 MB)","Level":"ok"}},""" +
                """{"Name":"Git","State":"up"}]}]}""",
        )
        val (notes, git) = view.Groups[0].Services
        assertEquals(BackupLine("Last backup 3 h ago (notes → b2, 12 MB)", "ok"), notes.Backup)
        assertNull(git.Backup)
    }
}
