package com.nexxlabs.caddis.journeys

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.nexxlabs.caddis.domain.model.Intents as IntentIds
import com.nexxlabs.caddis.domain.model.ReturnRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** "Remind me": an exact time for a save, the notification's home on Home, and what clears it. */
@HiltAndroidTest
class ReminderJourneyTest : JourneyTest() {

    private val url = "https://example.com/night-read"
    private var intentsStarted = false

    @After
    fun releaseIntents() {
        if (intentsStarted) Intents.release()
    }

    private fun stubExternalIntents() {
        Intents.init()
        intentsStarted = true
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    private fun setReminderIn(id: String, minutes: Long) = runBlocking {
        repo.setReminder(id, testClock.instant().plus(Duration.ofMinutes(minutes)))
    }

    private fun openItemSheet(id: String) {
        launchApp(route = "item/$id")
        awaitText("WHY YOU KEPT IT")
        tapDescribed("Bring back later")
        awaitText("Bring it back")
    }

    @Test
    fun theSheet_explainsEachMode_whenItIsSelected() {
        val id = save(url, "Night read", IntentIds.READ).id
        openItemSheet(id)

        // Later is the default: Home, no sound.
        assertShowing("Caddis puts this back on Home at the time you choose. No sound, no buzz.", "Tonight", "Not again")

        tapTab("Remind me")
        awaitText("You’ll get a notification at the exact time you set. Tap it to open the link.")
        assertShowing("Quick times", "In 1 hour", "9:00 am", "7:00 pm", "11:00 pm")
        awaitNoText("Not again")

        tapTab("Later")
        awaitText("Caddis puts this back on Home at the time you choose. No sound, no buzz.")
    }

    @Test
    fun remindMe_picksAnExactTime_andShowsItOnTheItem_withUndo() {
        val id = save(url, "Night read", IntentIds.READ).id
        openItemSheet(id)

        tapTab("Remind me")
        awaitText("Quick times")
        tapText("11:00 pm")
        tap(hasText("Remind me", substring = true) and role(Role.Button))

        awaitText("We’ll remind you", substring = true)
        eventually("the reminder is kept") { item(id).returnRule == ReturnRule.REMINDER }
        val at = item(id).returnAt!!.atZone(testClock.zone)
        assertEquals(23, at.hour)
        assertEquals(0, at.minute)
        assertTrue("the item shows its reminder", exists(hasContentDescription("Reminder", substring = true)))

        undo()
        eventually("undone") { item(id).returnRule == ReturnRule.DEFAULT && item(id).returnAt == null }
    }

    @Test
    fun anItemWithAReminder_reopensOnIt_andTheReminderCanBeCleared() {
        val id = save(url, "Night read", IntentIds.READ).id
        setReminderIn(id, 180)
        launchApp(route = "item/$id")
        awaitText("WHY YOU KEPT IT")

        tap(hasContentDescription("Reminder", substring = true))
        awaitText("Bring it back")
        assertShowing("Update reminder", "Clear reminder")

        tapButton("Clear reminder")
        awaitText("Reminder cleared.")
        eventually("cleared") { item(id).returnRule == ReturnRule.DEFAULT && item(id).returnAt == null }

        undo()
        eventually("restored") { item(id).returnRule == ReturnRule.REMINDER }
    }

    @Test
    fun choosingALaterTime_replacesTheReminder() {
        val id = save(url, "Night read", IntentIds.READ).id
        setReminderIn(id, 180)
        openItemSheet(id)

        tapTab("Later")
        tapText("This weekend")
        awaitText("Back Saturday morning.", substring = false)
        eventually("a later date replaces it") { item(id).returnRule == ReturnRule.WEEKEND }
    }

    @Test
    fun aDueReminder_leadsHome_untilTheLinkIsOpened() {
        stubExternalIntents()
        val id = save(url, "Night read", IntentIds.READ).id
        setReminderIn(id, 5)
        testClock.advance(Duration.ofMinutes(6))
        launchApp()

        awaitText("Night read")
        awaitText("Your reminder", substring = true)
        assertTrue(exists(button("Open")))

        // It stays through the next day: a reminder is not a Later date.
        eventually("shown today is recorded") { item(id).surfacedCount == 1 }
        assertEquals(ReturnRule.REMINDER, item(id).returnRule)

        tapButton("Open")
        intended(hasAction(Intent.ACTION_VIEW))
        eventually("opening it answers the reminder") { item(id).returnRule == ReturnRule.DEFAULT }
        assertNotNull(item(id).lastOpenedAt)
        awaitText("You’re all caught up")
    }

    @Test
    fun aReminderStillAhead_staysOffHome() {
        val id = save(url, "Night read", IntentIds.READ).id
        setReminderIn(id, 180)
        daysLater(0)
        launchApp()

        awaitText("You’re all caught up")
        assertTrue(!exists(hasText("Night read")))
        assertEquals(ReturnRule.REMINDER, item(id).returnRule)
    }
}
