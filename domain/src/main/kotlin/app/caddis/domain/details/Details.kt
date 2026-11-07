package app.caddis.domain.details

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
) {
    val isEmpty: Boolean get() = listOf(title, description, creator, thumbnailUrl).all { it.isNullOrBlank() } && lengthSeconds == null
}

/** Where to fetch details from. Instagram and Facebook are never fetched (tech arch 1.2). */
sealed interface FetchPlan {
    data object Skip : FetchPlan
    /** A public oEmbed JSON endpoint, then optionally the page itself for length. */
    data class OEmbed(val endpoint: String, val alsoPage: Boolean) : FetchPlan
    data object Page : FetchPlan
}

object DetailsPlanner {
    fun plan(platform: Platform, url: String): FetchPlan {
        val enc = java.net.URLEncoder.encode(url, "UTF-8")
        return when (platform) {
            Platform.INSTAGRAM, Platform.FACEBOOK, Platform.NOTE -> FetchPlan.Skip
            Platform.YOUTUBE -> FetchPlan.OEmbed("https://www.youtube.com/oembed?format=json&url=$enc", alsoPage = true)
            Platform.X -> FetchPlan.OEmbed("https://publish.twitter.com/oembed?omit_script=true&url=$enc", alsoPage = false)
            Platform.REDDIT, Platform.MEDIUM, Platform.WEB -> FetchPlan.Page
        }
    }
}

object DetailsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val WORDS_PER_MINUTE = 230

    /** Parse an HTML page's Open Graph / Twitter / structured data. */
    fun parsePage(html: String, baseUrl: String, platform: Platform): LinkDetails {
        val doc = Jsoup.parse(html, baseUrl)
        val title = meta(doc, "og:title") ?: meta(doc, "twitter:title") ?: doc.title().takeIf { it.isNotBlank() }
        val description = meta(doc, "og:description") ?: meta(doc, "twitter:description") ?: meta(doc, "description")
        val image = meta(doc, "og:image:secure_url") ?: meta(doc, "og:image") ?: meta(doc, "twitter:image")
        val creator = meta(doc, "author") ?: meta(doc, "article:author")?.takeUnless { it.startsWith("http") }
            ?: doc.selectFirst("link[itemprop=name]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: meta(doc, "twitter:creator") ?: meta(doc, "og:site_name")
        val length = when (platform) {
            Platform.YOUTUBE -> youtubeLength(doc, html)
            else -> readingTime(doc)
        }
        return LinkDetails(
            title = title?.let(::clean),
            description = description?.let(::clean),
            creator = creator?.let(::clean),
            thumbnailUrl = image?.let { doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.ifBlank { null } ?: it },
            lengthSeconds = length,
        )
    }

    /** Parse a public oEmbed response (YouTube, X). */
    fun parseOEmbed(body: String, platform: Platform): LinkDetails {
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return LinkDetails()
        fun s(k: String) = obj[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return when (platform) {
            Platform.X -> {
                val text = s("html")?.let { Jsoup.parse(it).selectFirst("p")?.text() }
                val author = s("author_name")
                LinkDetails(
                    title = text?.let { if (it.length > 90) it.take(89).trimEnd() + "…" else it }
                        ?: author?.let { "Post by $it" },
                    description = text,
                    creator = s("author_url")?.substringAfterLast('/')?.let { "@$it" } ?: author,
                    lengthSeconds = text?.let { readingSecondsForWords(it.split(Regex("\\s+")).size) },
                )
            }
            else -> LinkDetails(
                title = s("title"),
                creator = s("author_name"),
                thumbnailUrl = s("thumbnail_url"),
                lengthSeconds = obj["duration"]?.jsonPrimitive?.intOrNull,
            )
        }
    }

    /** ISO-8601 duration such as PT1H2M3S → seconds. */
    fun isoDurationSeconds(v: String): Int? {
        val m = Regex("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").find(v.trim()) ?: return null
        val (d, h, mi, s) = m.destructured
        val total = (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }

    private fun youtubeLength(doc: Document, html: String): Int? {
        doc.selectFirst("meta[itemprop=duration]")?.attr("content")?.let(::isoDurationSeconds)?.let { return it }
        return Regex("\"lengthSeconds\"\\s*:\\s*\"(\\d+)\"").find(html)?.groupValues?.get(1)?.toIntOrNull()
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
}

/**
 * MET-03 / principle 5: merge fetched details into an item without touching anything the person
 * edited, and never blanking a field we already had (MET-05: details survive a deleted post).
 */
fun Item.mergeDetails(d: LinkDetails, now: Instant): Item {
    fun pick(field: EditableField, current: String, fetched: String?): String =
        if (field in userEdited || fetched.isNullOrBlank()) current else fetched
    return copy(
        title = pick(EditableField.TITLE, title, d.title),
        description = pick(EditableField.DESCRIPTION, description, d.description),
        creator = pick(EditableField.CREATOR, creator, d.creator),
        thumbnailUrl = if (EditableField.THUMBNAIL in userEdited || d.thumbnailUrl.isNullOrBlank()) thumbnailUrl else d.thumbnailUrl,
        lengthSeconds = if (EditableField.LENGTH in userEdited || d.lengthSeconds == null) lengthSeconds else d.lengthSeconds,
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
