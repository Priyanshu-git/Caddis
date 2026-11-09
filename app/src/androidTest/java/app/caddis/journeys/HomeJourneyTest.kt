package app.caddis.journeys

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.model.Intents as IntentIds
import app.caddis.domain.model.ReturnRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home: at most three saves that fit now, each finished, delayed or opened in a tap. */
@HiltAndroidTest
class HomeJourneyTest : JourneyTest() {

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

    /** A save that has been waiting more than a day, so Home may bring it back. */
    private fun waitingRead(title: String = "Why we nap", slug: String = "why-we-nap") =
        save("https://example.com/$slug", title = title, intent = IntentIds.READ).also { daysLater(1) }

    @Test
    fun aBrandNewSave_waitsADay_beforeItComesBack() {
        save("https://example.com/fresh", title = "Fresh one", intent = IntentIds.READ)
        launchApp()

        assertShowing("Today", "You’re all caught up", "Browse the library")
        awaitText("Read saves return on", substring = true)
        assertTrue(!exists(androidx.compose.ui.test.hasText("Fresh one")))
    }

    @Test
    fun anOlderSave_comesBackWithOpenDoneAndLater() {
        waitingRead()
        launchApp()

        awaitText("Why we nap")
        assertTrue(exists(button("Open")))
        assertTrue(exists(button("Read")))
        assertTrue(exists(button("Later")))
        assertTrue(!exists(androidx.compose.ui.test.hasText("You’re all caught up")))
        eventually("shown today is recorded") { library().single().surfacedCount == 1 }
    }

    @Test
    fun homeShowsAtMostThree_andTheRestWaitInTheLibrary() {
        save("https://example.com/a", "First read", IntentIds.READ)
        save("https://example.com/b", "A film", IntentIds.WATCH)
        save("https://example.com/c", "A lesson", IntentIds.LEARN)
        save("https://example.com/d", "A gadget", IntentIds.BUY)
        daysLater(1)
        launchApp()

        awaitText("Everything else waits in the Library.")
        assertEquals(3, count(button("Later")))
    }

    @Test
    fun done_marksItUsed_andUndoBringsItBack() {
        val saved = waitingRead()
        launchApp()
        awaitText("Why we nap")

        tapButton("Read")
        awaitText("Marked “Read”.")
        eventually("marked done") { item(saved.id).status == ItemStatus.DONE }
        awaitText("You’re all caught up")
        assertEquals(1, runBlocking { repo.observeWeek(java.time.LocalDate.of(2026, 11, 2)).first().usedCount })

        undo()
        eventually("back to active") { item(saved.id).status == ItemStatus.ACTIVE }
        awaitText("Why we nap")
        assertEquals(0, runBlocking { repo.observeWeek(java.time.LocalDate.of(2026, 11, 2)).first().usedCount })
    }

    @Test
    fun later_hidesTheSaveUntilThen_andUndoRestoresIt() {
        val saved = waitingRead()
        launchApp()
        awaitText("Why we nap")

        tapButton("Later")
        awaitText("Bring it back")
        tapText("Tonight")
        awaitText("Back tonight at 7 pm.")

        eventually("return time saved") { item(saved.id).returnRule == ReturnRule.TONIGHT }
        assertTrue(item(saved.id).returnAt!!.isAfter(testClock.instant()))
        awaitText("You’re all caught up")

        undo()
        eventually("return time restored") { item(saved.id).returnAt == null }
        awaitText("Why we nap")
    }

    @Test
    fun later_canPickADate() {
        val saved = waitingRead()
        launchApp()
        awaitText("Why we nap")

        tapButton("Later")
        tapText("Pick a date")
        tapText("Set")

        awaitText("Back on", substring = true)
        eventually("date saved") { item(saved.id).returnRule == ReturnRule.DATE }
        assertNotNull(item(saved.id).returnAt)
    }

    @Test
    fun open_sendsTheLinkToTheBrowserOrItsApp() {
        stubExternalIntents()
        val saved = waitingRead()
        launchApp()
        awaitText("Why we nap")

        tapButton("Open")

        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData("https://example.com/why-we-nap")))
        eventually("opened is recorded") { item(saved.id).lastOpenedAt != null }
    }

    @Test
    fun tappingASave_opensItsPage_andBackReturnsToHome() {
        waitingRead()
        launchApp()
        awaitText("Why we nap")

        tapText("Why we nap")
        awaitText("WHY YOU KEPT IT")
        back()
        awaitText("Today")
        awaitText("Why we nap")
    }

    @Test
    fun theWeekCard_countsWhatYouSavedUsedAndDeleted() {
        save("https://example.com/one", "One")
        launchApp()

        assertShowing("This week", "Saved", "Used", "Deleted", "Mon 2 – Sun 8 Nov")
        assertEquals(1, runBlocking { repo.observeWeek(java.time.LocalDate.of(2026, 11, 2)).first().savedCount })
    }

    @Test
    fun theWeeklySummaryNotification_opensHomeAtTheWeek() {
        launchApp(route = "home?week=true")

        awaitText("This week")
        eventually("summary viewed from the notification") {
            analytics.events.any { it is AnalyticsEvent.SummaryViewed && it.fromNotification }
        }
    }

    @Test
    fun oldUntouchedSaves_offerATidyUp() {
        save("https://example.com/old-1", "Old one", IntentIds.KEEP)
        save("https://example.com/old-2", "Old two", IntentIds.KEEP)
        daysLater(100)
        launchApp()

        assertShowing("Time for a tidy-up", "2 saves untouched for 90 days", "Review")
    }

    @Test
    fun picks_followTheClock_notTheLaunchDay() {
        // 12 hours is the grace period for a fresh save; after it, the save is eligible.
        save("https://example.com/half-day", "Half a day", IntentIds.READ)
        testClock.now = testClock.now.plusSeconds(13 * 3600)
        launchApp()
        awaitText("Half a day")
    }
}
