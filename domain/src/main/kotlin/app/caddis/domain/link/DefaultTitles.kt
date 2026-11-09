package app.caddis.domain.link

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Platform
import java.net.URI
import java.net.URLDecoder

/**
 * MET-05: what to call a save when the source gave us no title: offline, blocked, a challenge page,
 * or a platform we never call (Instagram, Facebook). Built from the link and platform only, with no
 * network, and replaced by the real title the moment one arrives.
 */
object DefaultTitles {
    private const val MAX_SLUG_CHARS = 80
    private val mediumId = Regex("-[0-9a-f]{10,12}$")

    fun of(platform: Platform, kind: ContentKind, url: String): String {
        val handle = PlatformDetector.detect(url).handle
        return when (platform) {
            Platform.INSTAGRAM -> when (kind) {
                ContentKind.PROFILE -> handle?.let { "$it on Instagram" } ?: "Instagram profile"
                else -> byAuthor("Instagram ${kind.label.lowercase()}", handle)
            }
            Platform.FACEBOOK -> byAuthor("Facebook ${kind.label.lowercase()}", handle)
            Platform.REDDIT -> when (kind) {
                ContentKind.SUBREDDIT -> handle?.let { "$it on Reddit" } ?: "Reddit"
                else -> listOfNotNull(slugTitle(url, platform) ?: "Reddit post", handle).joinToString(" · ")
            }
            Platform.MEDIUM -> listOfNotNull(slugTitle(url, platform) ?: "Medium article", handle).joinToString(" · ")
            Platform.X -> when (kind) {
                ContentKind.POST -> handle?.let { "Post by $it" } ?: "Post on X"
                else -> handle?.let { "$it on X" } ?: "X"
            }
            Platform.YOUTUBE -> when (kind) {
                ContentKind.SHORT -> "YouTube short"
                ContentKind.CHANNEL -> handle?.let { "$it on YouTube" } ?: "YouTube channel"
                else -> "YouTube video"
            }
            else -> when (kind) {
                // "Google Drive file", "Google Sheets document": a service that names its own kinds.
                ContentKind.FILE, ContentKind.FOLDER, ContentKind.DOCUMENT -> "${platform.label} ${kind.label.lowercase()}"
                else -> url.removePrefix("https://").removePrefix("http://").ifBlank { "Untitled" }
            }
        }
    }

    private fun byAuthor(base: String, handle: String?) = if (handle == null) base else "$base by $handle"

    /** The readable part of a post URL: Reddit's `/comments/<id>/<slug>` or Medium's last path segment. */
    private fun slugTitle(url: String, platform: Platform): String? {
        val segments = runCatching { URI(url).rawPath }.getOrNull().orEmpty().split('/').filter { it.isNotBlank() }
        val slug = when (platform) {
            Platform.REDDIT -> segments.indexOf("comments").takeIf { it >= 0 }?.let { segments.getOrNull(it + 2) }
            Platform.MEDIUM -> segments.lastOrNull()?.takeUnless { it.startsWith("@") }
            else -> null
        } ?: return null
        return humanise(slug)
    }

    private fun humanise(slug: String): String? {
        val decoded = runCatching { URLDecoder.decode(slug, "UTF-8") }.getOrDefault(slug)
        val words = decoded.removeSuffix(".html").replace(mediumId, "").replace(Regex("[-_+]+"), " ").trim()
        if (words.length < 3 || words.all { it.isDigit() || it == ' ' }) return null
        val capped = if (words.length > MAX_SLUG_CHARS) words.take(MAX_SLUG_CHARS - 1).trimEnd() + "…" else words
        return capped.replaceFirstChar { it.uppercase() }
    }
}
