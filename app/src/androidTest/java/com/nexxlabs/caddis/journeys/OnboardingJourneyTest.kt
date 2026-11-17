package com.nexxlabs.caddis.journeys

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.schedule.QuietHours
import com.nexxlabs.caddis.ui.onboarding.ONBOARDING_PAGER_TAG
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** First launch: five cards you swipe through, one calm question about reminders, then the app. */
@HiltAndroidTest
class OnboardingJourneyTest : JourneyTest() {

    private fun swipeForward() = compose.onNodeWithTag(ONBOARDING_PAGER_TAG).performTouchInput { swipeLeft() }
    private fun swipeBackward() = compose.onNodeWithTag(ONBOARDING_PAGER_TAG).performTouchInput { swipeRight() }

    /** Which intro card is in view, by its "Step n of 5" progress line. */
    private fun awaitStep(n: Int) = await(described("Step $n of 5"))

    /** Walks the five cards with the main button and waits for the reminders question. */
    private fun toReminders() {
        launchApp(onboarded = false)
        tapButton("Get started")
        repeat(3) { tapButton("Next") }
        tapButton("Continue")
        awaitText("Not now")
    }

    @Test
    fun firstLaunch_opensOnTheWelcomeCard_withNothingChosenYet() {
        launchApp(onboarded = false)

        assertShowing("Save it. Use it.", "Get started", "Skip", "Private by default. Everything stays on this phone.")
        awaitStep(1)
        val s = runBlocking { settings.current() }
        assertFalse(s.dailyReminder)
        assertFalse(s.weeklySummary)
        assertFalse(s.onboarded)
    }

    @Test
    fun theMainButton_walksThroughTheCards() {
        launchApp(onboarded = false)

        tapButton("Get started")
        awaitStep(2)
        awaitText("Save from any app.")
        tapButton("Next")
        awaitStep(3)
        awaitText("Say why you kept it.")
        tapButton("Next")
        awaitStep(4)
        awaitText("It comes back when it fits.")
        tapButton("Next")
        awaitStep(5)
        awaitText("Finish it, or keep it.")
        tapButton("Continue")
        awaitText("Want a nudge now and then?")
        awaitText("Not now")
    }

    @Test
    fun swipingMovesForwardAndBack() {
        launchApp(onboarded = false)
        awaitStep(1)

        swipeForward()
        awaitStep(2)
        swipeForward()
        awaitStep(3)
        swipeBackward()
        awaitStep(2)
        swipeBackward()
        awaitStep(1)

        // Swiping back from the first card goes nowhere.
        swipeBackward()
        awaitStep(1)
        assertTrue(exists(hasText("Save it. Use it.")))
    }

    @Test
    fun swipingPastTheLastCard_reachesTheRemindersQuestion() {
        launchApp(onboarded = false)
        repeat(5) { swipeForward() }

        awaitText("Not now")
        assertFalse("the reminders question has no Skip", exists(button("Skip")))
        swipeBackward()
        awaitStep(5)
        awaitText("Finish it, or keep it.")
    }

    @Test
    fun systemBack_goesOneCardBack() {
        launchApp(onboarded = false)
        tapButton("Get started")
        awaitStep(2)

        back()
        awaitStep(1)
    }

    @Test
    fun skip_opensHome_withRemindersOff_andIsRemembered() {
        val scenario = launchApp(onboarded = false)
        tapButton("Get started")
        awaitStep(2)
        tapButton("Skip")

        assertShowing("Today", "Nothing saved yet", "Three ways to save your first one.")
        assertTrue(runBlocking { settings.current().onboarded })
        assertFalse(runBlocking { settings.current().dailyReminder })
        assertTrue(analytics.events.any { it is AnalyticsEvent.OnboardingFinished && it.skippedAtStep == 2 })

        // Coming back later goes straight to Home.
        scenario.close()
        launchApp(onboarded = false)
        assertShowing("Today")
        assertFalse(exists(hasText("Save it. Use it.")))
    }

    @Test
    fun theRemindersQuestion_showsEverythingOff() {
        toReminders()

        assertShowing("Want a nudge now and then?", "Off until you turn it on", "Private by default. No ads, no selling your data. Everything stays on this phone.")
    }

    @Test
    fun notNow_opensHome_andIsRemembered() {
        val scenario = launchApp(onboarded = false)
        tapButton("Get started")
        repeat(3) { tapButton("Next") }
        tapButton("Continue")
        awaitText("Not now")
        tapButton("Not now")

        assertShowing("Today", "Nothing saved yet")
        assertTrue(runBlocking { settings.current().onboarded })
        assertFalse(runBlocking { settings.current().dailyReminder })
        assertTrue(analytics.events.any { it is AnalyticsEvent.OnboardingFinished && it.skippedAtStep == null && !it.dailyReminder })

        scenario.close()
        launchApp(onboarded = false)
        assertShowing("Today")
        assertFalse(exists(hasText("Want a nudge now and then?")))
    }

    @Test
    fun turningOnTheDailyReminder_thenContinue_keepsTheChoice() {
        toReminders()
        tap(described("Daily reminder"))
        awaitText("At most one a day")
        tapButton("Continue")

        assertShowing("Today")
        eventually("daily reminder saved") { runBlocking { settings.current().dailyReminder } }
        assertTrue(runBlocking { settings.current().onboarded })
        assertTrue(analytics.events.any { it is AnalyticsEvent.OnboardingFinished && it.dailyReminder })
    }

    @Test
    fun quietHours_cycleThroughTheThreeWindows() {
        toReminders()
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
