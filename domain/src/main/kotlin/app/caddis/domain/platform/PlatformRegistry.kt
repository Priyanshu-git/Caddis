package app.caddis.domain.platform

import app.caddis.domain.model.ContentKind

/** A link taken apart once, so each spec reads the same pieces. [host] has `www.`/`m.` removed. */
class UrlParts(val url: String, val host: String, val segments: List<String>) {
    val first: String? = segments.firstOrNull()?.lowercase()
    val lowerUrl: String = url.lowercase()
}

/** What a spec reads from a link without any network call. */
data class Reading(val kind: ContentKind, val handle: String? = null)

/** How details for a platform may be fetched (MET-01). The plan itself is built in `DetailsPlanner`. */
sealed interface Contact {
    /** Never contact the platform: Meta. */
    data object Never : Contact

    /** Fetch the page and read its Open Graph data. */
    data object Page : Contact

    /** A public oEmbed endpoint; `{url}` is replaced by the encoded link. Optionally read the page too. */
    data class OEmbed(val endpoint: String, val alsoPage: Boolean) : Contact
}

/**
 * A service Caddis recognises. A spec only improves what a link from that service looks like (name,
 * kind, handle, how details are fetched); a platform never needs one to exist. [read] being null means
 * "just a nicer name" (a brand label).
 */
class PlatformSpec(
    val id: String,
    val label: String,
    val hosts: List<String>,
    val contact: Contact = Contact.Page,
    /** Hosts that are never contacted or used for icons, besides [hosts] (e.g. a CDN). Only for [Contact.Never]. */
    val extraNeverContactHosts: List<String> = emptyList(),
    /** Narrows a host match, e.g. Google Docs lives under `docs.google.com/document`. */
    val accepts: (UrlParts) -> Boolean = { true },
    val read: ((UrlParts) -> Reading)? = null,
) {
    val isRich: Boolean get() = read != null || contact !is Contact.Page

    fun matches(p: UrlParts): Boolean = hosts.any { hostIs(p.host, it) } && accepts(p)
}

internal fun hostIs(host: String, domain: String) = host == domain || host.endsWith(".$domain")

/**
 * Recognised services, first match wins. Everything else is an ordinary site, named by
 * [Sites.registrableDomain]. Adding a service is one entry here plus a test; ids are stored, so never rename one.
 */
object PlatformRegistry {

    private val instagramReserved = setOf("reel", "reels", "p", "tv", "stories", "explore", "accounts", "direct")
    private val facebookReserved = setOf("watch", "reel", "videos", "photo", "photo.php", "story.php", "groups", "events", "share", "permalink.php")

    private fun instagram(p: UrlParts): Reading {
        val s = p.segments
        val kind = when (p.first) {
            "reel", "reels", "tv" -> ContentKind.REEL
            "p" -> ContentKind.POST
            null -> ContentKind.PROFILE
            else -> when (s.getOrNull(1)?.lowercase()) {
                "reel" -> ContentKind.REEL
                "p" -> ContentKind.POST
                else -> ContentKind.PROFILE
            }
        }
        val handle = s.firstOrNull()?.takeIf { it.lowercase() !in instagramReserved }?.let { "@$it" }
        return Reading(kind, handle)
    }

    private fun facebook(p: UrlParts): Reading {
        val kind = when {
            p.first == "reel" || p.lowerUrl.contains("/reel/") -> ContentKind.REEL
            p.host == "fb.watch" || p.first == "watch" || p.lowerUrl.contains("/videos/") -> ContentKind.VIDEO
            else -> ContentKind.POST
        }
        val handle = p.segments.firstOrNull()
            ?.takeIf { hostIs(p.host, "facebook.com") && it.lowercase() !in facebookReserved && !it.contains('.') }
        return Reading(kind, handle)
    }

    private fun youtube(p: UrlParts): Reading {
        val kind = when {
            p.first == "shorts" -> ContentKind.SHORT
            p.first != null && (p.first!!.startsWith("@") || p.first == "channel" || p.first == "c" || p.first == "user") -> ContentKind.CHANNEL
            else -> ContentKind.VIDEO
        }
        return Reading(kind, p.segments.firstOrNull()?.takeIf { it.startsWith("@") })
    }

    private fun reddit(p: UrlParts): Reading {
        val kind = if (p.segments.contains("comments") || p.host == "redd.it") ContentKind.POST else ContentKind.SUBREDDIT
        val handle = when (p.first) {
            "r" -> p.segments.getOrNull(1)?.let { "r/$it" }
            "u", "user" -> p.segments.getOrNull(1)?.let { "u/$it" }
            else -> null
        }
        return Reading(kind, handle)
    }

    private fun medium(p: UrlParts): Reading {
        val handle = p.segments.firstOrNull()?.takeIf { it.startsWith("@") }
            ?: p.host.removeSuffix(".medium.com").takeIf { it != "medium.com" && it.isNotBlank() }?.let { "@$it" }
        return Reading(ContentKind.ARTICLE, handle)
    }

    private fun x(p: UrlParts): Reading {
        val kind = if (p.segments.contains("status")) ContentKind.POST else ContentKind.PROFILE
        val handle = p.segments.firstOrNull()?.takeIf { it.lowercase() !in setOf("i", "home", "search", "hashtag") }?.let { "@$it" }
        return Reading(kind, handle)
    }

    private fun threads(p: UrlParts): Reading {
        val kind = if (p.segments.contains("post")) ContentKind.POST else ContentKind.PROFILE
        return Reading(kind, p.segments.firstOrNull()?.takeIf { it.startsWith("@") })
    }

    private fun drive(p: UrlParts): Reading {
        val folder = p.segments.contains("folders") || p.first == "drive"
        return Reading(if (folder) ContentKind.FOLDER else ContentKind.FILE)
    }

    private fun document(p: UrlParts) = Reading(ContentKind.DOCUMENT)

    private fun googleDocs(id: String, label: String, firstSegment: String) = PlatformSpec(
        id, label, listOf("docs.google.com"), accepts = { it.first == firstSegment }, read = ::document,
    )

    private fun brand(id: String, label: String, vararg hosts: String) = PlatformSpec(id, label, hosts.toList())

    val specs: List<PlatformSpec> = listOf(
        // Services we read the kind and handle from.
        PlatformSpec("instagram", "Instagram", listOf("instagram.com"), Contact.Never, listOf("cdninstagram.com"), read = ::instagram),
        PlatformSpec("facebook", "Facebook", listOf("facebook.com", "fb.com", "fb.watch"), Contact.Never, listOf("fbcdn.net"), read = ::facebook),
        PlatformSpec(
            "youtube", "YouTube", listOf("youtube.com", "youtu.be"),
            Contact.OEmbed("https://www.youtube.com/oembed?format=json&url={url}", alsoPage = true), read = ::youtube,
        ),
        // Reddit's public oEmbed needs no key or login; the page adds a thumbnail when it lets us in.
        PlatformSpec("reddit", "Reddit", listOf("reddit.com", "redd.it"), Contact.OEmbed("https://www.reddit.com/oembed?url={url}", alsoPage = true), read = ::reddit),
        PlatformSpec("medium", "Medium", listOf("medium.com"), read = ::medium),
        PlatformSpec(
            "x", "X", listOf("x.com", "twitter.com"),
            Contact.OEmbed("https://publish.twitter.com/oembed?omit_script=true&url={url}", alsoPage = false), read = ::x,
        ),
        // Threads is Meta's: same rule as Instagram.
        PlatformSpec("threads", "Threads", listOf("threads.net", "threads.com"), Contact.Never, read = ::threads),
        PlatformSpec("google-drive", "Google Drive", listOf("drive.google.com"), read = ::drive),
        googleDocs("google-docs", "Google Docs", "document"),
        googleDocs("google-sheets", "Google Sheets", "spreadsheets"),
        googleDocs("google-slides", "Google Slides", "presentation"),
        googleDocs("google-forms", "Google Forms", "forms"),
        // Brand names only: casing a domain cannot guess.
        PlatformSpec(
            "google-maps", "Google Maps", listOf("maps.google.com", "maps.app.goo.gl", "google.com"),
            accepts = { it.host.startsWith("maps.") || it.first == "maps" },
        ),
        brand("github", "GitHub", "github.com", "gist.github.com"),
        brand("gitlab", "GitLab", "gitlab.com"),
        brand("stackoverflow", "Stack Overflow", "stackoverflow.com"),
        brand("hackernews", "Hacker News", "news.ycombinator.com"),
        brand("spotify", "Spotify", "spotify.com", "spoti.fi"),
        brand("tiktok", "TikTok", "tiktok.com"),
        brand("linkedin", "LinkedIn", "linkedin.com", "lnkd.in"),
        brand("pinterest", "Pinterest", "pinterest.com", "pin.it"),
        brand("wikipedia", "Wikipedia", "wikipedia.org"),
        brand("substack", "Substack", "substack.com"),
        brand("notion", "Notion", "notion.so", "notion.site"),
        brand("dropbox", "Dropbox", "dropbox.com"),
        brand("vimeo", "Vimeo", "vimeo.com"),
        brand("twitch", "Twitch", "twitch.tv"),
        brand("bluesky", "Bluesky", "bsky.app"),
    )

    private val byId = specs.associateBy { it.id }

    fun byId(id: String): PlatformSpec? = byId[id]

    fun match(p: UrlParts): PlatformSpec? = specs.firstOrNull { it.matches(p) }

    private val neverContactHosts: List<String> =
        specs.filter { it.contact is Contact.Never }.flatMap { it.hosts + it.extraNeverContactHosts }

    /** Whether a host belongs to a platform we never contact (Meta), including its image CDNs. */
    fun isNeverContactHost(host: String): Boolean = neverContactHosts.any { hostIs(host.lowercase(), it) }
}

/** Naming for sites the registry does not know. */
object Sites {
    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val secondLevel = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

    /**
     * The domain a person would call the site: `blog.example.com` → `example.com`, `news.bbc.co.uk` →
     * `bbc.co.uk`. A heuristic (no public-suffix list), kept deliberately simple.
     */
    fun registrableDomain(host: String): String {
        val h = host.lowercase().removePrefix("www.").removePrefix("m.").removePrefix("mobile.").removePrefix("amp.")
        if (ipv4.matches(h)) return h
        val labels = h.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val take = if (tld.length == 2 && labels[labels.size - 2] in secondLevel) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }
}
