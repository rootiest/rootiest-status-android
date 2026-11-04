package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun allUpHasNoRows() {
        val ok = StatusView(Now = "x", Level = "ok", Headline = "All systems operational")
        assertEquals(WatchSummary("ok", "All systems operational", emptyList(), 0), ok.watchSummary())
    }

    @Test
    fun theSnapshotCrossesIntactAndJunkIsDropped() {
        assertEquals(view, WatchSnapshot.decode(WatchSnapshot.encode(view)))
        assertNull(WatchSnapshot.decode("not json"))
    }
}
