package com.nexxlabs.caddis.domain.notes

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Undo in the note editor: bursts of typing are one step, and undo walks back through them. */
class EditHistoryTest {

    @Test
    fun `nothing to undo at first`() {
        val h = EditHistory("")
        h.canUndo shouldBe false
        h.undo() shouldBe null
        h.current shouldBe ""
    }

    @Test
    fun `changes close together are one step`() {
        val h = EditHistory("", groupMillis = 1_000)
        h.record("h", 0)
        h.record("he", 300)
        h.record("hey", 900)
        h.undo() shouldBe ""
        h.canUndo shouldBe false
    }

    @Test
    fun `a pause starts a new step`() {
        val h = EditHistory("", groupMillis = 1_000)
        h.record("one", 0)
        h.record("one two", 5_000)
        h.undo() shouldBe "one"
        h.undo() shouldBe ""
        h.undo() shouldBe null
    }

    @Test
    fun `the pause is measured from the last change, not the first`() {
        val h = EditHistory("", groupMillis = 1_000)
        h.record("a", 0)
        h.record("ab", 800)
        h.record("abc", 1_600)
        h.undo() shouldBe ""
    }

    @Test
    fun `the same content is not a change`() {
        val h = EditHistory("a")
        h.record("a", 0) shouldBe false
        h.canUndo shouldBe false
    }

    @Test
    fun `sameness can ignore part of the state, like the caret`() {
        val h = EditHistory("a" to 0, same = { x, y -> x.first == y.first })
        h.record("a" to 1, 0) shouldBe false
        h.record("ab" to 2, 10) shouldBe true
        h.undo() shouldBe ("a" to 0)
    }

    @Test
    fun `after an undo the next change is its own step`() {
        val h = EditHistory("", groupMillis = 1_000)
        h.record("one", 0)
        h.record("one two", 5_000)
        h.undo() shouldBe "one"
        h.record("one three", 5_100)
        h.undo() shouldBe "one"
        h.undo() shouldBe ""
    }

    @Test
    fun `replacing the state adds no step`() {
        val h = EditHistory("")
        h.replace("loaded")
        h.canUndo shouldBe false
        h.current shouldBe "loaded"
        h.record("loaded!", 0)
        h.undo() shouldBe "loaded"
    }

    @Test
    fun `only the newest steps are kept`() {
        val h = EditHistory("0", groupMillis = 1, limit = 3)
        (1..5).forEach { h.record("$it", it * 10L) }
        h.undo() shouldBe "4"
        h.undo() shouldBe "3"
        h.undo() shouldBe "2"
        h.undo() shouldBe null
    }
}
