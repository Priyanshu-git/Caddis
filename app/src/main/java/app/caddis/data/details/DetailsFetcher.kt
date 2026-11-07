package app.caddis.data.details

import app.caddis.domain.details.DetailsParser
import app.caddis.domain.details.DetailsPlanner
import app.caddis.domain.details.FetchPlan
import app.caddis.domain.details.LinkDetails
import app.caddis.domain.model.Platform
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
 * itself is sent. Never called for Instagram or Facebook. Uses public metadata only; no login,
 * no paywall bypass (NFR platform rules).
 */
@Singleton
class DetailsFetcher @Inject constructor(private val http: OkHttpClient) {

    suspend fun fetch(url: String, platform: Platform): FetchResult = withContext(Dispatchers.IO) {
        when (val plan = DetailsPlanner.plan(platform, url)) {
            FetchPlan.Skip -> FetchResult.GiveUp
            FetchPlan.Page -> page(url, platform)
            is FetchPlan.OEmbed -> {
                val o = get(plan.endpoint, json = true)
                val fromOEmbed = (o as? Body.Ok)?.let { DetailsParser.parseOEmbed(it.text, platform) }
                val fromPage = if (plan.alsoPage) (page(url, platform) as? FetchResult.Ok)?.details else null
                val merged = merge(fromOEmbed, fromPage)
                when {
                    merged != null && !merged.isEmpty -> FetchResult.Ok(merged)
                    o is Body.Retry -> FetchResult.Retry
                    else -> FetchResult.GiveUp
                }
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
            .header("Accept", if (json) "application/json" else "text/html,application/xhtml+xml")
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
