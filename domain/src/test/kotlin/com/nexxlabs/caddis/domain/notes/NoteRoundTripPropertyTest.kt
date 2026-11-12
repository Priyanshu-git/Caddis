package com.nexxlabs.caddis.domain.notes

import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Whatever the person typed, in whatever order they switched styles on and off, saving the note and opening it again gives back
 * the same text with the same style on every character. Small cases are tried in every combination; big ones at random with a fixed seed.
 */
class NoteRoundTripPropertyTest {
    /** One character with the styles on it. */
    private data class Ch(val c: Char, val bold: Boolean = false, val italic: Boolean = false, val strike: Boolean = false, val underline: Boolean = false, val code: Boolean = false, val link: String? = null)

    private class Chunk(val text: String, val bold: Boolean, val italic: Boolean, val strike: Boolean, val underline: Boolean, val code: Boolean, val link: String?)

    private enum class Kind { PARAGRAPH, BULLET, NUMBER }

    private class Line(val kind: Kind, val depth: Int, val chunks: List<Chunk>)

    // ---------- model to the editor's html, and back from html ----------

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun chunkHtml(c: Chunk, random: Random): String {
        val tags = mutableListOf<String>()
        if (c.bold) tags += "b"
        if (c.italic) tags += "i"
        if (c.strike) tags += "s"
        if (c.underline) tags += "u"
        if (c.code) tags += "code"
        if (c.link != null) tags += "a href=\"${c.link}\""
        tags.shuffle(random) // the editor nests styles in whatever order they were switched on
        return tags.joinToString("") { "<$it>" } + esc(c.text) + tags.reversed().joinToString("") { "</${it.substringBefore(' ')}>" }
    }

    private fun editorHtml(lines: List<Line>, random: Random): String = buildString {
        val open = ArrayList<String>()
        fun closeTo(n: Int) { while (open.size > n) append("</${open.removeAt(open.lastIndex)}>") }
        for (l in lines) {
            val body = l.chunks.joinToString("") { chunkHtml(it, random) }
            if (l.kind == Kind.PARAGRAPH) {
                closeTo(0)
                append(if (l.chunks.isEmpty()) "<br>" else "<p>$body</p>")
            } else {
                val tag = if (l.kind == Kind.BULLET) "ul" else "ol"
                closeTo(l.depth)
                if (open.size == l.depth && open.last() != tag) closeTo(l.depth - 1)
                while (open.size < l.depth) { append("<$tag>"); open += tag }
                append(if (l.chunks.isEmpty()) "<li><br></li>" else "<li>$body</li>")
            }
        }
        closeTo(0)
    }

    private fun chars(chunks: List<Chunk>): List<Ch> =
        chunks.flatMap { c -> c.text.map { Ch(it, c.bold, c.italic, c.strike, c.underline, c.code, c.link) } }

    private fun readChars(node: Node, base: Ch, out: MutableList<Ch>) {
        when (node) {
            is TextNode -> node.wholeText.forEach { out += base.copy(c = it) }
            is Element -> {
                val s = when (node.tagName()) {
                    "b" -> base.copy(bold = true)
                    "i" -> base.copy(italic = true)
                    "s" -> base.copy(strike = true)
                    "u" -> base.copy(underline = true)
                    "code" -> base.copy(code = true)
                    "a" -> base.copy(link = node.attr("href"))
                    else -> base
                }
                node.childNodes().forEach { readChars(it, s, out) }
            }
        }
    }

    private class Got(val kind: Kind, val depth: Int, val chars: List<Ch>)

    private fun readBack(html: String): List<Got> {
        val out = ArrayList<Got>()
        fun inline(e: Element) = ArrayList<Ch>().also { l -> e.childNodes().forEach { readChars(it, Ch(' '), l) } }
        fun walk(nodes: List<Node>, kind: Kind, depth: Int) {
            for (n in nodes) if (n is Element) when (n.tagName()) {
                "p" -> out += Got(Kind.PARAGRAPH, 0, inline(n))
                "br" -> out += Got(Kind.PARAGRAPH, 0, emptyList())
                "ul" -> walk(n.childNodes(), Kind.BULLET, depth + 1)
                "ol" -> walk(n.childNodes(), Kind.NUMBER, depth + 1)
                "li" -> out += Got(kind, depth, inline(n))
            }
        }
        walk(Jsoup.parseBodyFragment(html).body().childNodes(), Kind.PARAGRAPH, 0)
        return out
    }

    /**
     * What a line should look like after saving: no style on spaces (a space at the edge of bold text moves outside the marks),
     * and no empty lines at the end (the editor adds one back after a trailing break).
     */
    private fun expected(lines: List<Line>): List<Got> {
        val got = lines.map { l -> Got(l.kind, l.depth, chars(l.chunks).let { cs -> if (cs.all { it.c.isWhitespace() && !it.code }) emptyList() else cs }) }
        return dropTrailingEmpty(got)
    }

    private fun dropTrailingEmpty(l: List<Got>) = l.dropLastWhile { it.chars.isEmpty() }

    private fun comparable(g: Got) = Triple(
        g.kind, g.depth,
        g.chars.map { if (it.c.isWhitespace() && !it.code) Ch(it.c, underline = it.underline, link = it.link) else it },
    )

    private fun check(lines: List<Line>, random: Random, label: String) {
        val html = editorHtml(lines, random)
        val stored = NoteMarkdown.fromHtml(html)
        val back = dropTrailingEmpty(readBack(NoteMarkdown.toHtml(stored)))
        val want = expected(lines)
        val describe = "$label\n  editor html: $html\n  stored: ${stored.replace("\n", "\\n")}\n  opened: ${NoteMarkdown.toHtml(stored)}"
        back.map(::comparable).toString() shouldBe want.map(::comparable).toString().also { if (back.map(::comparable) != want.map(::comparable)) println(describe) }
        // Saving again must not change anything.
        // (Empty lines and empty bullets at the very end are not kept when a note is opened.)
        fun withoutTail(s: String) = s.split("\n").dropLastWhile { it.isBlank() || Regex("^ *(-|\\d+\\.) ?$").matches(it) }.joinToString("\n")
        withoutTail(NoteMarkdown.fromHtml(NoteMarkdown.toHtml(stored))) shouldBe withoutTail(stored)
    }

    // ---------- every combination of a few styles ----------

    private val texts = listOf("a", " ", "*", "_x")

    @Test
    fun `every order of bold and italic switched on and off around short words comes back the same`() {
        val styles = listOf(false to false, true to false, false to true, true to true)
        val pieces = styles.flatMap { (b, i) -> texts.map { Chunk(it, b, i, false, false, false, null) } }
        val random = Random(1)
        var cases = 0
        fun extend(prefix: List<Chunk>, left: Int) {
            if (prefix.isNotEmpty()) { check(listOf(Line(Kind.PARAGRAPH, 0, prefix)), random, "chunks ${prefix.joinToString { "[${it.text} b=${it.bold} i=${it.italic}]" }}"); cases++ }
            if (left > 0) for (p in pieces) extend(prefix + p, left - 1)
        }
        extend(emptyList(), 4)
        println("tried $cases bold/italic sequences")
    }

    @Test
    fun `the steps from the bug report`() {
        // Bold on, "a"; italic on, "b"; bold off, "c"; italic off, "d".
        val chunks = listOf(
            Chunk("a", true, false, false, false, false, null), Chunk("b", true, true, false, false, false, null),
            Chunk("c", false, true, false, false, false, null), Chunk("d", false, false, false, false, false, null),
        )
        val stored = NoteMarkdown.fromHtml(editorHtml(listOf(Line(Kind.PARAGRAPH, 0, chunks)), Random(3)))
        stored shouldBe "**a*b***<!---->*c*d"
        check(listOf(Line(Kind.PARAGRAPH, 0, chunks)), Random(3), "bug report")
    }

    // ---------- random notes ----------

    private val words = listOf(
        "a", "bc", "word", " ", "  ", "\t", "*", "**", "_", "__", "`", "``", "[", "]", "(", ")", "<", ">", "<u>", "</u>", "&", "&amp;", "#", "-", "+", "1.", "2)", "=",
        "~", "~~", "\\", "\\*", "!", "|", "é", "日本", "😀", "https://x.com/a_b", "<br>", "<!---->",
    )

    private fun randomChunk(r: Random): Chunk {
        val text = (1..r.nextInt(1, 4)).joinToString("") { words[r.nextInt(words.size)] }
        fun on() = r.nextInt(3) == 0
        return Chunk(text, on(), on(), r.nextInt(6) == 0, r.nextInt(6) == 0, r.nextInt(6) == 0, if (r.nextInt(8) == 0) "https://x.com/p_q?a=1&b=2" else null)
    }

    private fun randomLines(r: Random): List<Line> {
        val lines = ArrayList<Line>()
        var depth = 0
        repeat(r.nextInt(1, 7)) {
            val kind = Kind.values()[r.nextInt(3)]
            depth = if (kind == Kind.PARAGRAPH) 0 else r.nextInt(1, minOf(depth, 3) + 2)
            val chunks = if (r.nextInt(6) == 0) emptyList() else List(r.nextInt(1, 7)) { randomChunk(r) }
            lines += Line(kind, depth, chunks)
        }
        return lines
    }

    @Test
    fun `random notes with every style, special character, list and empty line come back the same`() {
        val random = Random(20261112)
        repeat(30_000) { n -> check(randomLines(random), random, "random note #$n") }
    }

    @Test
    fun `random single lines of heavily mixed styles come back the same`() {
        val random = Random(7)
        repeat(30_000) { n -> check(listOf(Line(Kind.PARAGRAPH, 0, List(random.nextInt(1, 10)) { randomChunk(random) })), random, "random line #$n") }
    }
}
