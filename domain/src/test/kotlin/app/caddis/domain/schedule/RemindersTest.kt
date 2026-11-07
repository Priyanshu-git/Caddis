package app.caddis.domain.schedule

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val zone = ZoneId.of("UTC")
    private val wedNoon = ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, zone)
    private val sunEvening = ZonedDateTime.of(2026, 11, 8, 19, 0, 0, 0, zone)
    private val off = ReminderState(dailyOn = false, weeklyOn = false, quiet = QuietHours.TEN_TO_SEVEN, lastDailySent = null, lastWeeklySentWeek = null)

    @Test
    fun `nothing is sent unless the person opted in`() {
        Reminders.decide(off, wedNoon, hasPick = true) shouldBe ReminderDecision.None
        Reminders.decide(off.copy(dailyOn = true), wedNoon, hasPick = true) shouldBe ReminderDecision.Daily
        Reminders.decide(off.copy(dailyOn = true), wedNoon, hasPick = false) shouldBe ReminderDecision.None
    }

    @Test
    fun `at most one a day and never in quiet hours`() {
        val on = off.copy(dailyOn = true)
        Reminders.decide(on.copy(lastDailySent = wedNoon.toLocalDate()), wedNoon, true) shouldBe ReminderDecision.None
        Reminders.decide(on, wedNoon.withHour(23), true) shouldBe ReminderDecision.None
        Reminders.decide(on, wedNoon.withHour(6), true) shouldBe ReminderDecision.None
        QuietHours.NINE_TO_EIGHT.contains(21) shouldBe true
        QuietHours.NINE_TO_EIGHT.contains(8) shouldBe false
    }

    @Test
    fun `weekly summary goes out on Sunday evening once`() {
        val weekly = off.copy(weeklyOn = true, dailyOn = true)
        Reminders.decide(weekly, sunEvening, true) shouldBe ReminderDecision.Weekly
        Reminders.decide(weekly, sunEvening.withHour(15), true) shouldBe ReminderDecision.Daily
        Reminders.decide(weekly.copy(lastWeeklySentWeek = Weeks.weekStart(sunEvening.toLocalDate())), sunEvening, true) shouldBe ReminderDecision.Daily
    }
}
