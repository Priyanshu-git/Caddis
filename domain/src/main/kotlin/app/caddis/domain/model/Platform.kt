package app.caddis.domain.model

import java.net.URI

/**
 * Where a save came from: the site, named the way the site names itself. There is no list of
 * services anywhere. Until a page has been read, a link's platform is its host (`chatgpt.com`);
 * once the page says what it calls itself (`og:site_name`: `ChatGPT`), that name replaces it.
 *
 * Stored as [label]. Two platforms are the same when their labels match ignoring case.
 */
class Platform private constructor(val label: String) {
    /** Lower-case key for comparing, grouping and learning. */
    val id: String = label.lowercase()

    val isNote: Boolean get() = id == NOTE_ID

    override fun equals(other: Any?): Boolean = other is Platform && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "Platform($label)"

    companion object {
        const val NOTE_ID = "note"
        private const val UNKNOWN = "web"
        private const val MAX_SITE_NAME = 40
        private val hostPrefixes = listOf("www.", "m.", "mobile.", "amp.")

        val NOTE: Platform = Platform("Note")

        /** A link before its page is read: the host, without `www.`/`m.`/`mobile.`/`amp.`. A link with no host is `web`. */
        fun fromUrl(url: String): Platform = Platform(hostOf(url).ifBlank { UNKNOWN })

        /** A platform whose name came from a page; [siteName] is already checked by [cleanSiteName]. */
        fun named(siteName: String): Platform = Platform(siteName)

        /** Rebuilds a stored platform. Anything that was stored loads; nothing is validated again. */
        fun fromStored(text: String): Platform = when {
            text.equals(NOTE_ID, ignoreCase = true) -> NOTE
            text.isBlank() -> Platform(UNKNOWN)
            else -> Platform(text)
        }

        /**
         * A name a page gave for its site, or null if it is not usable: blank, a web address, or too long
         * to be a name (a sentence of marketing copy).
         */
        fun cleanSiteName(raw: String?): String? {
            val s = raw?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            if (s.isEmpty() || s.length > MAX_SITE_NAME) return null
            if (s.contains("://") || s.contains('/') || s.none { it.isLetterOrDigit() }) return null
            return s
        }

        private fun hostOf(url: String): String {
            var host = runCatching { URI(url).host }.getOrNull()?.lowercase().orEmpty()
            for (p in hostPrefixes) {
                if (host.startsWith(p) && host.removePrefix(p).contains('.')) host = host.removePrefix(p)
            }
            return host
        }
    }
}
