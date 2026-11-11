package com.nexxlabs.caddis.domain.notes

/** Small helpers for notes stored as Markdown (NOTE-01). The editor itself lives in :app. */
object Markdown {
    private val codeFence = Regex("^```.*$", RegexOption.MULTILINE)
    private val heading = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
    private val quote = Regex("^>\\s?", RegexOption.MULTILINE)
    private val bullet = Regex("^\\s*([-*+]|\\d+\\.)\\s+", RegexOption.MULTILINE)
    private val emphasis = Regex("(\\*\\*|__|\\*|_|~~|`)")
    private val link = Regex("\\[([^\\]]*)]\\(([^)]*)\\)")
    private val html = Regex("<[^>]+>")
    private val ws = Regex("\\s+")

    /** Plain text for previews and search explanations. */
    fun toPlainText(md: String): String {
        if (md.isBlank()) return ""
        var s = md
        s = codeFence.replace(s, "")
        s = link.replace(s) { it.groupValues[1] }
        s = heading.replace(s, "")
        s = quote.replace(s, "")
        s = bullet.replace(s, "")
        s = emphasis.replace(s, "")
        s = html.replace(s, " ")
        return ws.replace(s, " ").trim()
    }

    fun preview(md: String, max: Int = 110): String {
        val p = toPlainText(md)
        return if (p.length > max) p.take(max).trimEnd() + "…" else p
    }
}
