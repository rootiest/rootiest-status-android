package dev.rootiest.status.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTest {
    private fun service(name: String, state: String) = ServiceView(Name = name, State = state)
    private val view = StatusView(
        Now = "x", Level = "bad", Headline = "4 services down",
        Hosts = listOf(HostView("Atlas", "healthy")),
        Groups = listOf(GroupView("Code", listOf("Gitea", "Gists", "CI", "Pages", "Docs").mapIndexed { i, n -> service(n, if (i < 4) "down" else "up") })),
    )

    @Test
    fun theTileShowsTheFirstProblemsAndCountsTheRest() {
        assertEquals(
            WatchSummary("bad", "4 services down", listOf("Gitea: down", "Gists: down", "CI: down"), 1),
            view.watchSummary(),
        )
    }

    @Test
    fun theLinesUnderTheHeadline() {
        assertEquals("Gitea: down\nGists: down\nCI: down\nand 1 more", view.watchSummary().detail())
        assertEquals("Gitea: down\nGists: down\nCI: down\nPages: down", view.watchSummary(maxRows = 4).detail())
        assertEquals("Tap for every service", WatchSummary("ok", "All up", emptyList(), 0).detail())
    }

    @Test
    fun theComplicationCountsWhatIsDownAndWhatIsUp() {
        assertEquals(WatchComplication("bad", "4 down", up = 1, total = 5), view.watchComplication())
        val ok = StatusView(Now = "x", Level = "ok", Headline = "All up", Groups = listOf(GroupView("Code", listOf(service("Gitea", "up"), service("Pages", "standby")))))
        assertEquals(WatchComplication("ok", "OK", up = 2, total = 2), ok.watchComplication())
    }

    @Test
    fun aDownServerCountsTooButIsNotAService() {
        val v = view.copy(Hosts = listOf(HostView("Atlas", "unhealthy")))
        assertEquals("5 down", v.watchComplication().text)
        assertEquals(5, v.watchComplication().total)
    }

    @Test
    fun allUpHasNoRows() {
        val ok = StatusView(Now = "x", Level = "ok", Headline = "All systems operational")
        assertEquals(WatchSummary("ok", "All systems operational", emptyList(), 0), ok.watchSummary())
    }

    @Test
    fun theSnapshotCrossesWithWhatTheWatchShowsAndJunkIsDropped() {
        assertEquals(view, WatchSnapshot.decode(WatchSnapshot.encode(view)))
        assertNull(WatchSnapshot.decode("not json"))
    }

    private val now = "2026-11-09T12:00:00Z"
    private fun message(ref: String, at: String = now) = Event(At = at, Kind = "message", Subject = "Notice", Detail = "text $ref", Ref = ref)

    @Test
    fun aMessageShowsOnTheTileWhenAllIsWellAndIsCountedWhenNot() {
        val ok = StatusView(Now = now, Level = "ok", Headline = "All up", Events = listOf(message("a"), message("b")))
        assertEquals("Notice: text a", ok.watchSummary().detail())
        val bad = view.copy(Now = now, Events = listOf(message("a"), message("b")))
        assertEquals("Gitea: down\nGists: down\nand 2 more\n2 messages", bad.watchSummary().detail())
    }

    @Test
    fun aMessageOlderThanTheListShowsIsNotOnTheTile() {
        val ok = StatusView(Now = now, Level = "ok", Headline = "All up", Events = listOf(message("a", "2026-10-01T00:00:00Z")))
        assertEquals("Tap for every service", ok.watchSummary().detail())
    }

    @Test
    fun theSnapshotCarriesRecentMessagesAndServiceChangesButNoBars() {
        val change = Event(At = now, Kind = "service", Subject = "Gitea", From = "up", To = "down")
        val edge = Event(At = now, Kind = "edge", Subject = "Edge")
        val old = message("old", "2026-10-01T00:00:00Z")
        val full = StatusView(
            Now = now, Level = "ok", Headline = "All up",
            Groups = listOf(GroupView("Code", listOf(ServiceView(Name = "Gitea", State = "up", Uptime = "99%", Detail = "d", Backup = BackupLine("b"), Bar = listOf(BarCell("d", "ok", "t")))))),
            Events = listOf(message("a"), change, edge, old),
        )
        val sent = WatchSnapshot.decode(WatchSnapshot.encode(full))!!
        assertEquals(listOf(message("a"), change), sent.Events)
        val service = sent.Groups[0].Services[0]
        assertEquals(emptyList<BarCell>(), service.Bar)
        assertEquals(listOf("99%", "d", "b"), listOf(service.Uptime, service.Detail, service.Backup!!.Text))
    }

    @Test
    fun theSnapshotSaysHowOldItIs() {
        val v = view.copy(Now = "2026-11-09T12:00:00Z")
        assertEquals("Updated just now", v.updatedAgo(Instant.parse("2026-11-09T12:00:30Z")))
        assertEquals("Updated 5 min ago", v.updatedAgo(Instant.parse("2026-11-09T12:05:00Z")))
        assertNull(view.updatedAgo(Instant.parse("2026-11-09T12:05:00Z"))) // its Now is "x"
    }

    // A Data Layer item holds at most 100 KB; the live answer (status-sample.json) is about 49 KB signed out, and more than
    // 100 KB signed in, almost all of it uptime bars the watch does not show.
    @Test
    fun aRealSnapshotFitsWellInADataItemAndShowsTheSame() {
        val full = StatusApi({ null }).parse(javaClass.getResource("/status-sample.json")!!.readText())
        val sent = WatchSnapshot.encode(full)
        assertTrue("${sent.length} bytes", sent.length < 20_000)
        assertEquals(full.watchSummary(), WatchSnapshot.decode(sent)!!.watchSummary())
    }
}
