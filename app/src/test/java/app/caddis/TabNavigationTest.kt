package app.caddis

import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
            composable("profile") {}
            composable("item/{id}") {}
        }
    }

    private fun route() = nav.currentBackStackEntry?.destination?.route

    @Test
    fun `home tab returns home from library and profile`() {
        nav.navigateToTab("library")
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())

        nav.navigateToTab("profile")
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
        nav.navigateToTab("profile")
        nav.navigateToTab("home")
        assertEquals(HOME_ROUTE, route())
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
