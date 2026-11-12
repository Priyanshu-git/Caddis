package com.nexxlabs.caddis.domain.notes

import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

/** The editor's HTML to stored Markdown and back, with every typed character coming back as typed. */
class NoteMarkdownTest {
    private fun md(html: String) = NoteMarkdown.fromHtml(html)
    private fun html(md: String) = NoteMarkdown.toHtml(md)

    /** What the editor exports for one paragraph of [text]. */
    private fun paragraph(text: String) = "<p>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>"

    /** The text a paragraph of editor HTML holds once the editor has read it. */
    private fun textOf(html: String) = Jsoup.parseBodyFragment(html).body().wholeText()

    // ---------- paragraphs and empty lines ----------

    @Test
    fun `each paragraph is a line`() {
        md("<p>one</p><p>two</p>") shouldBe "one\ntwo"
        html("one\ntwo") shouldBe "<p>one</p><p>two</p>"
    }

    @Test
    fun `an empty paragraph is an empty line`() {
        md("<p>a</p><br><p>b</p>") shouldBe "a\n\nb"
        html("a\n\nb") shouldBe "<p>a</p><br><p>b</p>"
        md("<br><p>a</p>") shouldBe "\na"
        html("\na") shouldBe "<br><p>a</p>"
    }

    @Test
    fun `the old br marker line still reads as an empty paragraph`() {
        html("a\n\n<br>\nb") shouldBe "<p>a</p><br><br><p>b</p>"
    }

    @Test
    fun `empty lines at the very end are one fewer than written, as the editor adds one back`() {
        html("a\n") shouldBe "<p>a</p>"
        html("a\n\n") shouldBe "<p>a</p><br>"
        md("<p>a</p><br>") shouldBe "a\n"
    }

    @Test
    fun `nothing stays nothing`() {
        md("") shouldBe ""
        html("") shouldBe ""
    }

    @Test
    fun `a line of only spaces is an empty paragraph`() {
        md("<p>a</p><p>   </p><p>b</p>") shouldBe "a\n\nb"
        html("a\n   \nb") shouldBe "<p>a</p><br><p>b</p>"
    }

    @Test
    fun `windows line endings are read as line ends`() {
        html("a\r\nb") shouldBe "<p>a</p><p>b</p>"
    }

    // ---------- formatting ----------

    @Test
    fun `bold and italic are written with marks and read back`() {
        md("<p><b>bo</b>ld <i>it</i></p>") shouldBe "**bo**ld *it*"
        html("**bo**ld *it*") shouldBe "<p><b>bo</b>ld&#32;<i>it</i></p>"
    }

    @Test
    fun `bold and italic together`() {
        md("<p><b><i>both</i></b></p>") shouldBe "***both***"
        html("***both***") shouldBe "<p><b><i>both</i></b></p>"
        md("<p><b>a</b><b><i>b</i></b></p>") shouldBe "**a*b***"
        html("**a*b***") shouldBe "<p><b>a</b><b><i>b</i></b></p>"
    }

    @Test
    fun `strike, underline and code`() {
        md("<p><s>x</s> <u>y</u> <code>z</code></p>") shouldBe "~~x~~ <u>y</u> `z`"
        html("~~x~~ <u>y</u> `z`") shouldBe "<p><s>x</s>&#32;<u>y</u>&#32;<code>z</code></p>"
    }

    @Test
    fun `styles given as css are read too`() {
        md("<p><span style=\"font-weight: bold\">x</span><span style=\"font-style: italic\">y</span></p>") shouldBe "**x**<!---->*y*"
    }

    @Test
    fun `spaces at the edge of bold text go outside the marks`() {
        md("<p><b>strong </b>plain</p>") shouldBe "**strong** plain"
        md("<p>plain<b> strong</b></p>") shouldBe "plain **strong**"
        md("<p>a<i> </i>b</p>") shouldBe "a b"
    }

    @Test
    fun `links keep their text and address`() {
        md("<p><a href=\"https://x.com/a\">go</a></p>") shouldBe "[go](https://x.com/a)"
        html("[go](https://x.com/a)") shouldBe "<p><a href=\"https://x.com/a\">go</a></p>"
        md("<p><a href=\"https://x.com/a b(1)\">go</a></p>") shouldBe "[go](https://x.com/a%20b%281%29)"
        html("[go](https://x.com/a \"title\")") shouldBe "<p><a href=\"https://x.com/a\">go</a></p>"
        html("[**go**](https://x.com)") shouldBe "<p><a href=\"https://x.com\"><b>go</b></a></p>"
    }

    @Test
    fun `code with backticks or edge spaces keeps them`() {
        md("<p><code>a`b</code></p>") shouldBe "``a`b``"
        md("<p><code>`a</code></p>") shouldBe "`` `a ``"
        md("<p><code> a </code></p>") shouldBe "`  a  `"
        html("``a`b``") shouldBe "<p><code>a`b</code></p>"
        html("`  a  `") shouldBe "<p><code>&#32;a&#32;</code></p>"
    }

    // ---------- lists ----------

    @Test
    fun `bullets and numbers`() {
        md("<ul><li>a</li><li>b</li></ul>") shouldBe "- a\n- b"
        md("<ol><li>a</li><li>b</li></ol>") shouldBe "1. a\n2. b"
        html("- a\n- b") shouldBe "<ul><li>a</li><li>b</li></ul>"
        html("1. a\n2. b") shouldBe "<ol><li>a</li><li>b</li></ol>"
        html("* a\n+ b\n3) c") shouldBe "<ul><li>a</li><li>b</li></ul><ol><li>c</li></ol>"
    }

    @Test
    fun `nested lists`() {
        md("<ul><li>a</li><ul><li>b</li></ul><li>c</li></ul>") shouldBe "- a\n  - b\n- c"
        md("<ul><li>a</li><ul><li>b</li></ul></ul><ul><li>c</li></ul>") shouldBe "- a\n  - b\n- c"
        md("<ul><li>a<ul><li>b</li></ul></li></ul>") shouldBe "- a\n  - b"
        html("- a\n  - b\n- c") shouldBe "<ul><li>a</li><ul><li>b</li></ul><li>c</li></ul>"
        html("- a\n    - deep") shouldBe "<ul><li>a</li><ul><li>deep</li></ul></ul>"
    }

    @Test
    fun `numbering restarts after a paragraph and after another kind of list`() {
        md("<ol><li>a</li></ol><p>x</p><ol><li>b</li></ol>") shouldBe "1. a\nx\n1. b"
        md("<ol><li>a</li></ol><ul><li>b</li></ul><ol><li>c</li></ol>") shouldBe "1. a\n- b\n1. c"
    }

    @Test
    fun `a paragraph between lists ends the first list`() {
        html("- a\nx\n- b") shouldBe "<ul><li>a</li></ul><p>x</p><ul><li>b</li></ul>"
    }

    @Test
    fun `an empty bullet is kept`() {
        md("<ul><li>a</li><br></ul>") shouldBe "- a\n- "
        html("- a\n- \n- c") shouldBe "<ul><li>a</li><li><br></li><li>c</li></ul>"
    }

    @Test
    fun `a list item with formatting`() {
        md("<ul><li><b>a</b> b</li></ul>") shouldBe "- **a** b"
        html("- **a** b") shouldBe "<ul><li><b>a</b>&#32;b</li></ul>"
    }

    // ---------- typed characters come back as typed ----------

    @Test
    fun `typed marks are escaped`() {
        md(paragraph("2*3*4")) shouldBe "2\\*3\\*4"
        md(paragraph("<tag>")) shouldBe "\\<tag>"
        md(paragraph("a [b] `c` ~d~ e\\f")) shouldBe "a \\[b\\] \\`c\\` \\~d\\~ e\\\\f"
        html("2\\*3\\*4") shouldBe "<p>2*3*4</p>"
        html("\\<tag>") shouldBe "<p>&lt;tag&gt;</p>"
    }

    @Test
    fun `characters that start a block are escaped at the start of a line only`() {
        md(paragraph("# title")) shouldBe "\\# title"
        md(paragraph("- item")) shouldBe "\\- item"
        md(paragraph("+ item")) shouldBe "\\+ item"
        md(paragraph("> quote")) shouldBe "\\> quote"
        md(paragraph("1. step")) shouldBe "1\\. step"
        md(paragraph("2024) year")) shouldBe "2024\\) year"
        md(paragraph("---")) shouldBe "\\---"
        md(paragraph("===")) shouldBe "\\==="
        md(paragraph("  - indented")) shouldBe "  \\- indented"
        md(paragraph("-5 degrees")) shouldBe "-5 degrees"
        md(paragraph("#hashtag")) shouldBe "#hashtag"
        md(paragraph("a - b # c > d")) shouldBe "a - b # c > d"
        md(paragraph("3.5 stars")) shouldBe "3.5 stars"
    }

    @Test
    fun `underscores are escaped unless they sit inside a word`() {
        md(paragraph("snake_case_name")) shouldBe "snake_case_name"
        md(paragraph("_x_")) shouldBe "\\_x\\_"
        md(paragraph("a _ b")) shouldBe "a \\_ b"
    }

    @Test
    fun `an ampersand is escaped only when it could start an entity`() {
        md(paragraph("Tom & Jerry")) shouldBe "Tom & Jerry"
        md(paragraph("&amp; &#42; &lt;")) shouldBe "\\&amp; \\&#42; \\&lt;"
        md(paragraph("R&D; ok")) shouldBe "R\\&D; ok"
    }

    @Test
    fun `an escape before an ordinary character is a backslash`() {
        html("C:\\dir\\x") shouldBe "<p>C:\\dir\\x</p>"
        html("a\\\\b") shouldBe "<p>a\\b</p>"
    }

    @Test
    fun `marks that do not pair stay as typed`() {
        html("2 * 3 * 4") shouldBe "<p>2 * 3 * 4</p>"
        html("**oops") shouldBe "<p>**oops</p>"
        html("a ~~b") shouldBe "<p>a ~~b</p>"
        html("one `tick") shouldBe "<p>one `tick</p>"
        html("<u>unclosed") shouldBe "<p>&lt;u&gt;unclosed</p>"
        html("[no link] here") shouldBe "<p>[no link] here</p>"
        html("[half](link") shouldBe "<p>[half](link</p>"
    }

    @Test
    fun `headings and quotes are not special`() {
        html("# Title") shouldBe "<p># Title</p>"
        html("> quoted") shouldBe "<p>&gt; quoted</p>"
    }

    @Test
    fun `html in a note is text`() {
        html("<b>not bold</b>") shouldBe "<p>&lt;b&gt;not bold&lt;/b&gt;</p>"
        html("a & b &amp; c") shouldBe "<p>a &amp; b &amp;amp; c</p>"
    }

    @Test
    fun `spaces the editor would drop are kept`() {
        html("a  b") shouldBe "<p>a&#32;&#32;b</p>"
        html("  lead") shouldBe "<p>&#32;&#32;lead</p>"
        html("trail ") shouldBe "<p>trail&#32;</p>"
        html("a\tb") shouldBe "<p>a&#9;b</p>"
        md("<p>a  b</p>") shouldBe "a  b"
        md("<p>  lead</p>") shouldBe "  lead"
    }

    @Test
    fun `named entities in the editor's html are decoded`() {
        md("<p>2&midast;3&lowbar;x &lt;t&gt; &amp;amp&semi;</p>") shouldBe "2\\*3_x \\<t> \\&amp;"
    }

    private val risky = listOf(
        "2*3*4", "_a_", "**x**", "# h", "## h", "#", "- x", "-", "+ x", "1. x", "10) x", "1.", "> q", ">", "<u>x</u>", "</u>", "<br>",
        "[a](b)", "[", "]", "![i](u)", "`code`", "`", "``", "x`y", "~~s~~", "~", "\\*", "a\\b", "C:\\dir\\x", "\\", "\\\\",
        "&amp;", "&#42;", "&", "a & b", "---", "===", "***", "* * *", "*", "**", "_", "__", "snake_case_name", "  indented",
        "trail  ", "a  b", "tab\tx", "<", ">", "<tag>", "</tag>", "()", "[x]", "- ", "a - b", "(1) x", "1)x", "x. y", "  1. x",
        "**bold** and *it* and ~~s~~", "https://example.com/a_b?x=1&y=2", "50% off *today*", "a*b", "a**b", "a_b_c", "!important", "|a|b|",
    )

    @Test
    fun `any typed line comes back exactly as typed`() {
        for (t in risky) {
            val stored = md(paragraph(t))
            textOf(html(stored)) shouldBe t
            // And a second round changes nothing.
            md(html(stored)) shouldBe stored
        }
    }

    @Test
    fun `any typed line comes back as typed inside a list item and after other lines`() {
        for (t in risky) {
            val stored = md("<p>first</p><ul><li>" + paragraph(t).removePrefix("<p>").removeSuffix("</p>") + "</li></ul><p>last</p>")
            val back = Jsoup.parseBodyFragment(html(stored)).body()
            back.select("li").single().wholeText() shouldBe t
            back.select("p").map { it.wholeText() } shouldBe listOf("first", "last")
        }
    }

    @Test
    fun `typed text inside bold or italic comes back as typed`() {
        for (t in risky) {
            val stored = md("<p><b>" + paragraph(t).removePrefix("<p>").removeSuffix("</p>") + "</b> and <i>x</i></p>")
            val back = Jsoup.parseBodyFragment(html(stored)).body()
            back.select("b").single().wholeText().trim() shouldBe t.trim()
            back.select("i").single().wholeText() shouldBe "x"
        }
    }

    // ---------- round trips of stored text ----------

    @Test
    fun `stored notes survive being read and written again`() {
        val notes = listOf(
            "plain", "a\nb\nc", "a\n\nb", "\nlead", "**a** b", "*a*", "***a***", "**a*b***", "~~x~~", "<u>x</u>", "`c`",
            "[t](https://x.com)", "- a\n  - b\n- c", "1. a\n2. b", "- a\n- \n- c", "a\n- x\nb", "\\# not a heading", "2\\*3\\*4",
            "**bold** and *it* and `code` and [l](https://x.com/a)", "- **a** b\n  1. c\n  2. d\n- e",
        )
        for (n in notes) md(html(n)) shouldBe n
    }

    @Test
    fun `what earlier versions wrote still reads`() {
        // Written by the editor library: lists with "- ", blank lines, a br marker, bold and italic with * marks.
        html("**Packing**\n- passport\n- charger\n\n<br>\n*done* by ~~noon~~") shouldBe
            "<p><b>Packing</b></p><ul><li>passport</li><li>charger</li></ul><br><br><p><i>done</i>&#32;by&#32;<s>noon</s></p>"
    }
}
