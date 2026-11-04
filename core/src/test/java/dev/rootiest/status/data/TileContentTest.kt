package dev.rootiest.status.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The tile says what an Everything widget says, lit only when all is well.
class TileContentTest {
    private fun view(level: String, headline: String) = StatusView(Now = "x", Level = level, Headline = headline, Groups = emptyList())

    @Test
    fun allUpIsALitTile() {
        val tile = view("ok", "All systems operational").tileContent()
        assertEquals(TileContent("ok", "All systems operational"), tile)
        assertTrue(tile.active)
    }

    @Test
    fun aProblemDimsItWithTheHeadline() {
        assertFalse(view("bad", "2 services down").tileContent().active)
        assertFalse(view("warn", "1 server degraded").tileContent().active)
        assertEquals("2 services down", view("bad", "2 services down").tileContent().subtitle)
    }

    @Test
    fun itMatchesTheWidget() {
        val v = view("warn", "1 server degraded")
        val widget = v.widgetContent(WidgetScope.All)
        assertEquals(TileContent(widget.level, widget.headline), v.tileContent())
    }
}
