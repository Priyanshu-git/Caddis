package com.nexxlabs.caddis.domain.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

data class ReminderState(
    val dailyOn: Boolean,
    val weeklyOn: Boolean,
    val lastDailySent: LocalDate?,
    val lastWeeklySentWeek: LocalDate?,
)

sealed interface ReminderDecision {
    data object None : ReminderDecision
    data object Daily : ReminderDecision
    data object Weekly : ReminderDecision
}

/**
 * Opt-in, at most one notification a day (BRB-04), and the weekly summary on Sunday evening (WK-02).
 * Calm by design: no catch-up bursts, no repeats. There are no quiet hours: the hourly check
 * sends whenever it runs and something fits.
 */
object Reminders {
    const val WEEKLY_FROM_HOUR = 18

    fun decide(state: ReminderState, now: ZonedDateTime, hasPick: Boolean): ReminderDecision {
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
