package app.caddis.domain.link

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Platform
import java.net.URI

sealed interface LinkCheck {
    /** [url] is the canonical https URL we store; [normalized] is the duplicate key (CAP-06). */
    data class Valid(val url: String, val normalized: String) : LinkCheck
    data class Invalid(val message: String) : LinkCheck
}

object Links {
    private val blockedSchemes = Regex("^(tel|mailto|sms|file|javascript|data|intent|content):", RegexOption.IGNORE_CASE)
    private val trackingParam = Regex("^(utm_.*|fbclid|igsh|igshid|si|gclid|ref|ref_src|ref_url|mc_cid|mc_eid|s|t|feature)$", RegexOption.IGNORE_CASE)
    private val urlInText = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** CAP-01/CAP-04: only http(s) web links may be saved. */
    fun check(raw: String): LinkCheck {
        val r = raw.trim()
        if (r.isEmpty()) return LinkCheck.Invalid("Paste or type a link first.")
        if (blockedSchemes.containsMatchIn(r)) return LinkCheck.Invalid("Only web links (http or https) can be saved.")
        val withScheme = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(r)) r else "https://$r"
        if (withScheme.any { it.isWhitespace() }) return LinkCheck.Invalid("That doesn't look like a web link.")
        val uri = runCatching { URI(withScheme) }.getOrNull()
            ?: return LinkCheck.Invalid("That doesn't look like a web link.")
        val host = uri.host ?: return LinkCheck.Invalid("That doesn't look like a web link.")
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) {
            return LinkCheck.Invalid("That doesn't look like a web link.")
        }
        return LinkCheck.Valid(url = withScheme, normalized = normalize(withScheme))
    }

    /** The first web link inside shared text, e.g. "Look at this https://youtu.be/x?si=1". */
    fun extractFirst(text: String): String? =
        urlInText.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')

    /**
     * Duplicate key: no scheme, no www, lower-case host, no trailing slash, tracking params dropped,
     * youtu.be and mobile hosts folded into their main host.
     */
    fun normalize(url: String): String {
        var s = url.trim().replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        s = s.substringBefore('#')
        val path = s.substringBefore('?')
        val query = if (s.contains('?')) s.substringAfter('?') else ""
        var host = path.substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
        var rest = path.drop(path.substringBefore('/').length).trimEnd('/')
        if (host == "mobile.twitter.com" || host == "twitter.com") host = "x.com"
        if (host == "youtu.be" && rest.length > 1) {
            val id = rest.drop(1)
            host = "youtube.com"
            rest = "/watch"
            return "$host$rest?v=$id"
        }
        val kept = query.split('&')
            .filter { it.isNotBlank() && !trackingParam.matches(it.substringBefore('=')) }
            .sorted()
        return host + rest + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
    }

    fun host(url: String): String =
        runCatching { URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull() ?: ""
}

data class Detection(
    val platform: Platform,
    val kind: ContentKind,
    /** Handle or community read from the URL, e.g. "@oven.notes", "r/Cooking", "u/redlentil". */
    val handle: String?,
    val host: String,
)

/** Reads platform, content kind and handle from a URL without any network call. */
object PlatformDetector {
    private val instagramReserved = setOf("reel", "reels", "p", "tv", "stories", "explore", "accounts", "direct")
    private val facebookReserved = setOf("watch", "reel", "videos", "photo", "photo.php", "story.php", "groups", "events", "share", "permalink.php")

    fun detect(url: String): Detection {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: ""
        val segments = (uri?.rawPath ?: "").split('/').filter { it.isNotBlank() }
        val first = segments.firstOrNull()?.lowercase()
        val lowerUrl = url.lowercase()

        return when {
            host.endsWith("instagram.com") -> {
                val kind = when (first) {
                    "reel", "reels", "tv" -> ContentKind.REEL
                    "p" -> ContentKind.POST
                    null -> ContentKind.PROFILE
                    else -> if (segments.getOrNull(1)?.lowercase() in setOf("reel", "p")) {
                        if (segments[1].lowercase() == "reel") ContentKind.REEL else ContentKind.POST
                    } else ContentKind.PROFILE
                }
                val handle = segments.firstOrNull()?.takeIf { it.lowercase() !in instagramReserved }?.let { "@$it" }
                Detection(Platform.INSTAGRAM, kind, handle, host)
            }
            host.endsWith("facebook.com") || host == "fb.watch" || host == "fb.com" -> {
                val kind = when {
                    first == "reel" || lowerUrl.contains("/reel/") -> ContentKind.REEL
                    host == "fb.watch" || first == "watch" || lowerUrl.contains("/videos/") -> ContentKind.VIDEO
                    else -> ContentKind.POST
                }
                val handle = segments.firstOrNull()
                    ?.takeIf { host.endsWith("facebook.com") && it.lowercase() !in facebookReserved && !it.contains('.') }
                Detection(Platform.FACEBOOK, kind, handle, host)
            }
            host.endsWith("youtube.com") || host == "youtu.be" -> {
                val kind = when {
                    first == "shorts" -> ContentKind.SHORT
                    first != null && (first.startsWith("@") || first == "channel" || first == "c" || first == "user") -> ContentKind.CHANNEL
                    else -> ContentKind.VIDEO
                }
                val handle = segments.firstOrNull()?.takeIf { it.startsWith("@") }
                Detection(Platform.YOUTUBE, kind, handle, host)
            }
            host.endsWith("reddit.com") || host == "redd.it" -> {
                val kind = if (segments.contains("comments") || host == "redd.it") ContentKind.POST else ContentKind.SUBREDDIT
                val handle = when (first) {
                    "r" -> segments.getOrNull(1)?.let { "r/$it" }
                    "u", "user" -> segments.getOrNull(1)?.let { "u/$it" }
                    else -> null
                }
                Detection(Platform.REDDIT, kind, handle, host)
            }
            host.endsWith("medium.com") -> {
                val handle = segments.firstOrNull()?.takeIf { it.startsWith("@") }
                    ?: host.removeSuffix(".medium.com").takeIf { it != "medium.com" && it.isNotBlank() }?.let { "@$it" }
                Detection(Platform.MEDIUM, ContentKind.ARTICLE, handle, host)
            }
            host == "x.com" || host.endsWith(".x.com") || host.endsWith("twitter.com") -> {
                val kind = if (segments.contains("status")) ContentKind.POST else ContentKind.PROFILE
                val handle = segments.firstOrNull()?.takeIf { it.lowercase() !in setOf("i", "home", "search", "hashtag") }?.let { "@$it" }
                Detection(Platform.X, kind, handle, host)
            }
            lowerUrl.substringBefore('?').endsWith(".pdf") -> Detection(Platform.WEB, ContentKind.PDF, null, host)
            else -> Detection(Platform.WEB, ContentKind.WEBPAGE, null, host)
        }
    }
}
