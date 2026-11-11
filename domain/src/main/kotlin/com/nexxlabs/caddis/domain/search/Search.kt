package com.nexxlabs.caddis.domain.search

import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.notes.Markdown

/**
 * SRC-02: keywords in any order, no interpretation. Each word becomes an FTS prefix term and all
 * must match (implicit AND in FTS4).
 */
object SearchQuery {
    private val token = Regex("[\\p{L}\\p{N}]+")

    fun terms(q: String): List<String> = token.findAll(q.lowercase()).map { it.value }.distinct().toList()

    /** FTS4 MATCH expression, or null when there is nothing to search for. */
    fun toFtsMatch(q: String): String? {
        val t = terms(q)
        if (t.isEmpty()) return null
        return t.joinToString(" ") { "$it*" }
    }

    /** "Matched in the title" etc. — the first field containing the first term, shown under results. */
    fun explain(item: Item, q: String): String {
        val first = terms(q).firstOrNull() ?: return ""
        val fields = listOf(
            "the title" to item.title,
            "the description" to item.description,
            "your note" to Markdown.toPlainText(item.note),
            "your reason" to item.reason,
            "the creator" to item.creator + " " + item.sourceFrom,
            "the link" to (item.url ?: ""),
            "the source" to item.sourceApp,
        )
        val hit = fields.firstOrNull { (_, v) -> terms(v).any { it.startsWith(first) } }
        return hit?.let { "Matched in ${it.first}" } ?: "Matched your words"
    }
}

/** Result-count bucket for the `search_run` event (the words are never sent). */
fun resultBucket(count: Int): String = when {
    count == 0 -> "0"
    count == 1 -> "1"
    count <= 5 -> "2-5"
    count <= 20 -> "6-20"
    else -> "21+"
}
