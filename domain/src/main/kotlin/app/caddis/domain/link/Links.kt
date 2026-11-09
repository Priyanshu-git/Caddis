package app.caddis.domain.link

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Platform
import app.caddis.domain.platform.PlatformRegistry
import app.caddis.domain.platform.Reading
import app.caddis.domain.platform.Sites
import app.caddis.domain.platform.UrlParts
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

    /**
     * Typed text that is not a web link but is worth keeping: offer to save it as a note instead
     * (CAP-05). Empty input is just "nothing typed yet".
     */
    fun couldBeNote(raw: String): Boolean = raw.isNotBlank() && check(raw) is LinkCheck.Invalid

    /** A note's title from free text: its first non-blank line, at most 80 characters. */
    fun noteTitleFrom(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(80)

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
) {
    /**
     * Who made it, as far as the link says: the handle, else the site's domain for a site we only know
     * by its address. A recognised service without a handle has no creator to show.
     */
    val creator: String get() = handle ?: if (platform.readsLinks || platform.isNote) "" else host
}

/** Reads platform, content kind and handle from a URL without any network call. */
object PlatformDetector {
    fun detect(url: String): Detection {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: ""
        val segments = (uri?.rawPath ?: "").split('/').filter { it.isNotBlank() }
        val parts = UrlParts(url, host, segments)
        val isPdf = url.lowercase().substringBefore('?').endsWith(".pdf")

        val spec = PlatformRegistry.match(parts)
        if (spec != null) {
            val r = spec.read?.invoke(parts) ?: Reading(if (isPdf) ContentKind.PDF else ContentKind.WEBPAGE)
            return Detection(Platform.of(spec), r.kind, r.handle, host)
        }
        // An ordinary site is its own platform, named by its domain. A link with no usable host still saves.
        val domain = Sites.registrableDomain(host)
        val platform = if (domain.isBlank()) Platform.fromId("web") else Platform.site(domain)
        return Detection(platform, if (isPdf) ContentKind.PDF else ContentKind.WEBPAGE, null, host)
    }
}
