package com.nexxlabs.caddis.domain.notes

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** A note typed into a plain text box: every line and character comes back as typed, however long. */
class NotePlainTextTest {
    private fun back(text: String) = NoteMarkdown.toPlainText(NoteMarkdown.fromPlainText(text))

    @Test
    fun `new lines are kept, one per line`() {
        NoteMarkdown.fromPlainText("one\ntwo") shouldBe "one\ntwo"
        back("one\ntwo") shouldBe "one\ntwo"
    }

    @Test
    fun `empty lines are kept, including at the start and the end`() {
        back("a\n\nb") shouldBe "a\n\nb"
        back("\n\na") shouldBe "\n\na"
        back("a\n\n") shouldBe "a\n\n"
    }

    @Test
    fun `nothing stays nothing`() {
        NoteMarkdown.fromPlainText("") shouldBe ""
        NoteMarkdown.toPlainText("") shouldBe ""
    }

    @Test
    fun `windows and old mac line breaks become new lines`() {
        NoteMarkdown.fromPlainText("a\r\nb\rc") shouldBe "a\nb\nc"
    }

    @Test
    fun `characters that mean formatting are escaped and come back as typed`() {
        NoteMarkdown.fromPlainText("2*3*4 <tag> [x](y) `z` ~~s~~") shouldBe "2\\*3\\*4 \\<tag> \\[x\\](y) \\`z\\` \\~\\~s\\~\\~"
        back("2*3*4 <tag> [x](y) `z` ~~s~~ **b** _i_ a_b_c &amp; \\ #") shouldBe "2*3*4 <tag> [x](y) `z` ~~s~~ **b** _i_ a_b_c &amp; \\ #"
    }

    @Test
    fun `a line that looks like a list, heading or quote stays plain text`() {
        val text = "- one\n+ two\n* three\n1. four\n2) five\n# six\n> seven\n===\n  - indented"
        back(text) shouldBe text
        // ...and what is stored reads as no list at all.
        NoteMarkdown.toHtml(NoteMarkdown.fromPlainText("- one")) shouldBe "<p>- one</p>"
    }

    @Test
    fun `spaces and tabs at the edges of a line are kept`() {
        back("  leading\ntrailing  \n\ttab") shouldBe "  leading\ntrailing  \n\ttab"
    }

    @Test
    fun `a long note with many lines is not cut short`() {
        val text = (1..2_000).joinToString("\n") { "line $it of the note" }
        back(text) shouldBe text
    }

    @Test
    fun `a note written by the rich editor shows as plain text`() {
        NoteMarkdown.toPlainText("**bold** and *italic* and `code`") shouldBe "bold and italic and code"
        NoteMarkdown.toPlainText("- one\n- two\n  - nested") shouldBe "- one\n- two\n  - nested"
        NoteMarkdown.toPlainText("1. a\n1. b\n- c") shouldBe "1. a\n2. b\n- c"
        NoteMarkdown.toPlainText("see [the guide](https://example.com/g)") shouldBe "see the guide (https://example.com/g)"
        NoteMarkdown.toPlainText("2\\*3\\*4 \\<24>") shouldBe "2*3*4 <24>"
    }

    @Test
    fun `random text of every kind comes back unchanged`() {
        val random = Random(7)
        val pieces = listOf("a", "b", " ", "  ", "\n", "\n\n", "*", "**", "_", "~", "~~", "`", "<", ">", "&", "&amp;", "[", "]", "(", ")", "\\", "#", "-", "+", "=", "1.", "2)", "é", "😀", "\t", "<u>", "<br>", "<!---->")
        repeat(20_000) {
            val text = buildString { repeat(random.nextInt(0, 14)) { append(pieces[random.nextInt(pieces.size)]) } }
            // A line of only spaces is kept as an empty line: it holds nothing to show.
            val expected = text.split("\n").joinToString("\n") { if (it.isBlank()) "" else it }
            back(text) shouldBe expected
        }
    }
}
