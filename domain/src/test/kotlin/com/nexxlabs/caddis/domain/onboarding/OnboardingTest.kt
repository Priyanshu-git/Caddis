package com.nexxlabs.caddis.domain.onboarding

import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OnboardingTest {
    @Test
    fun `five intro cards then the reminders question`() {
        Onboarding.INTRO_PAGES shouldBe 5
        Onboarding.PAGE_COUNT shouldBe 6
        (0..4).all { Onboarding.isIntro(it) } shouldBe true
        Onboarding.isIntro(Onboarding.NUDGES_PAGE) shouldBe false
        Onboarding.isLast(Onboarding.NUDGES_PAGE) shouldBe true
    }

    @Test
    fun `next and previous stop at the ends`() {
        Onboarding.next(0) shouldBe 1
        Onboarding.next(4) shouldBe 5
        Onboarding.next(5) shouldBe 5
        Onboarding.previous(5) shouldBe 4
        Onboarding.previous(0) shouldBe 0
    }

    @Test
    fun `a restored page is kept inside the flow`() {
        Onboarding.clamp(-3) shouldBe 0
        Onboarding.clamp(2) shouldBe 2
        Onboarding.clamp(99) shouldBe 5
    }

    @Test
    fun `skip is offered on the intro cards only`() {
        (0..4).all { Onboarding.canSkip(it) } shouldBe true
        Onboarding.canSkip(5) shouldBe false
    }

    @Test
    fun `the main button reads Get started, then Next, then Continue`() {
        Onboarding.primary(0) shouldBe Onboarding.Primary.GET_STARTED
        (1..3).map { Onboarding.primary(it) }.toSet() shouldBe setOf(Onboarding.Primary.NEXT)
        Onboarding.primary(4) shouldBe Onboarding.Primary.CONTINUE
        Onboarding.primary(5) shouldBe Onboarding.Primary.CONTINUE
    }

    @Test
    fun `progress is a step out of five on the intro cards and nothing after`() {
        Onboarding.step(0) shouldBe 1
        Onboarding.step(4) shouldBe 5
        Onboarding.step(5) shouldBe null
    }

    @Test
    fun `the finished event says where it was skipped and which reminders were chosen, nothing else`() {
        val skipped = AnalyticsEvent.OnboardingFinished(skippedAtStep = 2, dailyReminder = false, weeklySummary = false)
        skipped.name shouldBe "onboarding_finished"
        skipped.params() shouldBe mapOf("skipped_at" to "2", "daily" to "no", "weekly" to "no")

        val done = AnalyticsEvent.OnboardingFinished(skippedAtStep = null, dailyReminder = true, weeklySummary = false)
        done.params() shouldBe mapOf("skipped_at" to "none", "daily" to "yes", "weekly" to "no")
    }
}
