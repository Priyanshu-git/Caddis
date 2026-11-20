package com.nexxlabs.caddis

import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.nexxlabs.caddis.ui.nav.HOME_ROUTE
import com.nexxlabs.caddis.ui.nav.navigateToTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabNavigationTest {
    private lateinit var nav: TestNavHostController

    @Before
    fun setUp() {
        nav = TestNavHostController(ApplicationProvider.getApplicationContext())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = HOME_ROUTE) {
            composable(HOME_ROUTE, arguments = listOf(navArgument("week") { type = NavType.BoolType; defaultValue = false })) {}
            composable("library") {}
            composable("settings") {}
            composable("item/{id}") {}
        }
    }

    private fun route() = nav.currentBackStackEntry?.destination?.route

    @Test
    fun `home tab returns home from library and settings`() {
        nav.navigateToTab("library")
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())

        nav.navigateToTab("settings")
        nav.navigateToTab("library")
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())
        assertEquals(1, nav.currentBackStack.value.count { it.destination.route != null })
    }

    @Test
    fun `switching tabs back and forth always lands on home`() {
        nav.navigateToTab("library")
        nav.navigateToTab("home")
        nav.navigateToTab("library")
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())
    }

    @Test
    fun `home tab returns home after Browse the library and after a pushed screen`() {
        nav.navigate("library") { popUpTo(HOME_ROUTE); launchSingleTop = true }
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())
        nav.navigateToTab("library")
        nav.navigate("item/1")
        nav.navigateToTab("settings")
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())
    }

    @Test
    fun `tapping the tab you are on keeps the same screen`() {
        val home = nav.currentBackStackEntry
        nav.navigateToTab("home")
        assertSame(home, nav.currentBackStackEntry)

        nav.navigateToTab("library")
        val library = nav.currentBackStackEntry
        nav.navigateToTab("library")
        assertSame(library, nav.currentBackStackEntry)

        nav.navigateToTab("settings")
        val settings = nav.currentBackStackEntry
        nav.navigateToTab("settings")
        assertSame(settings, nav.currentBackStackEntry)
    }

    @Test
    fun `a tab you left comes back where you were`() {
        nav.navigateToTab("library")
        nav.navigate("item/1")
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())
        nav.navigateToTab("library")
        assertEquals("item/{id}", route())
    }
}
