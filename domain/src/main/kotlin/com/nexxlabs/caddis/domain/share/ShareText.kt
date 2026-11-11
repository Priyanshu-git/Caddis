package com.nexxlabs.caddis.domain.share

import com.nexxlabs.caddis.domain.model.Item

/**
 * What the Share action sends: the title (only a real one), the link, then a sign-off. Nothing the
 * person wrote for themselves goes out: no reason, note, intent or collections ("private by default").
 *
 *     How rivers carry stones
 *     https://youtu.be/abc123
 *
 *     Shared via Caddis app
 */
object ShareText {
    const val SIGN_OFF = "Shared via Caddis app"
    const val MAX_TITLE_CHARS = 100

    /** Ids that identify who shared a link. Unlike the dedupe list, `t` (a timestamp) is kept. */
    private val trackingParam = Regex("^(utm_.*|fbclid|gclid|igsh|igshid|si|mc_cid|mc_eid)$", RegexOption.IGNORE_CASE)

    /** The full text for the share sheet, or null when the save has no link. */
    fun of(item: Item): String? {
        val url = item.url?.takeIf { it.isNotBlank() } ?: return null
        return buildString {
            title(item)?.let { append(it).append('\n') }
            append(cleanUrl(url))
            append("\n\n").append(SIGN_OFF)
        }
    }

    /** Email subject / share-sheet title: the real title only, or null. */
    fun subject(item: Item): String? = if (item.url.isNullOrBlank()) null else title(item)

    /** The saved link without tracking parameters; every other part is left exactly as saved. */
    fun cleanUrl(url: String): String {
        val fragmentAt = url.indexOf('#').let { if (it < 0) url.length else it }
        val fragment = url.substring(fragmentAt)
        val beforeFragment = url.substring(0, fragmentAt)
        val queryAt = beforeFragment.indexOf('?')
        if (queryAt < 0) return url
        val base = beforeFragment.substring(0, queryAt)
        val kept = beforeFragment.substring(queryAt + 1).split('&')
            .filter { it.isNotEmpty() && !trackingParam.matches(it.substringBefore('=')) }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }

    private fun title(item: Item): String? {
        val t = item.title.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        return if (t.length > MAX_TITLE_CHARS) t.take(MAX_TITLE_CHARS - 1).trimEnd() + "…" else t
    }
}
