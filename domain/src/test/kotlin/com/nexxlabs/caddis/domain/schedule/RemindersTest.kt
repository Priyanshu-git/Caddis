package com.nexxlabs.caddis.domain.schedule

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val zone = ZoneId.of("UTC")
    private val wedNoon = ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, zone)
    private val sunEvening = ZonedDateTime.of(2026, 11, 8, 19, 0, 0, 0, zone)
    private val off = ReminderState(dailyOn = false, weeklyOn = false, lastDailySent = null, lastWeeklySentWeek = null)

    @Test
    fun `nothing is sent unless the person opted in`() {
        Reminders.decide(off, wedNoon, hasPick = true) shouldBe ReminderDecision.None
        Reminders.decide(off.copy(dailyOn = true), wedNoon, hasPick = true) shouldBe ReminderDecision.Daily
        Reminders.decide(off.copy(dailyOn = true), wedNoon, hasPick = false) shouldBe ReminderDecision.None
    }

    @Test
    fun `at most one a day`() {
        val on = off.copy(dailyOn = true)
        Reminders.decide(on.copy(lastDailySent = wedNoon.toLocalDate()), wedNoon, true) shouldBe ReminderDecision.None
        Reminders.decide(on.copy(lastDailySent = wedNoon.toLocalDate().minusDays(1)), wedNoon, true) shouldBe ReminderDecision.Daily
    }

    @Test
    fun `there are no quiet hours, so any hour of the day may send`() {
        val on = off.copy(dailyOn = true)
        (0..23).forEach { hour -> Reminders.decide(on, wedNoon.withHour(hour), true) shouldBe ReminderDecision.Daily }
    }

    @Test
    fun `the daily and weekly kinds share one budget a day`() {
        val both = off.copy(dailyOn = true, weeklyOn = true, lastDailySent = sunEvening.toLocalDate())
        Reminders.decide(both, sunEvening, true) shouldBe ReminderDecision.None
    }

    @Test
    fun `weekly summary goes out on Sunday evening once`() {
        val weekly = off.copy(weeklyOn = true, dailyOn = true)
        Reminders.decide(weekly, sunEvening, true) shouldBe ReminderDecision.Weekly
        Reminders.decide(weekly, sunEvening.withHour(15), true) shouldBe ReminderDecision.Daily
        Reminders.decide(weekly.copy(lastWeeklySentWeek = Weeks.weekStart(sunEvening.toLocalDate())), sunEvening, true) shouldBe ReminderDecision.Daily
    }
}
