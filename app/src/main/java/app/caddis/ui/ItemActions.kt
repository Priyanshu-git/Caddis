package app.caddis.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.caddis.data.LibraryRepository
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.details.formatLength
import app.caddis.domain.model.Intent as CaddisIntent
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.model.Platform
import app.caddis.domain.model.ReturnRule
import app.caddis.domain.schedule.ReturnScheduler
import app.caddis.domain.schedule.doneLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The item actions shared by Home, Library, the item page and tidy-up, each with the prototype's
 * toast and Undo. Runs on the app scope so leaving a screen never drops an action.
 */
@Singleton
class ItemActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LibraryRepository,
    private val messenger: Messenger,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {
    fun done(item: Item, intents: List<CaddisIntent>) = scope.launch {
        repo.markDone(item.id)
        messenger.show("Marked “${doneLabel(intents, item.intentId)}”.") { repo.markActive(item.id) }
    }

    fun markActive(item: Item) = scope.launch { repo.markActive(item.id) }

    fun delete(item: Item, source: AnalyticsEvent.DeleteSource = AnalyticsEvent.DeleteSource.ITEM_PAGE, toast: Boolean = true) = scope.launch {
        repo.delete(item.id, source)
        if (toast) messenger.show("Moved “${short(item.displayTitle)}” to Trash") { repo.restore(item.id) }
    }

    fun later(item: Item, rule: ReturnRule, date: LocalDate? = null) = scope.launch {
        val prevRule = item.returnRule
        val prevAt = item.returnAt
        val label = repo.bringBackLater(item.id, rule, date)
        messenger.show("Back $label.") { repo.setReturn(item.id, prevRule, prevAt) }
    }

    /** LIB-05: the original app when it is installed (Android resolves app links), otherwise the browser. */
    fun open(item: Item, entry: AnalyticsEvent.OpenEntry) {
        val url = item.url ?: return
        scope.launch { repo.markOpened(item.id, entry) }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            messenger.show("No app on this phone can open that link.")
        }
    }

    fun today(): LocalDate = clock.instant().atZone(clock.zone).toLocalDate()

    companion object {
        fun short(t: String) = if (t.length > 28) t.take(27) + "…" else t
    }
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun LocalDate.dayMonth(): String = format(DAY_MONTH)

/** "YouTube · 12 min", "Note · 28 Oct". */
fun Item.meta(clock: Clock = Clock.systemDefaultZone()): String {
    if (isNote) return "Note · " + createdAt.atZone(clock.zone).toLocalDate().dayMonth()
    val len = formatLength(lengthSeconds, kind.isVideo).ifBlank { kind.label }
    return listOf(platform.label, len).filter { it.isNotBlank() }.joinToString(" · ")
}

fun Item.savedLabel(clock: Clock = Clock.systemDefaultZone()): String =
    "Saved " + createdAt.atZone(clock.zone).toLocalDate().dayMonth()

/** The Library tag on the right of a row: done label, "Back …", or the intent. */
fun Item.rightTag(intents: List<CaddisIntent>, clock: Clock = Clock.systemDefaultZone()): String? {
    if (status == ItemStatus.DONE) return doneLabel(intents, intentId)
    val at = returnAt
    if (at != null && at.isAfter(clock.instant())) return "Back " + ReturnScheduler.label(returnRule, at, clock.zone)
    return null
}

fun intentName(intents: List<CaddisIntent>, id: String) = intents.firstOrNull { it.id == id }?.name ?: "Just keep"

fun Platform.openLabel() = if (this == Platform.WEB) "your browser" else label
