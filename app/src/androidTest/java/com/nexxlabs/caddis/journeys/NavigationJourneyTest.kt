package com.nexxlabs.caddis.journeys

import com.nexxlabs.caddis.domain.model.Intents
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moving around: the three tabs, the add button, back, and the links notifications use. */
@HiltAndroidTest
class NavigationJourneyTest : JourneyTest() {

    private val addButton get() = described("Add a link or note")

    @Test
    fun theBottomBar_switchesBetweenHomeLibraryAndProfile() {
        launchApp()
        awaitText("Today")

        goTo("Library")
        awaitText("Search your saves")
        goTo("Profile")
        awaitText("On this phone")
        goTo("Home")
        awaitText("Today")
    }

    @Test
    fun theAddButton_isOnlyOnHomeAndLibrary() {
        launchApp()
        awaitText("Today")
        assertTrue(exists(addButton))

        goTo("Library")
        awaitText("Search your saves")
        assertTrue(exists(addButton))

        goTo("Profile")
        awaitText("On this phone")
        awaitGone(addButton)
    }

    @Test
    fun theBottomBar_hidesOnDeeperScreens_andReturnsOnBack() {
        launchApp()
        goTo("Profile")
        tapText("Return rules")
        awaitGone(tab("Library"))
        awaitGone(addButton)

        back()
        awaitText("On this phone")
        await(tab("Library"))
    }

    @Test
    fun back_fromATab_goesToHome() {
        launchApp()
        goTo("Library")
        awaitText("Search your saves")

        back()
        awaitText("Today")
    }

    @Test
    fun aLibraryDetour_isRememberedWhenYouComeBackToLibrary() {
        save("https://example.com/pasta", "Pasta night")
        launchApp()
        goTo("Library")
        tapTab("Collections")
        awaitText("Manage collections")

        goTo("Profile")
        awaitText("On this phone")
        goTo("Library")
        awaitText("Manage collections")
    }

    @Test
    fun home_isAlwaysFreshWhenYouReturnToIt() {
        save("https://example.com/pasta", "Pasta night", Intents.READ)
        daysLater(1)
        launchApp()
        awaitText("Pasta night")

        tapText("Pasta night")
        awaitText("WHY YOU KEPT IT")
        back()
        goTo("Library")
        awaitText("Search your saves")
        goTo("Home")

        awaitText("Today")
        awaitText("Pasta night")
    }

    @Test
    fun aNotificationLink_opensTheSaveDirectly_andBackGoesHome() {
        val id = save("https://example.com/pasta", "Pasta night").id
        launchApp(route = "item/$id")

        awaitText("WHY YOU KEPT IT")
        awaitText("Pasta night")
        back()
        awaitText("Today")
    }
}
