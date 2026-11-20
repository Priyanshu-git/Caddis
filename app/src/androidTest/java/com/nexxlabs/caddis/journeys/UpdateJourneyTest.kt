package com.nexxlabs.caddis.journeys

import com.nexxlabs.caddis.FakeUpdater
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/** A newer Caddis on Google Play: offered once on its own, always available in Settings, restart when it is downloaded. */
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
    fun settings_offersTheUpdate_andThenOffersARestart() {
        updater.offer(versionCode = 9)
        // Already asked today, so nothing opens by itself.
        runBlocking { settings.markUpdateAsked(9, clock.instant()) }
        launchApp()
        goTo("Settings")

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
    fun settings_showsNothingWhenThereIsNoUpdate() {
        launchApp()
        goTo("Settings")
        awaitText("Reminders")

        awaitNoText("Update Caddis")
        awaitNoText("Restart to update")
    }

    private fun openAbout() {
        launchApp()
        goTo("Settings")
        tapText("About us")
        awaitText("Check for updates")
    }

    @Test
    fun checkForUpdates_saysNothingUntilYouAsk_thenSaysYouAreUpToDate() {
        openAbout()
        awaitNoText("Up to date")

        tapText("Check for updates")

        awaitText("Up to date")
        assertEquals(0, updater.starts.get())
    }

    @Test
    fun checkForUpdates_saysWhenGooglePlayCannotBeReached_insteadOfUpToDate() {
        updater.goOffline()
        openAbout()

        tapText("Check for updates")

        awaitText("Couldn't check")
        awaitNoText("Up to date")
    }

    @Test
    fun checkForUpdates_opensPlaysDialogWhenOneIsWaiting() {
        updater.offer(versionCode = 9)
        runBlocking { settings.markUpdateAsked(9, clock.instant()) }
        openAbout()
        awaitText("Update ready")
        val before = updater.refreshes.get()

        tapText("Check for updates")

        eventually("asked Play again and opened the dialog") { updater.refreshes.get() > before && updater.starts.get() == 1 }
        awaitText("Updating…")
    }
}
