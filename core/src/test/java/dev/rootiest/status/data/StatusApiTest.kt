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
    fun shownIsTheHealthOrTheStateFromAnOlderHub() {
        val view = api.parse(
            """{"Now":"x","Level":"ok","Headline":"h","Groups":[{"Name":"G","Services":[""" +
                """{"Name":"Notes","State":"up","Health":"healthy","Shown":"healthy"},""" +
                """{"Name":"Git","State":"up"}]}]}""",
        )
        val (notes, git) = view.Groups[0].Services
        assertEquals("healthy", notes.shownState)
        assertEquals("up", git.shownState) // no Shown: the State
    }

    @Test
    fun aServersLocationComesApartFromTheRestOfItsNote() {
        // as rootiest-hub's HostView encodes them since #144
        val view = api.parse(
            """{"Now":"x","Level":"ok","Headline":"h","Hosts":[""" +
                """{"Name":"Prometheus","State":"healthy","Note":"MO · hosts Gitea and Mattermost","Location":"MO","Place":"St. Louis, Missouri (Central US)"},""" +
                """{"Name":"Hestia","State":"healthy","Note":"MA","Location":"MA","Place":"Massachusetts"},""" +
                """{"Name":"r1","State":"healthy","Note":"serving traffic"}],"Groups":[]}""",
        )
        val (prometheus, hestia, r1) = view.Hosts!!
        assertEquals("St. Louis, Missouri (Central US)", prometheus.Place)
        assertEquals("hosts Gitea and Mattermost", prometheus.noteRest)
        assertEquals("", hestia.noteRest)
        assertEquals("serving traffic", r1.noteRest) // no location: the note as it is
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

    @Test
    fun backupTimeFollowsTheTimesSetting() {
        val now = java.time.Instant.parse("2026-11-02T16:05:00Z")
        val line = BackupLine("Last backup 3 h ago (Atlas → Hestia, 2.2 MiB)", "ok", "2026-11-02T13:05:00Z", "Atlas → Hestia, 2.2 MiB")
        val utc24 = TimeFormat(utc = true, hours = ClockHours.H24, date = DateStyle.Iso)
        assertEquals("Last backup 2026-11-02 13:05 UTC (Atlas → Hestia, 2.2 MiB)", line.text(utc24, now))
        assertEquals("Last backup 3 h ago (Atlas → Hestia, 2.2 MiB)", line.text(TimeFormat(style = TimeStyle.Relative), now))
        // an older hub sends the text alone
        assertEquals("Last backup 3 h ago (x)", BackupLine("Last backup 3 h ago (x)", "ok").text(utc24, now))
    }
}
