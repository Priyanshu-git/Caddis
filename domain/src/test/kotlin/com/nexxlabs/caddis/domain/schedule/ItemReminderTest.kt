package com.nexxlabs.caddis.domain.schedule

import com.nexxlabs.caddis.domain.model.ContentKind
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.Platform
import com.nexxlabs.caddis.domain.model.ReturnRule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** Sat 21 Nov 2026, 19:40. */
private val SAT_EVENING: ZonedDateTime = ZonedDateTime.of(2026, 11, 21, 19, 40, 0, 0, ZONE)

private fun at(day: Int, hour: Int, minute: Int = 0): Instant = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, ZONE).toInstant()

class ItemReminderResolveTest {
    @Test
    fun `a time later today stays today`() {
        val r = ItemReminder.resolve(LocalDate.of(2026, 11, 21), LocalTime.of(23, 0), SAT_EVENING)
        r.at shouldBe at(21, 23)
        r.movedToNextDay.shouldBeFalse()
    }

    @Test
    fun `a time that has gone by today moves to tomorrow and says so`() {
        val r = ItemReminder.resolve(LocalDate.of(2026, 11, 21), LocalTime.of(19, 0), SAT_EVENING)
        r.at shouldBe at(22, 19)
        r.movedToNextDay.shouldBeTrue()
    }

    @Test
    fun `the current minute counts as gone by, the next minute does not`() {
        val now = SAT_EVENING.withSecond(30)
        ItemReminder.resolve(LocalDate.of(2026, 11, 21), LocalTime.of(19, 40), now).movedToNextDay.shouldBeTrue()
        ItemReminder.resolve(LocalDate.of(2026, 11, 21), LocalTime.of(19, 41), now).movedToNextDay.shouldBeFalse()
    }

    @Test
    fun `a later day is never moved`() {
        val r = ItemReminder.resolve(LocalDate.of(2026, 11, 27), LocalTime.of(8, 0), SAT_EVENING)
        r.at shouldBe at(27, 8)
        r.movedToNextDay.shouldBeFalse()
    }

    @Test
    fun `the default is the next whole hour at least half an hour away`() {
        ItemReminder.defaultFor(SAT_EVENING).toInstant() shouldBe at(21, 21)
        ItemReminder.defaultFor(SAT_EVENING.withMinute(30)).toInstant() shouldBe at(21, 20)
        ItemReminder.defaultFor(SAT_EVENING.withMinute(29)).toInstant() shouldBe at(21, 20)
        ItemReminder.defaultFor(SAT_EVENING.withHour(23).withMinute(40)).toInstant() shouldBe at(22, 1)
    }

    @Test
    fun `an hour from now drops the seconds, tomorrow morning is nine`() {
        ItemReminder.inOneHour(SAT_EVENING.withSecond(41)) shouldBe at(21, 20, 40)
        ItemReminder.tomorrowMorning(SAT_EVENING) shouldBe at(22, 9)
        ItemReminder.tomorrowMorning(SAT_EVENING.withHour(0).withMinute(30)) shouldBe at(22, 9)
    }
}

class ItemReminderWordsTest {
    private val now = SAT_EVENING.toInstant()

    @Test
    fun `clock reads like people say it`() {
        ItemReminder.clock(LocalTime.of(23, 0)) shouldBe "11:00 pm"
        ItemReminder.clock(LocalTime.of(9, 5)) shouldBe "9:05 am"
        ItemReminder.clock(LocalTime.of(0, 0)) shouldBe "12:00 am"
        ItemReminder.clock(LocalTime.of(12, 30)) shouldBe "12:30 pm"
    }

    @Test
    fun `until counts minutes, hours and days`() {
        ItemReminder.until(now, now.plusSeconds(20)) shouldBe "in under a minute"
        ItemReminder.until(now, now.plusSeconds(45 * 60)) shouldBe "in 45 min"
        ItemReminder.until(now, at(21, 23)) shouldBe "in 3 h 20 min"
        ItemReminder.until(now, at(21, 22, 40)) shouldBe "in 3 h"
        ItemReminder.until(now, at(22, 19, 10)) shouldBe "in 23 h 30 min"
        ItemReminder.until(now, at(23, 19, 40)) shouldBe "in 2 days"
        ItemReminder.until(now, at(22, 19, 40)) shouldBe "in 1 day"
    }

    @Test
    fun `day words`() {
        ItemReminder.dayWord(at(21, 23), now, ZONE) shouldBe "tonight"
        ItemReminder.dayWord(at(21, 21), now, ZONE) shouldBe "tonight"
        ItemReminder.dayWord(at(22, 9), now, ZONE) shouldBe "tomorrow"
        ItemReminder.dayWord(at(24, 9), now, ZONE) shouldBe "Tue 24 Nov"
        ItemReminder.dayWord(at(21, 8), SAT_EVENING.withHour(6).toInstant(), ZONE) shouldBe "today"
    }

    @Test
    fun `phrases for the toast and tags for rows`() {
        ItemReminder.phrase(at(21, 23), now, ZONE) shouldBe "tonight at 11:00 pm"
        ItemReminder.phrase(at(22, 9), now, ZONE) shouldBe "tomorrow at 9:00 am"
        ItemReminder.phrase(at(24, 9), now, ZONE) shouldBe "on Tue 24 Nov at 9:00 am"
        ItemReminder.tag(at(21, 23), now, ZONE) shouldBe "Tonight 11:00 pm"
        ItemReminder.tag(at(22, 9), now, ZONE) shouldBe "Tomorrow 9:00 am"
        ItemReminder.tag(at(24, 9), now, ZONE) shouldBe "Tue 24 Nov, 9:00 am"
        ItemReminder.rowTag(at(21, 23), now, ZONE) shouldBe "Reminder 11:00 pm"
        ItemReminder.rowTag(at(22, 9), now, ZONE) shouldBe "Reminder tomorrow"
        ItemReminder.rowTag(at(24, 9), now, ZONE) shouldBe "Reminder 24 Nov"
    }

    @Test
    fun `the notification line fits the hour, and a late one names the time`() {
        ItemReminder.notificationText(at(21, 23), at(21, 23, 1), ZONE) shouldBe "You asked to be reminded tonight."
        ItemReminder.notificationText(at(22, 8), at(22, 8), ZONE) shouldBe "You asked to be reminded this morning."
        ItemReminder.notificationText(at(22, 14), at(22, 14, 20), ZONE) shouldBe "You asked to be reminded this afternoon."
        ItemReminder.notificationText(at(21, 23), at(22, 7), ZONE) shouldBe "You asked to be reminded at 11:00 pm."
    }
}

private fun reminder(id: String, at: Instant?, status: ItemStatus = ItemStatus.ACTIVE, rule: ReturnRule = ReturnRule.REMINDER) = Item(
    id = id, type = com.nexxlabs.caddis.domain.model.ItemType.LINK, url = "https://e.com/$id", normalizedUrl = "e.com/$id",
    platform = Platform.named("example.com"), kind = ContentKind.WEBPAGE, intentId = Intents.READ, status = status,
    createdAt = at(1, 9), returnRule = rule, returnAt = at,
)

class ReminderSweepTest {
    private val after = at(21, 20)
    private val now = at(21, 23, 1)

    @Test
    fun `due means after the last sweep and not after now, earliest first`() {
        val items = listOf(
            reminder("late", at(21, 23)),
            reminder("early", at(21, 22)),
            reminder("handled", at(21, 19)),
            reminder("future", at(22, 9)),
        )
        ReminderSweep.due(items, after, now).map { it.id }.shouldContainExactly("early", "late")
    }

    @Test
    fun `a reminder exactly at the last sweep was already handled, one exactly at now is due`() {
        ReminderSweep.due(listOf(reminder("a", after)), after, now).shouldBeEmpty()
        ReminderSweep.due(listOf(reminder("b", now)), after, now).map { it.id }.shouldContainExactly("b")
    }

    @Test
    fun `only active items with the reminder rule count`() {
        val items = listOf(
            reminder("done", at(21, 22), status = ItemStatus.DONE),
            reminder("gone", at(21, 22), status = ItemStatus.DELETED),
            reminder("later", at(21, 22), rule = ReturnRule.DATE),
            reminder("none", null),
        )
        ReminderSweep.due(items, after, now).shouldBeEmpty()
        ReminderSweep.next(items, after).shouldBeNull()
    }

    @Test
    fun `next is the earliest reminder still ahead`() {
        val items = listOf(reminder("a", at(22, 9)), reminder("b", at(21, 23, 30)), reminder("old", at(21, 10)))
        ReminderSweep.next(items, now) shouldBe at(21, 23, 30)
        ReminderSweep.next(items, at(21, 23, 30)) shouldBe at(22, 9)
    }
}

class ReminderOnHomeTest {
    private val picker = HomePicker()
    private val rules = Intents.defaultRules.associateBy { it.intentId }

    @Test
    fun `a reminder stays hidden until its time, then leads Home and is not capped`() {
        val now = ZonedDateTime.of(2026, 11, 21, 23, 1, 0, 0, ZONE)
        val items = listOf(reminder("r1", at(21, 23)), reminder("r2", at(21, 23)))
        picker.pick(items, rules, now.minusMinutes(5)).shouldBeEmpty()
        picker.pick(items, rules, now).map { it.item.id }.toSet() shouldBe setOf("r1", "r2")
        picker.pick(items + reminder("future", at(22, 9)), rules, now).map { it.item.id }.toSet() shouldBe setOf("r1", "r2")
    }

    @Test
    fun `a due reminder ranks above a dated return`() {
        val now = ZonedDateTime.of(2026, 11, 21, 23, 1, 0, 0, ZONE)
        val dated = reminder("dated", at(21, 8), rule = ReturnRule.DATE)
        val picks = picker.pick(listOf(dated, reminder("rem", at(21, 23))), rules, now)
        picks.first().item.id shouldBe "rem"
    }
}
