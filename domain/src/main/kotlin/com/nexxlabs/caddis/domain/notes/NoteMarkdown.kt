package com.nexxlabs.caddis.domain.notes

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Turns what the note editor holds into the Markdown stored on the item, and back (NOTE-01).
 *
 * The editor library writes Markdown without escaping what a person typed, so `2*3*4` came back as
 * italic "234" and `<tag>` vanished. This writer escapes every character that could be read as
 * formatting, and this reader honours those escapes. The editor speaks HTML (entities included), so the
 * two directions are [fromHtml] (editor to stored) and [toHtml] (stored to editor).
 *
 * The stored dialect, kept compatible with what earlier versions wrote:
 * - one line per paragraph; an empty paragraph is an empty line (a line holding only `<br>` is read the same);
 * - `- ` bullets and `1. ` numbers, nested by two spaces per level;
 * - `**bold**`, `*italic*`, `~~strike~~`, `<u>underline</u>`, `` `code` ``, `[text](url)`;
 * - a backslash before any ASCII punctuation character means that character, literally.
 * There are no headings or quotes: a `#` or `>` that is not escaped is just text.
 */
object NoteMarkdown {
    /** The editor's HTML (what it exports) as the Markdown to store. */
    fun fromHtml(html: String): String = writeMarkdown(readHtml(html))

    /** Stored Markdown as the HTML to load into the editor. */
    fun toHtml(markdown: String): String = writeHtml(readMarkdown(markdown))

    /**
     * Plain text typed into a simple text box, as the Markdown to store: one line per line, every
     * character that could be read as formatting escaped, so reading it back gives the same text.
     */
    fun fromPlainText(text: String): String =
        writeMarkdown(
            text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
                .map { line -> Block(ListKind.NONE, 0, if (line.isEmpty()) emptyList() else listOf(Run(line, Style()))) },
        )

    /**
     * Stored Markdown as the plain text a simple text box shows: marks are dropped, list markers
     * (`- `, `1. `) stay as typed, a link keeps its address in brackets, and every line break is kept.
     */
    fun toPlainText(markdown: String): String {
        val counts = HashMap<Int, Int>()
        val kinds = HashMap<Int, ListKind>()
        return readMarkdown(markdown).joinToString("\n") { b ->
            val text = b.runs.joinToString("") { r ->
                val link = r.style.link
                if (link != null && link != r.text) "${r.text} ($link)" else r.text
            }
            if (b.kind == ListKind.NONE) {
                counts.clear(); kinds.clear()
                return@joinToString text
            }
            val depth = maxOf(b.depth, 1)
            counts.keys.removeAll { it > depth }
            kinds.keys.removeAll { it > depth }
            if (kinds[depth] != b.kind) counts[depth] = 0
            kinds[depth] = b.kind
            val n = (counts[depth] ?: 0) + 1
            counts[depth] = n
            "  ".repeat(depth - 1) + (if (b.kind == ListKind.BULLET) "-" else "$n.") + " " + text
        }
    }
}

// ---------------------------------------------------------------- model

private data class Style(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

private class Run(val text: String, val style: Style)

private enum class ListKind { NONE, BULLET, NUMBER }

/** One paragraph (or list item). [depth] is 0 for a plain paragraph and 1+ inside lists. */
private class Block(val kind: ListKind, val depth: Int, val runs: List<Run>)

private fun mergeRuns(runs: List<Run>): List<Run> {
    val out = ArrayList<Run>()
    for (r in runs) {
        if (r.text.isEmpty()) continue
        val last = out.lastOrNull()
        if (last != null && last.style == r.style) out[out.lastIndex] = Run(last.text + r.text, r.style) else out += r
    }
    return out
}

private fun isBlankText(runs: List<Run>) = runs.all { !it.style.code && it.text.isBlank() }

// ---------------------------------------------------------------- editor HTML -> blocks

private val paragraphTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre")

private fun readHtml(html: String): List<Block> {
    val out = ArrayList<Block>()
    readBlocks(Jsoup.parseBodyFragment(html).body().childNodes(), ListKind.NONE, 0, out)
    return out
}

private fun readBlocks(nodes: List<Node>, kind: ListKind, depth: Int, out: MutableList<Block>) {
    val pending = ArrayList<Run>()
    fun flush() {
        if (pending.isNotEmpty()) out += Block(kind, depth, pending.toList())
        pending.clear()
    }
    for (n in nodes) {
        if (n !is Element) {
            if (n is TextNode && n.isBlank && pending.isEmpty()) continue
            inline(n, Style(), pending)
            continue
        }
        when (n.tagName().lowercase()) {
            "br" -> { flush(); out += Block(kind, depth, emptyList()) }
            "ul" -> { flush(); readBlocks(n.childNodes(), ListKind.BULLET, depth + 1, out) }
            "ol" -> { flush(); readBlocks(n.childNodes(), ListKind.NUMBER, depth + 1, out) }
            "li" -> { flush(); readItem(n, kind, depth, out) }
            in paragraphTags -> { flush(); out += Block(kind, depth, ArrayList<Run>().also { r -> n.childNodes().forEach { inline(it, Style(), r) } }) }
            else -> inline(n, Style(), pending)
        }
    }
    flush()
}

private fun readItem(li: Element, kind: ListKind, depth: Int, out: MutableList<Block>) {
    val k = if (kind == ListKind.NONE) ListKind.BULLET else kind
    val d = if (kind == ListKind.NONE) 1 else depth
    val runs = ArrayList<Run>()
    val nested = ArrayList<Element>()
    for (c in li.childNodes()) {
        if (c is Element && (c.tagName() == "ul" || c.tagName() == "ol")) nested += c else inline(c, Style(), runs)
    }
    out += Block(k, d, runs)
    for (list in nested) readBlocks(listOf(list), k, d, out)
}

private fun inline(node: Node, style: Style, out: MutableList<Run>) {
    when (node) {
        is TextNode -> if (node.wholeText.isNotEmpty()) out += Run(node.wholeText, style)
        is Element -> {
            val s = when (node.tagName().lowercase()) {
                "b", "strong" -> style.copy(bold = true)
                "i", "em" -> style.copy(italic = true)
                "s", "strike", "del" -> style.copy(strike = true)
                "u", "ins" -> style.copy(underline = true)
                "code", "code-span" -> style.copy(code = true)
                "a" -> node.attr("href").takeIf { it.isNotEmpty() }?.let { style.copy(link = it) } ?: style
                "br" -> return
                "span" -> fromCss(node.attr("style"), style)
                else -> style
            }
            node.childNodes().forEach { inline(it, s, out) }
        }
    }
}

private val cssBold = Regex("font-weight\\s*:\\s*(bold|[6-9]00)", RegexOption.IGNORE_CASE)
private val cssItalic = Regex("font-style\\s*:\\s*italic", RegexOption.IGNORE_CASE)
private val cssUnderline = Regex("text-decoration[^;]*underline", RegexOption.IGNORE_CASE)
private val cssStrike = Regex("text-decoration[^;]*line-through", RegexOption.IGNORE_CASE)

private fun fromCss(css: String, style: Style): Style {
    if (css.isEmpty()) return style
    return style.copy(
        bold = style.bold || cssBold.containsMatchIn(css),
        italic = style.italic || cssItalic.containsMatchIn(css),
        underline = style.underline || cssUnderline.containsMatchIn(css),
        strike = style.strike || cssStrike.containsMatchIn(css),
    )
}

// ---------------------------------------------------------------- blocks -> Markdown

private fun writeMarkdown(blocks: List<Block>): String {
    val lines = ArrayList<String>()
    val counts = HashMap<Int, Int>()
    val kinds = HashMap<Int, ListKind>()
    for (b in blocks) {
        val content = markdownLine(b.runs)
        if (b.kind == ListKind.NONE) {
            counts.clear(); kinds.clear()
            lines += content
            continue
        }
        val depth = maxOf(b.depth, 1)
        counts.keys.removeAll { it > depth }
        kinds.keys.removeAll { it > depth }
        if (kinds[depth] != b.kind) counts[depth] = 0
        kinds[depth] = b.kind
        val n = (counts[depth] ?: 0) + 1
        counts[depth] = n
        val marker = if (b.kind == ListKind.BULLET) "-" else "$n."
        lines += "  ".repeat(depth - 1) + marker + " " + content
    }
    return lines.joinToString("\n")
}

/** An empty HTML comment: invisible in any Markdown renderer, and it ends a run of `*` marks. */
private const val SEPARATOR = "<!---->"

private class Wrap(val key: String, val open: String, val close: String) {
    /** Bold and italic are both written with `*`, so they can be mistaken for each other when they touch. */
    val star: Boolean get() = key == "b" || key == "i"
}

/** How many runs from [index] on keep the style [key]. */
private fun lastingRuns(runs: List<Run>, index: Int, key: String): Int {
    var n = 0
    while (index + n < runs.size && wrapsFor(runs[index + n].style).any { it.key == key }) n++
    return n
}

private fun wrapsFor(s: Style): List<Wrap> = buildList {
    s.link?.let { add(Wrap("a:$it", "[", "](${linkDestination(it)})")) }
    if (s.bold) add(Wrap("b", "**", "**"))
    if (s.italic) add(Wrap("i", "*", "*"))
    if (s.strike) add(Wrap("s", "~~", "~~"))
    if (s.underline) add(Wrap("u", "<u>", "</u>"))
}

private fun linkDestination(url: String): String = buildString {
    for (c in url) when (c) {
        ' ' -> append("%20")
        '(' -> append("%28")
        ')' -> append("%29")
        '<' -> append("%3C")
        '>' -> append("%3E")
        '\\' -> append("%5C")
        else -> append(c)
    }
}

/**
 * Moves from the marks open now to the ones [want] needs for the run at [index]: closes what is no longer wanted (and whatever sits
 * inside it), then opens what is missing. A style that lasts longer goes outside one that ends sooner, so fewer marks are reopened.
 */
private fun step(stack: MutableList<Wrap>, want: List<Wrap>, runs: List<Run>, index: Int): Pair<List<Wrap>, List<Wrap>> {
    val wantKeys = want.map { it.key }.toSet()
    val bad = stack.indexOfFirst { it.key !in wantKeys }
    val closing = ArrayList<Wrap>()
    if (bad >= 0) while (stack.size > bad) closing += stack.removeAt(stack.lastIndex)
    val have = stack.map { it.key }.toSet()
    val opening = want.filter { it.key !in have }.sortedByDescending { lastingRuns(runs, index, it.key) }
    stack += opening
    return closing to opening
}

private fun runsOf(text: CharSequence, styles: List<Style>): List<Run> {
    val out = ArrayList<Run>()
    var from = 0
    while (from < styles.size) {
        var to = from
        while (to < styles.size && styles[to] == styles[from]) to++
        out += Run(text.substring(from, to), styles[from])
        from = to
    }
    return out
}

/**
 * `**`, `*` and `~~` only work as marks when they open before a letter and close after one. Where the marks would sit next to a space
 * (a style closed and reopened around it), the space loses that style, which on a space is invisible. Returns true when it changed anything.
 */
private fun clearSpacesNextToMarks(text: CharSequence, styles: MutableList<Style>, runs: List<Run>): Boolean {
    val clear = mapOf<String, Pair<(Style) -> Boolean, (Style) -> Style>>(
        "b" to ({ s: Style -> s.bold } to { s: Style -> s.copy(bold = false) }),
        "i" to ({ s: Style -> s.italic } to { s: Style -> s.copy(italic = false) }),
        "s" to ({ s: Style -> s.strike } to { s: Style -> s.copy(strike = false) }),
    )
    fun space(i: Int) = i in styles.indices && text[i].isWhitespace() && !styles[i].code
    val stack = ArrayList<Wrap>()
    var pos = 0
    var changed = false
    for (k in 0..runs.size) {
        val (closing, opening) = step(stack, if (k < runs.size) wrapsFor(runs[k].style) else emptyList(), runs, k)
        for (w in closing) {
            val (has, drop) = clear[w.key] ?: continue
            var j = pos - 1
            while (space(j) && has(styles[j])) { styles[j] = drop(styles[j]); j--; changed = true }
        }
        for (w in opening) {
            val (has, drop) = clear[w.key] ?: continue
            var j = pos
            while (space(j) && has(styles[j])) { styles[j] = drop(styles[j]); j++; changed = true }
        }
        if (changed) return true
        if (k < runs.size) pos += runs[k].text.length
    }
    return false
}

/** One paragraph's runs as a Markdown line. */
private fun markdownLine(runs: List<Run>): String {
    if (isBlankText(runs)) return ""
    val text = StringBuilder()
    val styles = ArrayList<Style>()
    for (r in runs) for (c in r.text) { text.append(c); styles += r.style }

    // Whitespace at the edge of bold, italic or strike text goes outside the marks, as Markdown requires.
    val kinds: List<Pair<(Style) -> Boolean, (Style) -> Style>> = listOf(
        { s: Style -> s.bold } to { s: Style -> s.copy(bold = false) },
        { s: Style -> s.italic } to { s: Style -> s.copy(italic = false) },
        { s: Style -> s.strike } to { s: Style -> s.copy(strike = false) },
    )
    for ((has, clear) in kinds) {
        var i = 0
        while (i < styles.size) {
            if (!has(styles[i])) { i++; continue }
            var j = i
            while (j < styles.size && has(styles[j])) j++
            var a = i
            while (a < j && text[a].isWhitespace() && !styles[a].code) { styles[a] = clear(styles[a]); a++ }
            var z = j
            while (z > a && text[z - 1].isWhitespace() && !styles[z - 1].code) { z--; styles[z] = clear(styles[z]) }
            i = j
        }
    }
    var rebuilt = runsOf(text, styles)
    // Marks that would open before a space or close after one are moved to the other side of it.
    while (clearSpacesNextToMarks(text, styles, rebuilt)) rebuilt = runsOf(text, styles)

    val full = text.toString()
    val firstText = full.indexOfFirst { !it.isWhitespace() }
    val sb = StringBuilder()
    val stack = ArrayList<Wrap>()
    var offset = 0
    for ((index, r) in rebuilt.withIndex()) {
        val (closing, opening) = step(stack, wrapsFor(r.style), rebuilt, index)
        closing.forEach { sb.append(it.close) }
        // Marks that touch (a closing * right before an opening *) cannot be told apart, so they are kept apart.
        if (closing.any { it.star } && opening.any { it.star }) sb.append(SEPARATOR)
        opening.forEach { sb.append(it.open) }
        sb.append(if (r.style.code) codeSpan(r.text) else escapeText(full, offset, offset + r.text.length, firstText))
        offset += r.text.length
    }
    while (stack.isNotEmpty()) sb.append(stack.removeAt(stack.lastIndex).close)
    return sb.toString()
}

private fun codeSpan(text: String): String {
    val longest = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
    val fence = "`".repeat(longest + 1)
    val pad = text.startsWith('`') || text.endsWith('`') || (text.startsWith(' ') && text.endsWith(' ') && text.isNotBlank())
    return if (pad) "$fence $text $fence" else "$fence$text$fence"
}

private val entityLike = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")

/** A marker such as `-` or `1.` only starts a block when a space or tab, or the end of the line, follows it. */
private fun blankOrEnd(c: Char?) = c == null || c == ' ' || c == '\t'

/** Escapes [s] from [from] until [to] so none of it can be read as formatting. [firstText] is the line's first non-space index. */
private fun escapeText(s: String, from: Int, to: Int, firstText: Int): String {
    val sb = StringBuilder()
    for (i in from until to) {
        val c = s[i]
        val next = s.getOrNull(i + 1)
        val escape = when {
            c == '\\' || c == '`' || c == '*' || c == '[' || c == ']' || c == '<' || c == '~' -> true
            c == '_' -> !(s.getOrNull(i - 1)?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true)
            c == '&' -> entityLike.matchAt(s, i) != null
            i == firstText -> when (c) {
                '#' -> blankOrEnd(next) || next == '#'
                '>' -> true
                '-', '+' -> blankOrEnd(next) || next == c
                '=' -> s.substring(i).all { it == '=' }
                else -> false
            }
            (c == '.' || c == ')') && i > firstText && i - firstText <= 9 &&
                s.substring(firstText, i).all { it.isDigit() } && blankOrEnd(next) -> true
            else -> false
        }
        if (escape) sb.append('\\')
        sb.append(c)
    }
    return sb.toString()
}

// ---------------------------------------------------------------- Markdown -> blocks

// One space after the marker belongs to it; any more is the item's own text.
private val listLine = Regex("^([ \\t]*)([-*+]|\\d{1,9}[.)])(?:[ \\t](.*))?$")

private fun readMarkdown(md: String): List<Block> {
    val blocks = ArrayList<Block>()
    var previousDepth = 0
    for (raw in md.split("\n")) {
        val line = raw.removeSuffix("\r")
        if (line.isBlank() || line.trim() == "<br>") {
            blocks += Block(ListKind.NONE, 0, emptyList())
            previousDepth = 0
            continue
        }
        val m = listLine.matchEntire(line)
        if (m == null) {
            blocks += Block(ListKind.NONE, 0, parseInline(line))
            previousDepth = 0
            continue
        }
        val indent = m.groupValues[1].fold(0) { n, ch -> n + if (ch == '\t') 2 else 1 }
        val depth = minOf(indent / 2 + 1, previousDepth + 1)
        previousDepth = depth
        val kind = if (m.groupValues[2][0].isDigit()) ListKind.NUMBER else ListKind.BULLET
        blocks += Block(kind, depth, parseInline(m.groupValues[3]))
    }
    return blocks
}

private enum class Mark { BOLD, ITALIC, STRIKE, UNDERLINE }

private sealed interface Tok {
    class Text(val s: String) : Tok
    class Code(val s: String) : Tok
    class Embedded(val runs: List<Run>) : Tok

    /** [explicit]: 0 may open or close, 1 only opens, 2 only closes. */
    class Delim(val mark: Mark, val raw: String, val canOpen: Boolean, val canClose: Boolean, val explicit: Int = 0) : Tok {
        var live = false
    }
}

private fun isAsciiPunctuation(c: Char) = c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

private fun parseInline(s: String): List<Run> {
    val toks = ArrayList<Tok>()
    val plain = StringBuilder()
    fun flush() { if (plain.isNotEmpty()) { toks += Tok.Text(plain.toString()); plain.setLength(0) } }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && isAsciiPunctuation(s[i + 1]) -> { plain.append(s[i + 1]); i += 2 }
            c == '`' -> {
                var n = 0
                while (i + n < s.length && s[i + n] == '`') n++
                val found = closingFence(s, i + n, n)
                if (found < 0) { plain.append("`".repeat(n)); i += n } else {
                    var content = s.substring(i + n, found)
                    if (content.length >= 2 && content.startsWith(' ') && content.endsWith(' ') && content.isNotBlank()) content = content.substring(1, content.length - 1)
                    flush(); toks += Tok.Code(content); i = found + n
                }
            }
            c == '*' || c == '~' -> {
                var n = 0
                while (i + n < s.length && s[i + n] == c) n++
                val before = s.getOrNull(i - 1)
                val after = s.getOrNull(i + n)
                val canOpen = after != null && !after.isWhitespace()
                val canClose = before != null && !before.isWhitespace()
                flush()
                when {
                    c == '*' && n == 1 -> toks += Tok.Delim(Mark.ITALIC, "*", canOpen, canClose)
                    c == '*' && n == 2 -> toks += Tok.Delim(Mark.BOLD, "**", canOpen, canClose)
                    c == '*' && n == 3 -> { toks += Tok.Delim(Mark.BOLD, "**", canOpen, canClose); toks += Tok.Delim(Mark.ITALIC, "*", canOpen, canClose) }
                    c == '~' && n == 2 -> toks += Tok.Delim(Mark.STRIKE, "~~", canOpen, canClose)
                    else -> toks += Tok.Text(c.toString().repeat(n))
                }
                i += n
            }
            c == '<' && s.startsWith(SEPARATOR, i) -> { flush(); i += SEPARATOR.length }
            c == '<' && s.startsWith("<u>", i, ignoreCase = true) -> { flush(); toks += Tok.Delim(Mark.UNDERLINE, s.substring(i, i + 3), true, false, 1); i += 3 }
            c == '<' && s.startsWith("</u>", i, ignoreCase = true) -> { flush(); toks += Tok.Delim(Mark.UNDERLINE, s.substring(i, i + 4), false, true, 2); i += 4 }
            c == '[' -> {
                val link = parseLink(s, i)
                if (link == null) { plain.append(c); i++ } else {
                    flush()
                    toks += Tok.Embedded(parseInline(link.label).map { Run(it.text, it.style.copy(link = link.destination)) })
                    i = link.end
                }
            }
            else -> { plain.append(c); i++ }
        }
    }
    flush()

    // A mark counts only when it pairs with another one on the same line; the rest stay as typed.
    val open = arrayOfNulls<Tok.Delim>(Mark.values().size)
    for (t in toks) {
        if (t !is Tok.Delim) continue
        val o = open[t.mark.ordinal]
        when {
            t.explicit == 1 -> if (o == null) open[t.mark.ordinal] = t
            t.explicit == 2 -> if (o != null) { o.live = true; t.live = true; open[t.mark.ordinal] = null }
            o != null && t.canClose -> { o.live = true; t.live = true; open[t.mark.ordinal] = null }
            o == null && t.canOpen -> open[t.mark.ordinal] = t
        }
    }

    val on = BooleanArray(Mark.values().size)
    fun now() = Style(bold = on[Mark.BOLD.ordinal], italic = on[Mark.ITALIC.ordinal], strike = on[Mark.STRIKE.ordinal], underline = on[Mark.UNDERLINE.ordinal])
    val runs = ArrayList<Run>()
    for (t in toks) when (t) {
        is Tok.Text -> runs += Run(t.s, now())
        is Tok.Code -> runs += Run(t.s, now().copy(code = true))
        is Tok.Embedded -> {
            val base = now()
            t.runs.forEach { runs += Run(it.text, it.style.copy(bold = it.style.bold || base.bold, italic = it.style.italic || base.italic, strike = it.style.strike || base.strike, underline = it.style.underline || base.underline)) }
        }
        is Tok.Delim -> if (t.live) on[t.mark.ordinal] = !on[t.mark.ordinal] else runs += Run(t.raw, now())
    }
    return mergeRuns(runs)
}

/** Where a run of exactly [n] backticks starts at or after [from], or -1. */
private fun closingFence(s: String, from: Int, n: Int): Int {
    var k = from
    while (k < s.length) {
        if (s[k] == '`') {
            var e = k
            while (e < s.length && s[e] == '`') e++
            if (e - k == n) return k
            k = e
        } else k++
    }
    return -1
}

private class ParsedLink(val label: String, val destination: String, val end: Int)

/** `[label](destination "title")` starting at [start], or null when it is not a complete link. */
private fun parseLink(s: String, start: Int): ParsedLink? {
    var depth = 1
    var k = start + 1
    while (k < s.length) {
        val c = s[k]
        when {
            c == '\\' && k + 1 < s.length -> k++
            // A code span binds tighter than a link, so brackets inside one do not count.
            c == '`' -> {
                var n = 0
                while (k + n < s.length && s[k + n] == '`') n++
                val close = closingFence(s, k + n, n)
                k = if (close >= 0) close + n - 1 else k + n - 1
            }
            c == '[' -> depth++
            c == ']' -> { depth--; if (depth == 0) break }
        }
        k++
    }
    if (k >= s.length || s.getOrNull(k + 1) != '(') return null
    val label = s.substring(start + 1, k)
    var p = k + 2
    val dest = StringBuilder()
    if (s.getOrNull(p) == '<') {
        p++
        while (p < s.length && s[p] != '>') { if (s[p] == '\\' && p + 1 < s.length) p++; dest.append(s[p]); p++ }
        if (p >= s.length) return null
        p++
        while (p < s.length && s[p] != ')') p++
    } else {
        var parens = 0
        while (p < s.length) {
            val c = s[p]
            if (c == '\\' && p + 1 < s.length && isAsciiPunctuation(s[p + 1])) { dest.append(s[p + 1]); p += 2; continue }
            if (c == '(') parens++
            if (c == ')') { if (parens == 0) break; parens-- }
            dest.append(c); p++
        }
    }
    if (p >= s.length || s[p] != ')') return null
    return ParsedLink(label, dest.toString().trim().substringBefore(' ').substringBefore('\t'), p + 1)
}

// ---------------------------------------------------------------- blocks -> editor HTML

private fun writeHtml(all: List<Block>): String {
    // The editor gives one more empty paragraph than there are <br> at the very end, so leave one out.
    // An empty bullet at the very end would bring an extra empty paragraph with it, so those are left out altogether.
    var blocks = all.dropLastWhile { it.kind != ListKind.NONE && isBlankText(it.runs) }
    if (blocks.isNotEmpty() && blocks.last().let { it.kind == ListKind.NONE && isBlankText(it.runs) }) blocks = blocks.dropLast(1)

    val sb = StringBuilder()
    val open = ArrayList<String>()
    fun closeTo(size: Int) { while (open.size > size) sb.append("</").append(open.removeAt(open.lastIndex)).append('>') }
    for (b in blocks) {
        if (b.kind == ListKind.NONE) {
            closeTo(0)
            if (isBlankText(b.runs)) sb.append("<br>") else sb.append("<p>").append(htmlRuns(b.runs)).append("</p>")
            continue
        }
        val tag = if (b.kind == ListKind.BULLET) "ul" else "ol"
        val depth = maxOf(b.depth, 1)
        closeTo(depth)
        if (open.size == depth && open.last() != tag) closeTo(depth - 1)
        while (open.size < depth) { sb.append('<').append(tag).append('>'); open += tag }
        sb.append("<li>").append(if (isBlankText(b.runs)) "<br>" else htmlRuns(b.runs)).append("</li>")
    }
    closeTo(0)
    return sb.toString()
}

private fun htmlRuns(runs: List<Run>): String {
    val sb = StringBuilder()
    for (r in mergeRuns(runs)) {
        val tags = ArrayList<String>()
        r.style.link?.let { tags += "a href=\"${attribute(it)}\"" }
        if (r.style.bold) tags += "b"
        if (r.style.italic) tags += "i"
        if (r.style.strike) tags += "s"
        if (r.style.underline) tags += "u"
        if (r.style.code) tags += "code"
        tags.forEach { sb.append('<').append(it).append('>') }
        sb.append(htmlText(r.text))
        tags.asReversed().forEach { sb.append("</").append(it.substringBefore(' ')).append('>') }
    }
    return sb.toString()
}

/** Text for the editor's HTML reader, which collapses spaces: those it would drop are written as character references. */
private fun htmlText(t: String): String {
    val sb = StringBuilder()
    for ((i, c) in t.withIndex()) when (c) {
        '&' -> sb.append("&amp;")
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        '\t' -> sb.append("&#9;")
        ' ' -> if (i == 0 || i == t.lastIndex || t[i - 1] == ' ' || t[i + 1] == ' ') sb.append("&#32;") else sb.append(' ')
        else -> sb.append(c)
    }
    return sb.toString()
}

private fun attribute(v: String) = v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
