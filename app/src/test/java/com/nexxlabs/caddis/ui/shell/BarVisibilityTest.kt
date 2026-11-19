package com.nexxlabs.caddis.ui.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarVisibilityTest {
    private fun bar() = BarVisibility(hideAfterPx = 50f)

    @Test fun startsVisible() = assertFalse(bar().hidden)

    @Test fun smallDownwardScrollKeepsTheBar() {
        val b = bar()
        b.onDrag(-20f); b.onDrag(-20f)
        assertFalse(b.hidden)
    }

    @Test fun scrollingDownPastTheThresholdHidesIt() {
        val b = bar()
        repeat(3) { b.onDrag(-20f) }
        assertTrue(b.hidden)
    }

    @Test fun anyUpwardScrollBringsItBack() {
        val b = bar()
        b.onDrag(-80f)
        assertTrue(b.hidden)
        b.onDrag(4f)
        assertFalse(b.hidden)
    }

    @Test fun changingDirectionRestartsTheCount() {
        val b = bar()
        b.onDrag(-40f)
        b.onDrag(5f)
        b.onDrag(-40f)
        assertFalse(b.hidden)
    }

    @Test fun anUnusedUpwardDragShowsIt() {
        val b = bar()
        b.onDrag(-80f)
        b.onUnusedDrag(12f)
        assertFalse(b.hidden)
    }

    @Test fun anUnusedDownwardDragDoesNotShowIt() {
        val b = bar()
        b.onDrag(-80f)
        b.onUnusedDrag(-12f)
        assertTrue(b.hidden)
    }

    @Test fun showResetsTheCount() {
        val b = bar()
        b.onDrag(-40f)
        b.show()
        b.onDrag(-40f)
        assertFalse(b.hidden)
    }
}
