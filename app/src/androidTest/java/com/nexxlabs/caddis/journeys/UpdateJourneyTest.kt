package com.nexxlabs.caddis.journeys

import com.nexxlabs.caddis.FakeUpdater
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/** A newer Caddis on Google Play: offered once on its own, always available in Profile, restart when it is downloaded. */
@HiltAndroidTest
class UpdateJourneyTest : JourneyTest() {

    @Inject lateinit var updater: FakeUpdater

    @Test
    fun anUpdateIsOfferedOnItsOwn_once() {
        updater.offer(versionCode = 9)

        launchApp()

        eventually("the dialog was opened") { updater.starts.get() == 1 }
        eventually("remembered") { runBlocking { settings.current() }.updateAsked?.versionCode == 9 }
        assertEquals(1, updater.starts.get())
    }

    @Test
    fun noUpdateIsOfferedDuringFirstLaunch() {
        updater.offer(versionCode = 9)

        launchApp(onboarded = false)
        awaitText("Skip")
        Thread.sleep(1_000) // Long enough for a launch check to have happened, if there were one.

        assertEquals(0, updater.starts.get())
    }

    @Test
    fun profile_offersTheUpdate_andThenOffersARestart() {
        updater.offer(versionCode = 9)
        // Already asked today, so nothing opens by itself.
        runBlocking { settings.markUpdateAsked(9, clock.instant()) }
        launchApp()
        goTo("Profile")

        awaitText("Update Caddis")
        assertEquals(0, updater.starts.get())
        tapText("Update Caddis")
        awaitText("Updating Caddis")
        assertEquals(1, updater.starts.get())

        updater.downloaded()
        // The toast first: it hides itself after 5 s, and the compose test clock skips time while waiting for anything else.
        awaitText("Update ready.")
        awaitText("Restart to update")
        tapText("Restart")
        eventually("restarted") { updater.restarts.get() == 1 }
    }

    @Test
    fun profile_showsNothingWhenThereIsNoUpdate() {
        launchApp()
        goTo("Profile")
        awaitText("On this phone")

        awaitNoText("Update Caddis")
        awaitNoText("Restart to update")
    }
}
