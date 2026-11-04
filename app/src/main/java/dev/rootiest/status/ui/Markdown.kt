package dev.rootiest.status.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

// Support ticket messages are Markdown, as written on the desk. The desk renders them as GitHub-flavoured Markdown with raw HTML dropped and
// images not shown; this draws them as styled text: bold, italics, code, headings, lists, quotes and links. Raw HTML is dropped too, an image
// is its description, and only http(s) and mailto links are links (the desk neutralises the rest), opened through onLink.

data class MarkdownColors(val link: Color, val code: Color, val quote: Color)

private val parser: Parser = Parser.builder().build()

fun markdown(source: String, colors: MarkdownColors, onLink: (String) -> Unit): AnnotatedString =
    Renderer(colors, onLink).blocks(parser.parse(source)).join("\n\n")

private class Renderer(val colors: MarkdownColors, val onLink: (String) -> Unit) {
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))

    fun children(parent: Node): List<Node> = generateSequence(parent.firstChild) { it.next }.toList()

    // Each block of parent as its own text; the caller joins them.
    fun blocks(parent: Node): List<AnnotatedString> = children(parent).mapNotNull { n ->
        when (n) {
            is Paragraph -> inlines(n)
            is Heading -> buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inlines(n)) } }
            is ListBlock -> list(n)
            is BlockQuote -> buildAnnotatedString {
                withStyle(SpanStyle(color = colors.quote, fontStyle = FontStyle.Italic)) { append(blocks(n).join("\n\n")) }
            }
            is FencedCodeBlock -> code(n.literal)
            is IndentedCodeBlock -> code(n.literal)
            is ThematicBreak -> AnnotatedString("―――")
            else -> null // raw HTML blocks are dropped
        }
    }

    // One line per item, its marker first; what an item holds after its first paragraph (more text, a nested list) goes under it, indented.
    fun list(list: ListBlock): AnnotatedString = buildAnnotatedString {
        var number = (list as? OrderedList)?.markerStartNumber ?: 1
        children(list).filterIsInstance<ListItem>().forEachIndexed { i, item ->
            if (i > 0) append('\n')
            append(if (list is OrderedList) "${number++}. " else "• ")
            blocks(item).forEachIndexed { j, block ->
                if (j > 0) append("\n")
                append(if (j > 0) indent(block, "    ") else block)
            }
        }
    }

    fun indent(text: AnnotatedString, by: String): AnnotatedString = buildAnnotatedString {
        text.split('\n').forEachIndexed { i, line ->
            if (i > 0) append('\n')
            append(by)
            append(line)
        }
    }

    fun code(literal: String) = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.code)) { append(literal.trimEnd('\n')) }
    }

    fun inlines(parent: Node): AnnotatedString = buildAnnotatedString { appendInlines(parent) }

    fun AnnotatedString.Builder.appendInlines(parent: Node) {
        for (n in children(parent)) {
            when (n) {
                is Text -> append(n.literal)
                is SoftLineBreak -> append(' ')
                is HardLineBreak -> append('\n')
                is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.code)) { append(n.literal) }
                is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(n) }
                is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlines(n) }
                is Image -> appendInlines(n) // its description, as text
                is Link -> if (safeLink(n.destination)) {
                    val url = n.destination
                    withLink(LinkAnnotation.Url(url, linkStyle) { onLink(url) }) { appendInlines(n) }
                } else {
                    appendInlines(n)
                }
                else -> appendInlines(n) // raw HTML has no children, so it is dropped
            }
        }
    }
}

private fun List<AnnotatedString>.join(separator: String): AnnotatedString = buildAnnotatedString {
    this@join.forEachIndexed { i, s ->
        if (i > 0) append(separator)
        append(s)
    }
}

// AnnotatedString keeps its styles through subSequence, so a split line keeps its bold, links and code.
private fun AnnotatedString.split(c: Char): List<AnnotatedString> {
    val out = mutableListOf<AnnotatedString>()
    var start = 0
    for (i in indices) {
        if (this[i] == c) {
            out += subSequence(start, i)
            start = i + 1
        }
    }
    out += subSequence(start, length)
    return out
}

internal fun safeLink(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:")
}
