package app.caddis.data.details

import app.caddis.domain.details.DetailsParser
import app.caddis.domain.details.DetailsPlanner
import app.caddis.domain.details.FetchPlan
import app.caddis.domain.details.LinkDetails
import app.caddis.domain.model.Platform
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
 * itself is sent. Never called for Instagram or Facebook. Uses public metadata only (public oEmbed
 * endpoints and Open Graph tags): no API keys, no login, no paywall bypass, no third-party proxy
 * (NFR platform rules). Anything that fails falls back to the defaults, see [DetailsParser] and
 * `DefaultTitles`.
 */
@Singleton
class DetailsFetcher @Inject constructor(private val http: OkHttpClient) {

    /**
     * Never throws: any failure (offline, blocked, a page we cannot parse) becomes [FetchResult.Retry]
     * or [FetchResult.GiveUp], and the save keeps its defaults (MET-05).
     */
    suspend fun fetch(url: String, platform: Platform): FetchResult = withContext(Dispatchers.IO) {
        try {
            fetchFor(url, platform)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult.GiveUp
        }
    }

    private fun fetchFor(url: String, platform: Platform): FetchResult = when (val plan = DetailsPlanner.plan(platform, url)) {
        FetchPlan.Skip -> FetchResult.GiveUp
        FetchPlan.Page -> page(url, platform)
        is FetchPlan.OEmbed -> {
            val o = get(plan.endpoint, json = true)
            val fromOEmbed = (o as? Body.Ok)?.let { DetailsParser.parseOEmbed(it.text, platform) }
            val pageResult = if (plan.alsoPage) page(url, platform) else null
            val merged = merge(fromOEmbed, (pageResult as? FetchResult.Ok)?.details)
            when {
                merged != null && !merged.isEmpty -> FetchResult.Ok(merged)
                // Either source may only be having a bad moment; give up only when both say never.
                o is Body.Retry || pageResult == FetchResult.Retry -> FetchResult.Retry
                else -> FetchResult.GiveUp
            }
        }
    }

    private fun page(url: String, platform: Platform): FetchResult = when (val b = get(url, json = false)) {
        is Body.Ok -> DetailsParser.parsePage(b.text, b.finalUrl, platform).let { if (it.isEmpty) FetchResult.GiveUp else FetchResult.Ok(it) }
        Body.Retry -> FetchResult.Retry
        Body.GiveUp -> FetchResult.GiveUp
    }

    /** oEmbed wins for title/creator/thumbnail; the page fills in length and description. */
    private fun merge(a: LinkDetails?, b: LinkDetails?): LinkDetails? {
        if (a == null) return b
        if (b == null) return a
        return LinkDetails(
            title = a.title ?: b.title,
            description = a.description ?: b.description,
            creator = a.creator ?: b.creator,
            thumbnailUrl = a.thumbnailUrl ?: b.thumbnailUrl,
            lengthSeconds = a.lengthSeconds ?: b.lengthSeconds,
            faviconUrl = a.faviconUrl ?: b.faviconUrl,
        )
    }

    private sealed interface Body {
        data class Ok(val text: String, val finalUrl: String) : Body
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
