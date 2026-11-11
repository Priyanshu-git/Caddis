package com.nexxlabs.caddis.data.details

import com.nexxlabs.caddis.domain.details.DetailsParser
import com.nexxlabs.caddis.domain.details.LinkDetails
import com.nexxlabs.caddis.domain.model.ContentKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FetchResult {
    data class Ok(val details: LinkDetails) : FetchResult
    /** Worth retrying later: offline, timeout, 5xx, 429. */
    data object Retry : FetchResult
    /** Will not get better: 4xx, private, not HTML. Keep what we have (MET-05). */
    data object GiveUp : FetchResult
}

/**
 * MET-01: the phone fetches details directly from each source (tech arch decision 3). Only the link
 * itself is sent. Every link is treated the same: read the page, and if the page advertises an oEmbed
 * endpoint, read that too. Uses public metadata only (Open Graph tags and oEmbed): no API keys, no
 * login, no paywall bypass, no third-party proxy (NFR platform rules). Nothing here knows which
 * site it is talking to. Anything that fails falls back to the defaults, see [DetailsParser] and
 * `DefaultTitles`.
 */
@Singleton
class DetailsFetcher @Inject constructor(private val http: OkHttpClient) {

    /**
     * Never throws: any failure (offline, blocked, a page we cannot parse) becomes [FetchResult.Retry]
     * or [FetchResult.GiveUp], and the save keeps its defaults (MET-05).
     */
    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            fetchFor(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult.GiveUp
        }
    }

    private fun fetchFor(url: String): FetchResult {
        val body = get(url, json = false)
        if (body is Body.Pdf) return FetchResult.Ok(LinkDetails(kind = ContentKind.PDF))
        val page = (body as? Body.Ok)?.let { DetailsParser.parsePage(it.text, it.finalUrl) }
        val oEmbed = page?.oEmbedUrl?.let { get(it, json = true) }
        val fromOEmbed = (oEmbed as? Body.Ok)?.let { DetailsParser.parseOEmbed(it.text) }
        val merged = merge(fromOEmbed, page)
        return when {
            merged != null && !merged.isEmpty -> FetchResult.Ok(merged)
            // Either source may only be having a bad moment; give up only when both say never.
            body is Body.Retry || oEmbed is Body.Retry -> FetchResult.Retry
            else -> FetchResult.GiveUp
        }
    }

    /** oEmbed wins for title/creator/thumbnail; the page names the site and fills in the rest. */
    private fun merge(oEmbed: LinkDetails?, page: LinkDetails?): LinkDetails? {
        if (oEmbed == null) return page?.copy(oEmbedUrl = null)
        if (page == null) return oEmbed
        return LinkDetails(
            title = oEmbed.title ?: page.title,
            description = oEmbed.description ?: page.description,
            creator = oEmbed.creator ?: page.creator,
            thumbnailUrl = oEmbed.thumbnailUrl ?: page.thumbnailUrl,
            lengthSeconds = oEmbed.lengthSeconds ?: page.lengthSeconds,
            faviconUrl = page.faviconUrl,
            siteName = page.siteName ?: oEmbed.siteName,
            kind = page.kind ?: oEmbed.kind,
        )
    }

    private sealed interface Body {
        data class Ok(val text: String, val finalUrl: String) : Body
        data object Pdf : Body
        data object Retry : Body
        data object GiveUp : Body
    }

    private fun get(url: String, json: Boolean): Body {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", if (json) "application/json" else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en")
            .build()
        return try {
            http.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> {
                        val type = r.header("Content-Type").orEmpty()
                        if (!json && type.contains("pdf", ignoreCase = true)) return Body.Pdf
                        if (!json && !type.contains("html")) return Body.GiveUp
                        // Metadata lives in <head>; cap the read so slow 3G and huge pages stay cheap.
                        val src = r.body?.source() ?: return Body.GiveUp
                        src.request(MAX_BYTES)
                        val text = src.buffer.readUtf8(minOf(src.buffer.size, MAX_BYTES))
                        Body.Ok(text, r.request.url.toString())
                    }
                    r.code == 429 || r.code >= 500 -> Body.Retry
                    else -> Body.GiveUp
                }
            }
        } catch (e: IOException) {
            Body.Retry
        } catch (e: IllegalArgumentException) {
            Body.GiveUp
        }
    }

    companion object {
        const val MAX_BYTES = 512L * 1024
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) CaddisLinkPreview/0.1"
    }
}
