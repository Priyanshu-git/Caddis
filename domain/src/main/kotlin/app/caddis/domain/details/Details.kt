package app.caddis.domain.details

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.EditableField
import app.caddis.domain.model.Item
import app.caddis.domain.model.Platform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Instant

/** Details read from a source page (MET-01). Every field is optional; a save never depends on them. */
data class LinkDetails(
    val title: String? = null,
    val description: String? = null,
    val creator: String? = null,
    val thumbnailUrl: String? = null,
    val lengthSeconds: Int? = null,
    /** Not counted by [isEmpty]: an icon alone says nothing about the link. */
    val faviconUrl: String? = null,
    /** What the site calls itself (`og:site_name`), already checked by [Platform.cleanSiteName]. It becomes the save's platform. */
    val siteName: String? = null,
    /** What the page says it is (`og:type`, a PDF response). Only ever refines a plain webpage. */
    val kind: ContentKind? = null,
    /** The oEmbed endpoint the page advertises, if any (https only). Used to fetch more, never stored. */
    val oEmbedUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = listOf(title, description, creator, thumbnailUrl, siteName).all { it.isNullOrBlank() } &&
            lengthSeconds == null && kind == null
}

object DetailsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val WORDS_PER_MINUTE = 230

    /** Parse an HTML page's Open Graph / Twitter / structured data. Nothing here depends on which site it is. */
    fun parsePage(html: String, baseUrl: String): LinkDetails {
        val doc = Jsoup.parse(html, baseUrl)
        val siteName = Platform.cleanSiteName(meta(doc, "og:site_name") ?: meta(doc, "application-name"))
        val title = meta(doc, "og:title") ?: meta(doc, "twitter:title") ?: doc.title().takeIf { it.isNotBlank() }
        // A challenge page or a site-wide title says nothing about this link: no details beats wrong ones.
        // A page that only says its own name (a login wall) still tells us what the site is called.
        if (isGenericTitle(title, siteName)) {
            val bareName = siteName != null && title?.let(::clean)?.equals(siteName, ignoreCase = true) == true
            return LinkDetails(siteName = siteName.takeIf { bareName })
        }
        val description = meta(doc, "og:description") ?: meta(doc, "twitter:description") ?: meta(doc, "description")
        val image = meta(doc, "og:image:secure_url") ?: meta(doc, "og:image") ?: meta(doc, "twitter:image")
        val creator = meta(doc, "author") ?: meta(doc, "article:author")?.takeUnless { it.startsWith("http") }
            ?: doc.selectFirst("link[itemprop=name]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: meta(doc, "twitter:creator")
        return LinkDetails(
            title = title?.let(::clean),
            description = description?.let(::clean),
            creator = creator?.let(::clean),
            thumbnailUrl = image?.let { doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.ifBlank { null } ?: it },
            lengthSeconds = videoLength(doc) ?: readingTime(doc),
            faviconUrl = favicon(doc),
            siteName = siteName,
            kind = kindOf(doc),
            oEmbedUrl = oEmbedLink(doc),
        )
    }

    /** What the page says it is. Null when it says nothing useful, so the link keeps its plain-webpage kind. */
    private fun kindOf(doc: Document): ContentKind? {
        val type = meta(doc, "og:type")?.lowercase().orEmpty()
        return when {
            type.startsWith("video") || meta(doc, "twitter:card") == "player" -> ContentKind.VIDEO
            type == "article" -> ContentKind.ARTICLE
            type == "profile" -> ContentKind.PROFILE
            else -> null
        }
    }

    /** The oEmbed JSON endpoint a page advertises in its head. Only https is followed. */
    private fun oEmbedLink(doc: Document): String? =
        doc.selectFirst("link[rel=alternate][type\$=json+oembed][href]")?.absUrl("href")?.takeIf { it.startsWith("https://") }

    /**
     * The page's declared icon. The touch icon is larger, so it reads better as a thumbnail. SVG is
     * skipped because the image loader has no SVG decoder.
     */
    private fun favicon(doc: Document): String? {
        val links = doc.select("link[rel][href]").filterNot {
            it.attr("type").contains("svg", ignoreCase = true) || it.attr("href").substringBefore('?').endsWith(".svg", ignoreCase = true)
        }
        fun find(match: (List<String>) -> Boolean) = links
            .firstOrNull { match(it.attr("rel").lowercase().split(Regex("\\s+"))) }
            ?.absUrl("href")?.takeIf { it.startsWith("http") }
        return find { "apple-touch-icon" in it || "apple-touch-icon-precomposed" in it } ?: find { "icon" in it }
    }

    /** Parse a public oEmbed response, whichever site it came from. */
    fun parseOEmbed(body: String): LinkDetails {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return LinkDetails()
        fun s(k: String) = obj[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        // Text posts have no title, only an HTML snippet: use its first paragraph.
        val text = if (s("title") == null) s("html")?.let { Jsoup.parse(it).selectFirst("p")?.text() }?.takeIf { it.isNotBlank() } else null
        val author = s("author_name")
        return LinkDetails(
            title = s("title") ?: text?.let { if (it.length > 90) it.take(89).trimEnd() + "…" else it } ?: author?.let { "Post by $it" },
            description = text,
            creator = author,
            thumbnailUrl = s("thumbnail_url"),
            lengthSeconds = obj["duration"]?.jsonPrimitive?.intOrNull
                ?: text?.let { readingSecondsForWords(it.split(Regex("\\s+")).size) },
            siteName = Platform.cleanSiteName(s("provider_name")),
        )
    }

    /** ISO-8601 duration such as PT1H2M3S → seconds. */
    fun isoDurationSeconds(v: String): Int? {
        val m = Regex("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").find(v.trim()) ?: return null
        val (d, h, mi, s) = m.destructured
        val total = (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }

    /** A video's length as the page declares it: schema.org `duration` (ISO-8601) or Open Graph seconds. */
    private fun videoLength(doc: Document): Int? {
        doc.selectFirst("meta[itemprop=duration]")?.attr("content")?.let(::isoDurationSeconds)?.let { return it }
        return (meta(doc, "og:video:duration") ?: meta(doc, "video:duration"))?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun readingTime(doc: Document): Int? {
        // Medium and others say it outright: <meta name="twitter:data1" content="6 min read">
        meta(doc, "twitter:data1")?.let { Regex("(\\d+)\\s*min").find(it) }?.groupValues?.get(1)?.toIntOrNull()?.let { return it * 60 }
        val article = doc.selectFirst("article") ?: return null
        val words = article.text().split(Regex("\\s+")).count { it.isNotBlank() }
        return if (words < 120) null else readingSecondsForWords(words)
    }

    private fun readingSecondsForWords(words: Int): Int = ((words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE).coerceAtLeast(1) * 60

    private fun meta(doc: Document, key: String): String? =
        (doc.selectFirst("meta[property=$key]") ?: doc.selectFirst("meta[name=$key]"))
            ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()

    /** Bot-check and error pages whose wording is unmistakable, matched by prefix. */
    private val interstitialPrefix = Regex(
        "^(just a moment|attention required|access denied|whoa there, pardner|you['’]ve been blocked|verify(ing)? you are human|" +
            "are you a (human|robot)|robot or human|pardon our interruption|checking your browser|security check)",
        RegexOption.IGNORE_CASE,
    )

    /** Ambiguous words only count as an error page when they are the whole title ("Blocked drains" is a real title). */
    private val interstitialExact = setOf(
        "error", "blocked", "forbidden", "403", "404", "429", "403 forbidden", "404 not found",
        "page not found", "not found", "too many requests", "please wait",
    )

    private fun isGenericTitle(title: String?, siteName: String?): Boolean {
        val t = title?.let(::clean)?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        if (t in interstitialExact || interstitialPrefix.containsMatchIn(t)) return true
        // The bare site name (what a login wall or blocked page serves) is not a title for a post on that site.
        return siteName != null && t == siteName.lowercase()
    }
}

/**
 * MET-03 / principle 5: merge fetched details into an item without touching anything the person
 * edited, and never blanking a field we already had (MET-05: details survive a deleted post).
 */
fun Item.mergeDetails(d: LinkDetails, now: Instant): Item {
    fun pick(field: EditableField, current: String, fetched: String?): String =
        if (field in userEdited || fetched.isNullOrBlank()) current else fetched
    return copy(
        // The site names itself; a person cannot edit the platform, so the page's name always wins.
        platform = if (isNote || d.siteName == null) platform else Platform.named(d.siteName),
        // A page only ever refines a plain webpage (video, article, PDF); a kind we already have is kept.
        kind = if (kind == ContentKind.WEBPAGE && d.kind != null) d.kind else kind,
        title = pick(EditableField.TITLE, title, d.title),
        description = pick(EditableField.DESCRIPTION, description, d.description),
        creator = pick(EditableField.CREATOR, creator, d.creator),
        thumbnailUrl = if (EditableField.THUMBNAIL in userEdited || d.thumbnailUrl.isNullOrBlank()) thumbnailUrl else d.thumbnailUrl,
        lengthSeconds = if (EditableField.LENGTH in userEdited || d.lengthSeconds == null) lengthSeconds else d.lengthSeconds,
        faviconUrl = d.faviconUrl?.takeIf { it.isNotBlank() } ?: faviconUrl,
        detailsFetchedAt = now,
        updatedAt = now,
        revision = revision + 1,
    )
}

/** "12 min", "1 h 5 min", "6 min read". */
fun formatLength(seconds: Int?, isVideo: Boolean): String {
    if (seconds == null || seconds <= 0) return ""
    val min = (seconds + 59) / 60
    val base = if (min >= 60) "${min / 60} h" + (if (min % 60 > 0) " ${min % 60} min" else "") else "$min min"
    return if (isVideo) base else "$base read"
}
