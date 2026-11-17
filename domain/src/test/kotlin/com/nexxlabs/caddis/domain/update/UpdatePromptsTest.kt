package com.nexxlabs.caddis.domain.update

import com.nexxlabs.caddis.domain.update.UpdatePrompts.Asked
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class UpdatePromptsTest {
    private val now = Instant.parse("2026-11-17T10:00:00Z")

    @Test
    fun `a version that was never offered is offered`() {
        UpdatePrompts.shouldAsk(4, lastAsked = null, now = now, onboarded = true) shouldBe true
    }

    @Test
    fun `nothing is offered during first launch`() {
        UpdatePrompts.shouldAsk(4, lastAsked = null, now = now, onboarded = false) shouldBe false
    }

    @Test
    fun `the same version is not offered again the same day`() {
        val asked = Asked(4, now.minus(Duration.ofHours(3)))
        UpdatePrompts.shouldAsk(4, asked, now, onboarded = true) shouldBe false
    }

    @Test
    fun `the same version is not offered again just under a week later`() {
        val asked = Asked(4, now.minus(Duration.ofDays(7)).plusSeconds(1))
        UpdatePrompts.shouldAsk(4, asked, now, onboarded = true) shouldBe false
    }

    @Test
    fun `the same version is offered again after a week`() {
        UpdatePrompts.shouldAsk(4, Asked(4, now.minus(Duration.ofDays(7))), now, onboarded = true) shouldBe true
        UpdatePrompts.shouldAsk(4, Asked(4, now.minus(Duration.ofDays(30))), now, onboarded = true) shouldBe true
    }

    @Test
    fun `a newer version is offered at once, even right after an older one`() {
        val asked = Asked(4, now.minus(Duration.ofMinutes(5)))
        UpdatePrompts.shouldAsk(5, asked, now, onboarded = true) shouldBe true
    }

    @Test
    fun `a clock set back does not silence the prompt past a week`() {
        val asked = Asked(4, now.plus(Duration.ofDays(60)))
        UpdatePrompts.shouldAsk(4, asked, now, onboarded = true) shouldBe true
    }
}
