package app.caddis.domain.schedule

import app.caddis.domain.model.Intent
import app.caddis.domain.model.IntentRule
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.ReturnRule
import app.caddis.domain.model.TimeOfDay
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** BRB-03: "Bring back later" options and the moment each one means. */
object ReturnScheduler {
    val TONIGHT_TIME: LocalTime = LocalTime.of(19, 0)
    val MORNING_TIME: LocalTime = LocalTime.of(9, 0)

    fun returnAt(rule: ReturnRule, now: ZonedDateTime, date: LocalDate? = null): Instant? = when (rule) {
        ReturnRule.DEFAULT, ReturnRule.NEVER -> null
        ReturnRule.TONIGHT -> {
            val tonight = now.with(TONIGHT_TIME)
            (if (now.isBefore(tonight)) tonight else tonight.plusDays(1)).toInstant()
        }
        ReturnRule.WEEKEND -> {
            val sat = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).with(MORNING_TIME)
            val sun = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(MORNING_TIME)
            // The next weekend morning still ahead: Saturday, else Sunday, else next Saturday.
            listOf(sat, sun, sat.plusWeeks(1)).filter { it.isAfter(now) }.min().toInstant()
        }
        ReturnRule.NEXT_WEEK -> now.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(MORNING_TIME).toInstant()
        ReturnRule.DATE -> {
            requireNotNull(date) { "DATE needs a date" }
            date.atTime(MORNING_TIME).atZone(now.zone).toInstant()
        }
    }

    /** Short label for the toast and the Library tag, e.g. "tonight at 7 pm", "Saturday morning". */
    fun label(rule: ReturnRule, at: Instant?, zone: ZoneId): String {
        val d = at?.atZone(zone)
        return when (rule) {
            ReturnRule.TONIGHT -> "tonight at 7 pm"
            ReturnRule.WEEKEND -> if (d?.dayOfWeek == DayOfWeek.SUNDAY) "Sunday morning" else "Saturday morning"
            ReturnRule.NEXT_WEEK -> "next Monday"
            ReturnRule.DATE -> d?.let { "on ${it.dayOfMonth} ${it.month.name.take(1)}${it.month.name.substring(1, 3).lowercase()}" } ?: ""
            ReturnRule.DEFAULT, ReturnRule.NEVER -> ""
        }
    }
}

/** A Home pick with the reason it was chosen (for screen readers and debugging, never analytics). */
data class Pick(val item: Item, val score: Int)

/**
 * BRB-01: choose at most three saves that fit now, from intent rule, time of day, day of week,
 * item length and how recently each was shown. No model; a plain scoring function.
 */
class HomePicker(private val maxPicks: Int = 3) {

    fun pick(
        items: List<Item>,
        rules: Map<String, IntentRule>,
        now: ZonedDateTime,
    ): List<Pick> {
        val nowI = now.toInstant()
        val hour = now.hour
        val today = now.dayOfWeek
        val slot = TimeOfDay.of(hour)
        val weekend = today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY

        val scored = items.asSequence()
            .filter { it.status == ItemStatus.ACTIVE && it.type == ItemType.LINK }
            .filter { it.returnRule != ReturnRule.NEVER }
            .mapNotNull { item ->
                val explicit = item.returnAt
                if (explicit != null) {
                    // BRB-02: a deferred item never reappears before its date.
                    return@mapNotNull if (!explicit.isAfter(nowI)) Pick(item, 100 + ageBonus(item, nowI)) else null
                }
                val rule = rules[item.intentId] ?: return@mapNotNull null // Just keep stays where it is
                if (today !in rule.days) return@mapNotNull null
                // Give a fresh save a day before it comes back, so Home is not just "what you saved today".
                if (Duration.between(item.createdAt, nowI) < FRESH_GRACE) return@mapNotNull null
                var score = 10
                if (rule.timeOfDay == slot) score += 6
                score += lengthFit(item.lengthSeconds, weekend, slot)
                score += ageBonus(item, nowI)
                score -= recentlyShownPenalty(item, nowI)
                Pick(item, score)
            }
            .sortedWith(compareByDescending<Pick> { it.score }.thenBy { stableTieBreak(it.item.id, now.toLocalDate()) })
            .toList()

        // maxPerDay per intent; explicit returns are not limited (the person asked for them).
        val perIntent = mutableMapOf<String, Int>()
        val out = mutableListOf<Pick>()
        for (p in scored) {
            if (out.size == maxPicks) break
            val explicit = p.item.returnAt != null
            val cap = rules[p.item.intentId]?.maxPerDay ?: 1
            val used = perIntent[p.item.intentId] ?: 0
            if (!explicit && used >= cap) continue
            perIntent[p.item.intentId] = used + 1
            out += p
        }
        return out
    }

    /** Short things fit weekday mornings and afternoons; long things fit evenings and weekends. */
    private fun lengthFit(seconds: Int?, weekend: Boolean, slot: TimeOfDay): Int {
        if (seconds == null) return 0
        val long = seconds >= 20 * 60
        return when {
            long && (weekend || slot == TimeOfDay.EVENING) -> 3
            !long && !weekend && slot != TimeOfDay.EVENING -> 2
            long -> -2
            else -> 0
        }
    }

    /** Older saves get a nudge so they don't sink forever; capped. */
    private fun ageBonus(item: Item, now: Instant): Int =
        (Duration.between(item.createdAt, now).toDays() / 7).toInt().coerceIn(0, 4)

    /** Rotate: something shown yesterday and ignored gives way to others. */
    private fun recentlyShownPenalty(item: Item, now: Instant): Int {
        val last = item.lastSurfacedAt ?: return 0
        val hours = Duration.between(last, now).toHours()
        val base = when {
            hours < 20 -> 0 // still the same day's picks: keep them stable
            hours < 48 -> 6
            hours < 24 * 7 -> 3
            else -> 0
        }
        return base + item.surfacedCount.coerceAtMost(5)
    }

    private fun stableTieBreak(id: String, day: LocalDate): Int = (id + day).hashCode()

    companion object {
        val FRESH_GRACE: Duration = Duration.ofHours(12)
    }
}

/** FIN-03: saves untouched for 90 days, oldest first. */
object TidyUp {
    val STALE_AFTER: Duration = Duration.ofDays(90)

    fun candidates(items: List<Item>, now: Instant): List<Item> = items
        .filter { it.status == ItemStatus.ACTIVE && it.returnAt?.isAfter(now) != true }
        .filter { Duration.between(it.lastTouchedAt, now) >= STALE_AFTER }
        .sortedBy { it.lastTouchedAt }
}

/** FIN-02: deleted items are purged after 30 days. */
object Trash {
    val RETENTION: Duration = Duration.ofDays(30)

    fun daysLeft(deletedAt: Instant, now: Instant): Int {
        val left = RETENTION.minus(Duration.between(deletedAt, now))
        return if (left.isNegative) 0 else ((left.toHours() + 23) / 24).toInt()
    }

    fun isExpired(deletedAt: Instant, now: Instant): Boolean = Duration.between(deletedAt, now) >= RETENTION
}

/** WK-01: Monday–Sunday weeks. */
object Weeks {
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun weekStart(at: Instant, zone: ZoneId): LocalDate = weekStart(at.atZone(zone).toLocalDate())

    /** "Mon 2 – Sun 8 Nov", or "Mon 27 Oct – Sun 2 Nov" across months. */
    fun label(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        fun mon(d: LocalDate) = d.month.name.take(1) + d.month.name.substring(1, 3).lowercase()
        return if (weekStart.month == end.month) {
            "Mon ${weekStart.dayOfMonth} – Sun ${end.dayOfMonth} ${mon(end)}"
        } else {
            "Mon ${weekStart.dayOfMonth} ${mon(weekStart)} – Sun ${end.dayOfMonth} ${mon(end)}"
        }
    }
}

/** Done-button label for an item's intent (FIN-01). */
fun doneLabel(intents: List<Intent>, intentId: String): String =
    intents.firstOrNull { it.id == intentId }?.doneLabel ?: "Done"
