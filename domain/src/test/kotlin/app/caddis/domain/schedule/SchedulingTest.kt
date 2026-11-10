package app.caddis.domain.schedule

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.IntentRule
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import app.caddis.domain.model.ReturnRule
import app.caddis.domain.model.TimeOfDay
import app.caddis.domain.model.label
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** Wed 4 Nov 2026, 11:08 — the prototype's "today". */
private val WED_MORNING: ZonedDateTime = ZonedDateTime.of(2026, 11, 4, 11, 8, 0, 0, ZONE)

private fun item(
    id: String,
    intent: String,
    daysOld: Long = 10,
    now: ZonedDateTime = WED_MORNING,
    status: ItemStatus = ItemStatus.ACTIVE,
    returnAt: Instant? = null,
    lengthSeconds: Int? = null,
    type: ItemType = ItemType.LINK,
    lastOpened: Instant? = null,
) = Item(
    id = id, type = type, url = "https://e.com/$id", normalizedUrl = "e.com/$id", platform = Platform.named("example.com"),
    kind = ContentKind.WEBPAGE, intentId = intent, status = status,
    createdAt = now.toInstant().minus(Duration.ofDays(daysOld)), returnAt = returnAt,
    returnRule = if (returnAt != null) ReturnRule.DATE else ReturnRule.DEFAULT,
    lengthSeconds = lengthSeconds, lastOpenedAt = lastOpened,
)

private val rules = Intents.defaultRules.associateBy { it.intentId }

class ReturnSchedulerTest {
    @Test
    fun `tonight is 7 pm today, or tomorrow once 7 pm has passed`() {
        ReturnScheduler.returnAt(ReturnRule.TONIGHT, WED_MORNING) shouldBe WED_MORNING.withHour(19).withMinute(0).toInstant()
        val late = WED_MORNING.withHour(21)
        ReturnScheduler.returnAt(ReturnRule.TONIGHT, late) shouldBe late.plusDays(1).withHour(19).withMinute(0).toInstant()
    }

    @Test
    fun `this weekend is the next weekend morning still ahead`() {
        val sat9 = ZonedDateTime.of(2026, 11, 7, 9, 0, 0, 0, ZONE)
        ReturnScheduler.returnAt(ReturnRule.WEEKEND, WED_MORNING) shouldBe sat9.toInstant()
        ReturnScheduler.returnAt(ReturnRule.WEEKEND, sat9.withHour(7)) shouldBe sat9.toInstant()
        ReturnScheduler.returnAt(ReturnRule.WEEKEND, sat9.withHour(12)) shouldBe sat9.plusDays(1).toInstant()
        ReturnScheduler.returnAt(ReturnRule.WEEKEND, sat9.plusDays(1).withHour(12)) shouldBe sat9.plusWeeks(1).toInstant()
    }

    @Test
    fun `next week is Monday morning, a chosen date is that morning`() {
        ReturnScheduler.returnAt(ReturnRule.NEXT_WEEK, WED_MORNING) shouldBe ZonedDateTime.of(2026, 11, 9, 9, 0, 0, 0, ZONE).toInstant()
        ReturnScheduler.returnAt(ReturnRule.DATE, WED_MORNING, LocalDate.of(2026, 11, 12)) shouldBe
            ZonedDateTime.of(2026, 11, 12, 9, 0, 0, 0, ZONE).toInstant()
        ReturnScheduler.label(ReturnRule.DATE, ZonedDateTime.of(2026, 11, 12, 9, 0, 0, 0, ZONE).toInstant(), ZONE) shouldBe "on 12 Nov"
    }
}

class HomePickerTest {
    private val picker = HomePicker()

    @Test
    fun `shows at most three`() {
        val items = (1..10).map { item("w$it", Intents.WATCH) }
        val roomy = rules + (Intents.WATCH to IntentRule(Intents.WATCH, Intents.ALL_DAYS, TimeOfDay.EVENING, 3))
        picker.pick(items, roomy, WED_MORNING.withHour(20)) shouldHaveSize 3
        HomePicker(maxPicks = 3).pick(items + (1..5).map { item("r$it", Intents.READ) }, roomy, WED_MORNING.withHour(20)) shouldHaveSize 3
    }

    @Test
    fun `only intents whose rule includes today`() {
        // Wednesday: Cook (weekends) is out, Learn (weekdays) and Read (all days) are in.
        val picks = picker.pick(listOf(item("c", Intents.COOK), item("l", Intents.LEARN), item("r", Intents.READ)), rules, WED_MORNING)
        picks.map { it.item.id }.toSet() shouldBe setOf("l", "r")
    }

    @Test
    fun `the matching time of day ranks first`() {
        val picks = picker.pick(listOf(item("r", Intents.READ), item("l", Intents.LEARN)), rules, WED_MORNING)
        picks.first().item.id shouldBe "l" // Learn returns on weekday mornings
    }

    @Test
    fun `respects max per day per intent`() {
        val items = listOf(item("l1", Intents.LEARN), item("l2", Intents.LEARN), item("l3", Intents.LEARN))
        picker.pick(items, rules, WED_MORNING) shouldHaveSize 1
        val generous = rules + (Intents.LEARN to IntentRule(Intents.LEARN, Intents.WEEKDAYS, TimeOfDay.MORNING, 2))
        picker.pick(items, generous, WED_MORNING) shouldHaveSize 2
    }

    @Test
    fun `done, deleted, notes, just keep and fresh saves stay off Home`() {
        val items = listOf(
            item("d", Intents.READ, status = ItemStatus.DONE),
            item("x", Intents.READ, status = ItemStatus.DELETED),
            item("n", Intents.READ, type = ItemType.NOTE),
            item("k", Intents.KEEP),
            item("f", Intents.READ, daysOld = 0),
        )
        picker.pick(items, rules, WED_MORNING).shouldBeEmpty()
    }

    @Test
    fun `a deferred item does not reappear before its date, then comes first`() {
        val later = WED_MORNING.plusDays(2).toInstant()
        val items = listOf(item("cook", Intents.COOK, returnAt = later), item("r", Intents.READ))
        picker.pick(items, rules, WED_MORNING).map { it.item.id } shouldContainExactly listOf("r")
        picker.pick(items, rules, WED_MORNING.plusDays(2).plusMinutes(1)).first().item.id shouldBe "cook"
    }
}

class TidyTrashWeekTest {
    @Test
    fun `tidy-up offers active saves untouched for 90 days, oldest first`() {
        val now = WED_MORNING.toInstant()
        val items = listOf(
            item("a", Intents.READ, daysOld = 120),
            item("b", Intents.READ, daysOld = 200),
            item("c", Intents.READ, daysOld = 30),
            item("d", Intents.READ, daysOld = 200, lastOpened = now.minus(Duration.ofDays(5))),
            item("e", Intents.READ, daysOld = 200, status = ItemStatus.DONE),
        )
        TidyUp.candidates(items, now).map { it.id } shouldContainExactly listOf("b", "a")
    }

    @Test
    fun `trash keeps things for 30 days`() {
        val now = WED_MORNING.toInstant()
        Trash.daysLeft(now, now) shouldBe 30
        Trash.daysLeft(now.minus(Duration.ofDays(26)), now) shouldBe 4
        Trash.isExpired(now.minus(Duration.ofDays(30)), now) shouldBe true
        Trash.isExpired(now.minus(Duration.ofDays(29)), now) shouldBe false
    }

    @Test
    fun `weeks run Monday to Sunday`() {
        Weeks.weekStart(LocalDate.of(2026, 11, 4)) shouldBe LocalDate.of(2026, 11, 2)
        Weeks.weekStart(LocalDate.of(2026, 11, 8)) shouldBe LocalDate.of(2026, 11, 2)
        Weeks.weekStart(LocalDate.of(2026, 11, 9)).dayOfWeek shouldBe DayOfWeek.MONDAY
        Weeks.label(LocalDate.of(2026, 11, 2)) shouldBe "Mon 2 – Sun 8 Nov"
        Weeks.label(LocalDate.of(2026, 10, 26)) shouldBe "Mon 26 Oct – Sun 1 Nov"
    }

    @Test
    fun `rule labels read like the prototype`() {
        rules[Intents.COOK].label() shouldBe "Weekend mornings"
        rules[Intents.LEARN].label() shouldBe "Weekday mornings"
        rules[Intents.WATCH].label() shouldBe "Evenings"
        rules[Intents.KEEP].label() shouldBe "Stays where it is"
        IntentRule("x", setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), TimeOfDay.EVENING).label() shouldBe "Mon, Wed · Evening"
    }
}
