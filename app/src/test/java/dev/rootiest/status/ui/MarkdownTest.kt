package dev.rootiest.status.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    private val colors = MarkdownColors(Color.Blue, Color.Gray, Color.DarkGray)
    private fun md(s: String, onLink: (String) -> Unit = {}) = markdown(s, colors, onLink)

    @Test
    fun paragraphsListsAndBreaksBecomeLines() {
        val text = md("Pushing to **Gitea** fails:\n\n- since this morning\n- with `HTTP 502`\n\nSee [the status page](https://status.rootiest.dev).")
        assertEquals("Pushing to Gitea fails:\n\n• since this morning\n• with HTTP 502\n\nSee the status page.", text.text)
    }

    @Test
    fun stylesFollowTheMarkup() {
        val text = md("a **bold** *it* `code`")
        fun styleAt(word: String) = text.spanStyles.filter { text.text.indexOf(word) in it.start until it.end }.map { it.item }
        assertTrue(styleAt("bold").any { it.fontWeight == FontWeight.Bold })
        assertTrue(styleAt("it").any { it.fontStyle == FontStyle.Italic })
        assertTrue(styleAt("code").any { it.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun onlyWebAndMailLinksAreLinks() {
        val opened = mutableListOf<String>()
        val text = md("[web](https://example.com) [mail](mailto:a@b.c) [bad](javascript:alert(1)) [file](file:///etc/passwd)") { opened += it }
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        assertEquals(listOf("https://example.com", "mailto:a@b.c"), links)
        assertEquals("web mail bad file", text.text) // the others keep their words
        (text.getLinkAnnotations(0, text.length).first().item as LinkAnnotation.Url).linkInteractionListener!!.onClick(
            text.getLinkAnnotations(0, text.length).first().item,
        )
        assertEquals(listOf("https://example.com"), opened)
    }

    @Test
    fun rawHtmlIsDroppedAndImagesAreTheirDescription() {
        assertEquals("Hi there", md("Hi <b>there</b>").text)
        assertEquals("before\n\nafter", md("before\n\n<script>alert(1)</script>\n\nafter").text)
        assertEquals("a cat", md("![a cat](https://example.com/cat.png)").text)
    }

    @Test
    fun orderedAndNestedLists() {
        assertEquals("3. three\n4. four", md("3. three\n4. four").text)
        assertEquals("• outer\n    • inner", md("- outer\n  - inner").text)
    }

    @Test
    fun quotesHeadingsAndCode() {
        assertEquals("Title\n\nquoted\n\nline 1\nline 2", md("# Title\n\n> quoted\n\n```\nline 1\nline 2\n```").text)
    }
}
