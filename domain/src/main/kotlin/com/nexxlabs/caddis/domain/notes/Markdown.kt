package com.nexxlabs.caddis.domain.notes

/** Small helpers for notes stored as Markdown (NOTE-01). The editor itself lives in :app; see [NoteMarkdown]. */
object Markdown {
    private val codeFence = Regex("^```.*$", RegexOption.MULTILINE)
    private val heading = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
    private val quote = Regex("^>\\s?", RegexOption.MULTILINE)
    private val bullet = Regex("^\\s*([-*+]|\\d+\\.)\\s+", RegexOption.MULTILINE)
    // Underscores only count at a word's edge: snake_case keeps its underscores.
    private val emphasis = Regex("\\*\\*|\\*|~~|`|(?<![\\p{L}\\p{N}])_+|_+(?![\\p{L}\\p{N}])")
    private val link = Regex("\\[([^\\]]*)]\\(([^)]*)\\)")
    private val html = Regex("<[^>]+>")
    private val ws = Regex("\\s+")

    /** A backslash before punctuation means that character itself, so it is set aside while marks are stripped. */
    private val escape = Regex("\\\\([!-/:-@\\[-`{-~])")
    private const val SET_ASIDE = 0xE000

    /** Plain text for previews and search explanations. */
    fun toPlainText(md: String): String {
        if (md.isBlank()) return ""
        var s = escape.replace(md) { (SET_ASIDE + it.groupValues[1][0].code).toChar().toString() }
        s = codeFence.replace(s, "")
        s = link.replace(s) { it.groupValues[1] }
        s = heading.replace(s, "")
        s = quote.replace(s, "")
        s = bullet.replace(s, "")
        s = emphasis.replace(s, "")
        s = html.replace(s, "")
        s = ws.replace(s, " ").trim()
        return buildString(s.length) { s.forEach { c -> append(if (c.code in SET_ASIDE + 33..SET_ASIDE + 126) (c.code - SET_ASIDE).toChar() else c) } }
    }

    fun preview(md: String, max: Int = 110): String {
        val p = toPlainText(md)
        return if (p.length > max) p.take(max).trimEnd() + "…" else p
    }
}
