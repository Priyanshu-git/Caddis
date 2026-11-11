package com.nexxlabs.caddis.journeys

import com.nexxlabs.caddis.domain.schedule.QuietHours
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** First launch: one calm question about reminders, then the app. */
@HiltAndroidTest
class OnboardingJourneyTest : JourneyTest() {

    @Test
    fun firstLaunchAsksAboutReminders_withEverythingOff() {
        launchApp(onboarded = false)

        assertShowing("Want a nudge now and then?", "Off until you turn it on", "Private by default. No ads, no selling your data. Everything stays on this phone.")
        val s = runBlocking { settings.current() }
        assertFalse(s.dailyReminder)
        assertFalse(s.weeklySummary)
        assertFalse(s.onboarded)
    }

    @Test
    fun notNow_opensHome_andIsRemembered() {
        val scenario = launchApp(onboarded = false)
        tapButton("Not now")

        assertShowing("Today", "You’re all caught up")
        assertTrue(runBlocking { settings.current().onboarded })
        assertFalse(runBlocking { settings.current().dailyReminder })

        // Coming back later goes straight to Home.
        scenario.close()
        launchApp(onboarded = false)
        assertShowing("Today")
        assertFalse(exists(androidx.compose.ui.test.hasText("Want a nudge now and then?")))
    }

    @Test
    fun turningOnTheDailyReminder_thenContinue_keepsTheChoice() {
        launchApp(onboarded = false)
        tap(described("Daily reminder"))
        awaitText("At most one a day")
        tapButton("Continue")

        assertShowing("Today")
        eventually("daily reminder saved") { runBlocking { settings.current().dailyReminder } }
        assertTrue(runBlocking { settings.current().onboarded })
    }

    @Test
    fun quietHours_cycleThroughTheThreeWindows() {
        launchApp(onboarded = false)
        assertShowing(QuietHours.TEN_TO_SEVEN.label)

        tapText("Quiet hours")
        awaitText(QuietHours.ELEVEN_TO_SIX.label)
        tapText("Quiet hours")
        awaitText(QuietHours.NINE_TO_EIGHT.label)
        tapText("Quiet hours")
        awaitText(QuietHours.TEN_TO_SEVEN.label)
        assertEquals(QuietHours.TEN_TO_SEVEN, runBlocking { settings.current().quietHours })
    }
}
