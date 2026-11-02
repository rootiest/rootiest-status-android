package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StatusCacheTest {
    @get:Rule
    val dir = TemporaryFolder()

    private val view = StatusView(Now = "2026-11-02T00:00:00Z", Level = "ok", Headline = "All systems operational")

    @Test
    fun savesWholeFilesAndLeavesNoTemporaryOnes() {
        val cache = StatusCache(dir.root)
        cache.save(view)
        cache.save(view.copy(Level = "bad")) // replaces the first
        cache.saveStates(mapOf("Gitea" to "down"))
        assertEquals("bad", cache.load()?.Level)
        assertEquals(mapOf("Gitea" to "down"), cache.loadStates())
        assertEquals(setOf("last-status.json", "last-states.json"), dir.root.list()!!.toSet())
    }

    @Test
    fun aSnapshotOlderThanAskedIsNotReturned() {
        val cache = StatusCache(dir.root)
        assertNull(cache.load()) // none yet
        cache.save(view)
        val saved = File(dir.root, "last-status.json").lastModified()
        assertEquals("ok", cache.load(maxAgeMillis = 60_000, nowMillis = saved + 30_000)?.Level)
        assertNull(cache.load(maxAgeMillis = 60_000, nowMillis = saved + 90_000))
    }

    @Test
    fun aDamagedFileReadsAsNothing() {
        File(dir.root, "last-status.json").writeText("{\"Now\":") // as an interrupted plain write would have left it
        File(dir.root, "last-states.json").writeText("{")
        val cache = StatusCache(dir.root)
        assertNull(cache.load())
        assertEquals(emptyMap<String, String>(), cache.loadStates())
    }
}
