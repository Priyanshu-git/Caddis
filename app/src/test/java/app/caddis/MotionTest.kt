package app.caddis

import app.caddis.ui.NavMotion
import app.caddis.ui.isTabRoute
import app.caddis.ui.nav.HOME_ROUTE
import app.caddis.ui.navMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun `the three tab routes are tabs and nothing else is`() {
        assertTrue(isTabRoute(HOME_ROUTE))
        assertTrue(isTabRoute("home"))
        assertTrue(isTabRoute("library"))
        assertTrue(isTabRoute("profile"))
        assertFalse(isTabRoute("item/{id}"))
        assertFalse(isTabRoute("note/{id}"))
        assertFalse(isTabRoute("privacy"))
        assertFalse(isTabRoute(null))
    }

    @Test
    fun `moving between tabs fades through, forwards or backwards`() {
        assertEquals(NavMotion.TabSwitch, navMotion(HOME_ROUTE, "library", pop = false))
        assertEquals(NavMotion.TabSwitch, navMotion("library", "profile", pop = false))
        // Tapping Home from Library pops the stack, but it is still a tab-to-tab move.
        assertEquals(NavMotion.TabSwitch, navMotion("library", HOME_ROUTE, pop = true))
    }

    @Test
    fun `going deeper slides in and going back slides out`() {
        assertEquals(NavMotion.Forward, navMotion("library", "item/{id}", pop = false))
        assertEquals(NavMotion.Forward, navMotion("item/{id}", "note/{id}", pop = false))
        assertEquals(NavMotion.Forward, navMotion("profile", "privacy", pop = false))
        assertEquals(NavMotion.Back, navMotion("item/{id}", "library", pop = true))
        assertEquals(NavMotion.Back, navMotion("note/{id}", "item/{id}", pop = true))
    }

    @Test
    fun `arriving at a tab from somewhere deeper fades through`() {
        // Tidy-up → Library, and "account deleted → Home".
        assertEquals(NavMotion.TabSwitch, navMotion("tidy", "library", pop = false))
        assertEquals(NavMotion.TabSwitch, navMotion("account", HOME_ROUTE, pop = false))
    }
}
