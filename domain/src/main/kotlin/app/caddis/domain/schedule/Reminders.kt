package app.caddis.domain.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** BRB-04 quiet-hour windows offered in settings. [startHour] up to (not including) [endHour], across midnight. */
enum class QuietHours(val label: String, val startHour: Int, val endHour: Int) {
    TEN_TO_SEVEN("10 pm – 7 am", 22, 7),
    ELEVEN_TO_SIX("11 pm – 6 am", 23, 6),
    NINE_TO_EIGHT("9 pm – 8 am", 21, 8);

    fun contains(hour: Int): Boolean =
        if (startHour > endHour) hour >= startHour || hour < endHour else hour in startHour until endHour

    fun next(): QuietHours = entries[(ordinal + 1) % entries.size]
}

data class ReminderState(
    val dailyOn: Boolean,
    val weeklyOn: Boolean,
    val quiet: QuietHours,
    val lastDailySent: LocalDate?,
    val lastWeeklySentWeek: LocalDate?,
)

sealed interface ReminderDecision {
    data object None : ReminderDecision
    data object Daily : ReminderDecision
    data object Weekly : ReminderDecision
}

/**
 * Opt-in, at most one notification a day, never in quiet hours (BRB-04), and the weekly summary
 * on Sunday evening (WK-02). Calm by design: no catch-up bursts, no repeats.
 */
object Reminders {
    const val WEEKLY_FROM_HOUR = 18

    fun decide(state: ReminderState, now: ZonedDateTime, hasPick: Boolean): ReminderDecision {
        if (state.quiet.contains(now.hour)) return ReminderDecision.None
        val today = now.toLocalDate()
        // The one-a-day budget is shared by both kinds.
        if (state.lastDailySent == today) return ReminderDecision.None
        val week = Weeks.weekStart(today)
        if (state.weeklyOn && now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= WEEKLY_FROM_HOUR && state.lastWeeklySentWeek != week) {
            return ReminderDecision.Weekly
        }
        if (state.dailyOn && hasPick) return ReminderDecision.Daily
        return ReminderDecision.None
    }
}
