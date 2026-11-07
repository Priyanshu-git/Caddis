package app.caddis.domain.analytics

import app.caddis.domain.model.Intents
import app.caddis.domain.model.Platform
import java.time.Duration
import java.time.Instant

/**
 * The only analytics events allowed (tech arch 1.3). None carries a URL, title, note, reason,
 * creator or collection name — the types make that impossible.
 */
sealed class AnalyticsEvent(val name: String) {
    abstract fun params(): Map<String, String>

    enum class SaveEntry { SHARE_SHEET, IN_APP }
    enum class OpenEntry { HOME, LIBRARY, SEARCH }
    enum class DeleteSource { ITEM_PAGE, TIDY_UP }

    data class SaveCreated(val platform: Platform, val entry: SaveEntry, val intentGuessed: Boolean) : AnalyticsEvent("save_created") {
        override fun params() = mapOf("platform" to platform.name.lowercase(), "entry" to entry.name.lowercase(), "intent_guessed" to yes(intentGuessed))
    }

    data class IntentChanged(val fromIntent: String, val toIntent: String) : AnalyticsEvent("intent_changed") {
        override fun params() = mapOf("from" to Intents.analyticsId(fromIntent), "to" to Intents.analyticsId(toIntent))
    }

    data class ItemOpened(val platform: Platform, val entry: OpenEntry) : AnalyticsEvent("item_opened") {
        override fun params() = mapOf("platform" to platform.name.lowercase(), "entry" to entry.name.lowercase())
    }

    data class ItemDone(val intent: String, val ageBucket: String) : AnalyticsEvent("item_done") {
        override fun params() = mapOf("intent" to Intents.analyticsId(intent), "age" to ageBucket)
    }

    data class ItemDeleted(val intent: String, val ageBucket: String, val source: DeleteSource) : AnalyticsEvent("item_deleted") {
        override fun params() = mapOf("intent" to Intents.analyticsId(intent), "age" to ageBucket, "source" to source.name.lowercase())
    }

    data class HomeViewed(val picksShown: Int, val actedOn: Boolean) : AnalyticsEvent("home_viewed") {
        override fun params() = mapOf("picks" to picksShown.toString(), "acted" to yes(actedOn))
    }

    data class TidyUpFinished(val kept: Int, val deleted: Int) : AnalyticsEvent("tidy_up_finished") {
        override fun params() = mapOf("kept" to kept.toString(), "deleted" to deleted.toString())
    }

    data class SummaryViewed(val fromNotification: Boolean) : AnalyticsEvent("summary_viewed") {
        override fun params() = mapOf("from_notification" to yes(fromNotification))
    }

    data class NotificationSetting(val setting: String, val on: Boolean) : AnalyticsEvent("notification_setting") {
        override fun params() = mapOf("setting" to setting, "on" to yes(on))
    }

    data class SearchRun(val resultBucket: String) : AnalyticsEvent("search_run") {
        override fun params() = mapOf("results" to resultBucket)
    }

    protected fun yes(b: Boolean) = if (b) "yes" else "no"
}

/** Port: the client-only build logs locally; Firebase Analytics plugs in here later. */
fun interface AnalyticsSink {
    fun log(event: AnalyticsEvent)
}

/** Save age, bucketed so resolution rate can be computed without exact timestamps. */
fun ageBucket(createdAt: Instant, now: Instant): String {
    val d = Duration.between(createdAt, now).toDays()
    return when {
        d < 1 -> "0d"
        d < 7 -> "1-6d"
        d < 14 -> "7-13d"
        d <= 30 -> "14-30d"
        d <= 90 -> "31-90d"
        else -> "90d+"
    }
}
