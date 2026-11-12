package com.nexxlabs.caddis.ui

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.richeditor.model.RichTextState
import com.nexxlabs.caddis.domain.notes.NoteMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the real editor state does with the stored Markdown: opening a note, typing, and saving it again. */
@RunWith(RobolectricTestRunner::class)
class NoteEditorRoundTripTest {
    private fun open(stored: String) = RichTextState().also { it.setHtml(NoteMarkdown.toHtml(stored)) }
    private fun save(state: RichTextState) = NoteMarkdown.fromHtml(state.toHtml())
    private fun text(state: RichTextState) = state.annotatedString.text

    private val risky = listOf(
        "2*3*4", "_a_", "**x**", "# heading", "- item", "+ item", "1. step", "> quote", "<u>x</u>", "<tag>", "<br>",
        "[a](b)", "`code`", "~~s~~", "\\*", "C:\\dir\\x", "&amp;", "a & b", "---", "* * *", "snake_case_name",
        "a  b", "  indented", "trail  ", "tab\tx", "https://example.com/a_b?x=1&y=2", "50% off *today*", "a*b",
    )

    @Test
    fun `a typed line is saved and reopened as typed`() {
        for (t in risky) {
            val typed = RichTextState()
            typed.addTextAfterSelection(t)
            val stored = save(typed)
            assertEquals("typed: $t", t, text(typed))
            assertEquals("reopened: $t (stored as $stored)", t, text(open(stored)))
            assertEquals("saved again: $t", stored, save(open(stored)))
        }
    }

    @Test
    fun `a typed line inside bold or italic is saved and reopened as typed`() {
        for (t in listOf("2*3*4", "a_b", "<tag>", "[x](y)", "# h", "a & b")) {
            val typed = RichTextState()
            typed.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
            typed.addTextAfterSelection(t)
            typed.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
            typed.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
            typed.addTextAfterSelection(" and $t")
            val stored = save(typed)
            assertEquals("bold then italic: $t", text(typed), text(open(stored)))
        }
    }

    private val bold = SpanStyle(fontWeight = FontWeight.Bold)
    private val italic = SpanStyle(fontStyle = FontStyle.Italic)

    /** Each non-space character with the styles on it, e.g. "aB bI" (B bold, I italic, # both, c code). */
    private fun marks(s: RichTextState): String {
        val a = s.annotatedString
        return a.text.indices.filter { !a.text[it].isWhitespace() && a.text[it] != '•' }.joinToString(" ") { i ->
            fun on(f: (SpanStyle) -> Boolean) = a.spanStyles.any { it.start <= i && i < it.end && f(it.item) }
            val b = on { it.fontWeight == FontWeight.Bold }
            val it2 = on { it.fontStyle == FontStyle.Italic }
            "${a.text[i]}${if (b && it2) "#" else if (b) "B" else if (it2) "I" else "."}"
        }
    }

    private sealed interface Op {
        data object Bold : Op
        data object Italic : Op
        data class Type(val text: String) : Op
    }

    private fun play(ops: List<Op>): RichTextState = RichTextState().also { s ->
        for (op in ops) when (op) {
            Op.Bold -> s.toggleSpanStyle(bold)
            Op.Italic -> s.toggleSpanStyle(italic)
            is Op.Type -> s.addTextAfterSelection(op.text)
        }
    }

    @Test
    fun `the steps from the bug report keep their formatting`() {
        // Bold on, "a"; italic on, "b"; bold off, "c"; italic off, "d"; save; reopen.
        val typed = play(listOf(Op.Bold, Op.Type("a"), Op.Italic, Op.Type("b"), Op.Bold, Op.Type("c"), Op.Italic, Op.Type("d")))
        assertEquals("aB b# cI d.", marks(typed))
        val reopened = open(save(typed))
        assertEquals("abcd", text(reopened))
        assertEquals("aB b# cI d.", marks(reopened))
    }

    @Test
    fun `every order of switching bold and italic on and off while typing is kept`() {
        val ops = listOf(Op.Bold, Op.Italic, Op.Type("a"), Op.Type(" "), Op.Type("x_"), Op.Type("*"))
        var tried = 0
        fun extend(prefix: List<Op>, left: Int) {
            if (prefix.any { it is Op.Type }) {
                val typed = play(prefix)
                val stored = save(typed)
                val reopened = open(stored)
                val label = "ops $prefix stored as ${stored.replace("\n", "\\n")}"
                // A line of only spaces is kept as an empty line, and an empty bullet at the end is not kept.
                val typedText = text(typed)
                assertEquals(label, if (typedText.isBlank() || typedText.trim() == "•") "" else typedText, text(reopened))
                assertEquals(label, marks(typed), marks(reopened))
                // Empty lines and empty bullets at the very end are not kept when a note is opened.
                fun withoutTail(s: String) = s.split("\n").dropLastWhile { it.isBlank() || Regex("^ *(-|\\d+\\.) ?$").matches(it) }.joinToString("\n")
                assertEquals("saved again: $label", withoutTail(stored), withoutTail(save(reopened)))
                tried++
            }
            if (left > 0) for (op in ops) extend(prefix + op, left - 1)
        }
        extend(emptyList(), 6)
        println("tried $tried editor sequences")
    }

    @Test
    fun `formatting applied to a selection afterwards is kept`() {
        val words = listOf("one", "two", "three")
        for (first in 0..2) for (second in 0..2) for (style in listOf(bold, italic)) for (style2 in listOf(bold, italic)) {
            val typed = RichTextState()
            typed.addTextAfterSelection(words.joinToString(" "))
            fun range(i: Int): TextRange { val start = words.take(i).sumOf { it.length + 1 }; return TextRange(start, start + words[i].length) }
            typed.selection = range(first); typed.toggleSpanStyle(style)
            typed.selection = range(second); typed.toggleSpanStyle(style2)
            val reopened = open(save(typed))
            assertEquals("$first/$second", marks(typed), marks(reopened))
        }
    }

    @Test
    fun `bold and italic survive being saved and reopened`() {
        val state = open("**strong** and *slanted* and ***both***")
        assertEquals("strong and slanted and both", text(state))
        assertEquals("**strong** and *slanted* and ***both***", save(state))
    }

    @Test
    fun `stored notes are unchanged by opening and saving`() {
        val notes = listOf(
            "plain", "a\nb\nc", "a\n\nb", "\nlead", "a\n\n\nb", "**a** b", "*a*", "~~x~~", "<u>x</u>", "`c`",
            "[t](https://x.com)", "- a\n- b", "- a\n  - b\n- c", "1. a\n2. b", "a\n- x\nb", "\\# not a heading", "2\\*3\\*4",
            "- **a** b\n  1. c\n  2. d\n- e", "x  y", "  lead", "- a\n\nb", "1. a\n2. b\n\npara",
        )
        for (n in notes) assertEquals("note: ${n.replace("\n", "\\n")}", n, save(open(n)))
    }

    @Test
    fun `lists and empty lines show as the editor shows them`() {
        assertEquals("• a • b", text(open("- a\n- b")))
        assertEquals("1. a 2. b", text(open("1. a\n2. b")))
        assertEquals("a  b", text(open("a\n\nb")))
        assertEquals(" lead", text(open("\nlead")))
    }

    @Test
    fun `notes written by earlier versions open as before`() {
        val old = "**Packing**\n- passport\n- charger\n\n<br>\n*done* by ~~noon~~"
        val state = open(old)
        assertEquals("Packing • passport • charger   done by noon", text(state))
    }

    @Test
    fun `a trailing empty line is not added back each time the note is opened`() {
        var stored = "a\n\nb"
        repeat(3) { stored = save(open(stored)) }
        assertEquals("a\n\nb", stored)
        stored = "a\n"
        repeat(3) { stored = save(open(stored)) }
        assertEquals("a", stored.trimEnd('\n'))
    }

    @Test
    fun `an empty note opens empty`() {
        assertEquals("", text(open("")))
        assertEquals("", save(open("")))
    }
}
