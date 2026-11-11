package com.nexxlabs.caddis.journeys

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.nexxlabs.caddis.domain.model.Intents as IntentIds
import com.nexxlabs.caddis.domain.model.TimeOfDay
import com.nexxlabs.caddis.domain.schedule.QuietHours
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek

/** Profile: reminders, return rules, intents, your data, privacy and the account. */
@HiltAndroidTest
class ProfileJourneyTest : JourneyTest() {

    private var intentsStarted = false

    @After
    fun releaseIntents() {
        if (intentsStarted) Intents.release()
    }

    private fun openProfile() {
        launchApp()
        goTo("Profile")
        awaitText("On this phone")
    }

    private fun openFromProfile(row: String, title: String = row) {
        openProfile()
        tapText(row)
        awaitText(title)
    }

    private fun rule(intent: String) = runBlocking { repo.observeRules().first() }.getValue(intent)

    // ---------- the menu ----------

    @Test
    fun profile_listsEverythingYouCanChange() {
        openProfile()

        assertShowing(
            "No account yet. Sync comes later.", "Return rules", "Intents", "Notifications",
            "Pause on low battery", "Pause on mobile data", "Export and cache", "Trash", "Privacy policy", "Account settings",
        )
        // Eight default intents.
        assertTrue(exists(hasText("8")))
    }

    @Test
    fun backgroundWork_canBePausedOnMobileData_orLowBattery() {
        openProfile()
        assertTrue(runBlocking { settings.current() }.pauseOnLowBattery)
        assertFalse(runBlocking { settings.current() }.pauseOnMobileData)

        tap(described("Pause on mobile data"))
        tap(described("Pause on low battery"))

        eventually("both flipped") { runBlocking { settings.current() }.let { it.pauseOnMobileData && !it.pauseOnLowBattery } }
    }

    // ---------- notifications ----------

    @Test
    fun notifications_areOffUntilYouTurnThemOn() {
        openFromProfile("Notifications")

        assertShowing("Reminders are off", "Turn on the daily reminder to see one.")
        tap(described("Daily reminder"))
        awaitText("Something you saved that fits now")
        tap(described("Weekly summary"))

        eventually("both on") { runBlocking { settings.current() }.let { it.dailyReminder && it.weeklySummary } }
        assertTrue(analytics.events.any { it.name == "notification_setting" })
    }

    @Test
    fun quietHours_cycleFromTheNotificationsScreen() {
        openFromProfile("Notifications")
        assertShowing(QuietHours.TEN_TO_SEVEN.label)

        tapText("Quiet hours")
        awaitText(QuietHours.ELEVEN_TO_SIX.label)
        eventually("saved") { runBlocking { settings.current() }.quietHours == QuietHours.ELEVEN_TO_SIX }
    }

    // ---------- return rules ----------

    @Test
    fun returnRules_changeWhichDaysAndWhenASaveComesBack() {
        openFromProfile("Return rules")
        assertShowing("Weekend mornings")
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), rule(IntentIds.COOK).days)

        tapDescribed("Saturday")
        eventually("saturday off") { rule(IntentIds.COOK).days == setOf(DayOfWeek.SUNDAY) }
        tapDescribed("Friday")
        eventually("friday on") { DayOfWeek.FRIDAY in rule(IntentIds.COOK).days }

        tapButton("Evening")
        eventually("evening") { rule(IntentIds.COOK).timeOfDay == TimeOfDay.EVENING }
    }

    @Test
    fun returnRules_anIntentWithNoDays_neverReturns() {
        openFromProfile("Return rules")

        tapDescribed("Saturday")
        tapDescribed("Sunday")

        awaitText("Never returns")
        eventually("no days") { rule(IntentIds.COOK).days.isEmpty() }
    }

    @Test
    fun returnRules_limitHowManyComeBackADay() {
        openFromProfile("Return rules")
        val start = rule(IntentIds.COOK).maxPerDay

        tapDescribed("More")
        eventually("one more") { rule(IntentIds.COOK).maxPerDay == minOf(3, start + 1) }
        repeat(3) { tapDescribed("Fewer") }
        eventually("never below one") { rule(IntentIds.COOK).maxPerDay == 1 }
    }

    // ---------- intents ----------

    @Test
    fun intents_canBeCreated_withTheirOwnDoneWord() {
        openFromProfile("Intents")
        awaitText("Default, always kept", substring = true)
        awaitText("Intents say why you kept something.", substring = true)

        type("New intent name", "Gift")
        type("Done button label", "Gifted")
        tapButton("Create intent")

        awaitText("Created “Gift”.")
        awaitText("0 saves · Done says “Gifted”")
        assertTrue(runBlocking { repo.observeIntents().first() }.any { it.name == "Gift" && it.doneLabel == "Gifted" })
    }

    @Test
    fun intents_cannotShareAName() {
        openFromProfile("Intents")

        type("New intent name", "Read")
        tapButton("Create intent")

        awaitText("You already have an intent with that name.")
    }

    @Test
    fun intents_canBeRenamed() {
        runBlocking { repo.createIntent("Gift", "Gifted") }
        openFromProfile("Intents")

        tapText("Gift")
        replaceText("Intent name", "Present")
        tapButton("Save")

        awaitText("Saved.")
        awaitText("Present")
        assertTrue(runBlocking { repo.observeIntents().first() }.any { it.name == "Present" })
    }

    @Test
    fun deletingAnIntent_movesItsSavesToJustKeep() {
        val intent = runBlocking { repo.createIntent("Gift", "Gifted") } as com.nexxlabs.caddis.data.LibraryRepository.NameResult.Ok
        val saved = save("https://example.com/scarf", "A scarf", intent.id)
        openFromProfile("Intents")

        tapText("Gift")
        tapButton("Delete")
        awaitText("Sure? 1 save moves to Just keep")
        tapButton("Sure? 1 save moves to Just keep")

        awaitText("Intent deleted. 1 save moved to Just keep.")
        eventually("moved") { item(saved.id).intentId == IntentIds.KEEP }
        assertFalse(runBlocking { repo.observeIntents().first() }.any { it.name == "Gift" })
    }

    @Test
    fun justKeep_isLocked() {
        openFromProfile("Intents")

        tapText("Just keep")
        // It does not open an editor.
        assertFalse(exists(field("Intent name")))
    }

    // ---------- your data ----------

    @Test
    fun export_writesTheWholeLibrary_andOffersToShareIt() {
        Intents.init()
        intentsStarted = true
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        save("https://example.com/pasta", "Pasta night")
        openFromProfile("Export and cache", "Export your library")

        tapButton("JSON")
        intended(hasAction(Intent.ACTION_CHOOSER))
        val exports = File(context.cacheDir, "exports")
        eventually("json written") { exports.listFiles { f -> f.extension == "json" }?.any { it.readText().contains("Pasta night") } == true }

        tapButton("HTML bookmarks")
        eventually("html written") { exports.listFiles { f -> f.extension == "html" }?.any { it.readText().contains("Pasta night") } == true }
    }

    @Test
    fun clearCache_saysSo() {
        openFromProfile("Export and cache", "Export your library")
        assertShowing("Thumbnail cache")

        tapText("Clear cache")
        awaitText("Thumbnail cache cleared.")
    }

    // ---------- privacy and account ----------

    @Test
    fun privacy_isShortAndPlain() {
        openFromProfile("Privacy policy")

        assertShowing("Your library is yours", "No ads, no selling data", "The clipboard", "No AI")
    }

    @Test
    fun account_saysThereIsNoAccountYet() {
        openFromProfile("Account settings")

        assertShowing("Sync", "Off · this phone only", "Sign-in method", "None yet")
    }

    @Test
    fun deleteAllData_asksFirst_thenClearsEverything_butKeepsYouInTheApp() {
        save("https://example.com/pasta", "Pasta night")
        saveNote("Gift ideas")
        openFromProfile("Account settings")

        tapText("Delete all data")
        awaitText("Delete all your data?")
        tapButton("Cancel")
        awaitNoText("Delete all your data?")
        assertEquals("cancelling deletes nothing", 2, library().size)

        tapText("Delete all data")
        tapButton("Delete everything")

        // Home, empty. (Not asserted: which toast shows. Clearing the image cache posts its own, right after the delete one.)
        awaitText("Today")
        awaitText("You’re all caught up")
        eventually("library empty") { library().isEmpty() }
        // Defaults come back, and onboarding is not shown again.
        assertEquals(8, runBlocking { repo.observeIntents().first() }.size)
        assertTrue(runBlocking { settings.current() }.onboarded)
    }
}
