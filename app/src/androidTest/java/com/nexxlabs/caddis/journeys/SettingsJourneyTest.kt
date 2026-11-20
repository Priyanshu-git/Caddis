package com.nexxlabs.caddis.journeys

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import com.nexxlabs.caddis.ui.settings.AppLinks
import org.hamcrest.Matchers.allOf
import com.nexxlabs.caddis.domain.model.Intents as IntentIds
import com.nexxlabs.caddis.domain.model.TimeOfDay
import com.nexxlabs.caddis.BuildConfig
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

/** Settings: the hub, reminders, return rules, intents, your data, about us. */
@HiltAndroidTest
class SettingsJourneyTest : JourneyTest() {

    private var intentsStarted = false

    @After
    fun releaseIntents() {
        if (intentsStarted) Intents.release()
    }

    private fun openSettings() {
        launchApp()
        goTo("Settings")
        awaitText("Return rules")
    }

    private fun openFromSettings(row: String, title: String = row) {
        openSettings()
        tapText(row)
        awaitText(title)
    }

    /** Settings, then Your data. */
    private fun openYourData() = openFromSettings("Your data", "Pause on low battery")

    /** Settings, then Your data, then Export and cache. */
    private fun openExport() {
        openYourData()
        tapText("Export and cache")
        awaitText("Export your library")
    }

    private fun rule(intent: String) = runBlocking { repo.observeRules().first() }.getValue(intent)

    // ---------- the menu ----------

    @Test
    fun settings_listsEverythingYouCanChange() {
        openSettings()

        assertShowing(
            "Intents", "Return rules", "Reminders",
            "Enjoying Caddis?", "Rate", "Share", "Feedback",
            "Your data", "About us",
        )
        // Everything else is one tap down, not on the hub.
        awaitNoText("Pause on low battery")
        awaitNoText("Trash")
        awaitNoText("Delete all data")
        awaitNoText("Privacy policy")
        // No promises about accounts or sync.
        awaitNoText("sync", substring = true)
        awaitNoText("account", substring = true)
        // Eight default intents.
        assertTrue(exists(hasText("8")))
    }

    @Test
    fun backgroundWork_canBePausedOnLowBattery() {
        openYourData()
        assertTrue(runBlocking { settings.current() }.pauseOnLowBattery)
        awaitNoText("mobile data", substring = true)

        tap(described("Pause on low battery"))

        eventually("flipped") { !runBlocking { settings.current() }.pauseOnLowBattery }
    }

    // ---------- notifications ----------

    @Test
    fun reminders_areOffUntilYouTurnThemOn() {
        openFromSettings("Reminders", "Daily reminder")

        assertShowing("Reminders are off", "Turn on the daily reminder to see one.")
        tap(described("Daily reminder"))
        awaitText("Something you saved that fits now")
        tap(described("Weekly summary"))

        eventually("both on") { runBlocking { settings.current() }.let { it.dailyReminder && it.weeklySummary } }
        assertTrue(analytics.events.any { it.name == "notification_setting" })
    }

    @Test
    fun reminders_haveNoQuietHours_soNothingToSet() {
        openFromSettings("Reminders", "Daily reminder")

        assertShowing("Daily reminder", "Weekly summary")
        awaitNoText("Quiet hours")
    }

    // ---------- about caddis ----------

    @Test
    fun enjoyingCaddis_rateShareAndFeedbackOpenTheRightApps() {
        Intents.init()
        intentsStarted = true
        val ok = Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(ok)
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(ok)
        intending(hasAction(Intent.ACTION_SENDTO)).respondWith(ok)
        openSettings()

        tapText("Rate")
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(AppLinks.PLAY_URL)))

        tapText("Share")
        intended(hasAction(Intent.ACTION_CHOOSER))

        tapText("Feedback")
        intended(
            allOf(
                hasAction(Intent.ACTION_SENDTO),
                hasData("mailto:"),
                hasExtra(Intent.EXTRA_EMAIL, arrayOf(AppLinks.FEEDBACK_EMAIL)),
            ),
        )
    }

    @Test
    fun aboutUs_showsTheVersion_andNeverClaimsToBeUpToDateUntilChecked() {
        openFromSettings("About us", "Check for updates")

        assertShowing("Caddis", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "Privacy policy", "Made by Nexxlabs", AppLinks.FEEDBACK_EMAIL)
        awaitNoText("Up to date")
    }

    // ---------- return rules ----------

    @Test
    fun returnRules_changeWhichDaysAndWhenASaveComesBack() {
        openFromSettings("Return rules")
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
        openFromSettings("Return rules")

        tapDescribed("Saturday")
        tapDescribed("Sunday")

        awaitText("Never returns")
        eventually("no days") { rule(IntentIds.COOK).days.isEmpty() }
    }

    @Test
    fun returnRules_limitHowManyComeBackADay() {
        openFromSettings("Return rules")
        val start = rule(IntentIds.COOK).maxPerDay

        tapDescribed("More")
        eventually("one more") { rule(IntentIds.COOK).maxPerDay == minOf(3, start + 1) }
        repeat(3) { tapDescribed("Fewer") }
        eventually("never below one") { rule(IntentIds.COOK).maxPerDay == 1 }
    }

    // ---------- intents ----------

    @Test
    fun intents_canBeCreated_withTheirOwnDoneWord() {
        openFromSettings("Intents")
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
        openFromSettings("Intents")

        type("New intent name", "Read")
        tapButton("Create intent")

        awaitText("You already have an intent with that name.")
    }

    @Test
    fun intents_canBeRenamed() {
        runBlocking { repo.createIntent("Gift", "Gifted") }
        openFromSettings("Intents")

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
        openFromSettings("Intents")

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
        openFromSettings("Intents")

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
        openExport()

        tapButton("JSON")
        intended(hasAction(Intent.ACTION_CHOOSER))
        val exports = File(context.cacheDir, "exports")
        eventually("json written") { exports.listFiles { f -> f.extension == "json" }?.any { it.readText().contains("Pasta night") } == true }

        tapButton("HTML bookmarks")
        eventually("html written") { exports.listFiles { f -> f.extension == "html" }?.any { it.readText().contains("Pasta night") } == true }
    }

    @Test
    fun clearCache_saysSo() {
        openExport()
        assertShowing("Thumbnail cache")

        tapText("Clear cache")
        awaitText("Thumbnail cache cleared.")
    }

    // ---------- privacy and account ----------

    @Test
    fun privacy_isShortAndPlain() {
        openFromSettings("About us", "Check for updates")
        tapText("Privacy policy")
        awaitText("Your library is yours")

        assertShowing("Your library is yours", "No ads, no analytics, no selling data", "The clipboard", "No AI")
    }

    @Test
    fun deleteAllData_asksFirst_thenClearsEverything_butKeepsYouInTheApp() {
        save("https://example.com/pasta", "Pasta night")
        saveNote("Gift ideas")
        openYourData()
        awaitText("Export first if you want a copy.", substring = true)
        awaitNoText("sync", substring = true)

        tapText("Delete all data")
        awaitText("Delete all your data?")
        tapButton("Cancel")
        awaitNoText("Delete all your data?")
        assertEquals("cancelling deletes nothing", 2, library().size)

        tapText("Delete all data")
        tapButton("Delete everything")

        // Home, empty. (Not asserted: which toast shows. Clearing the image cache posts its own, right after the delete one.)
        awaitText("Today")
        awaitText("Nothing saved yet")
        eventually("library empty") { library().isEmpty() }
        // Defaults come back, and onboarding is not shown again.
        assertEquals(8, runBlocking { repo.observeIntents().first() }.size)
        assertTrue(runBlocking { settings.current() }.onboarded)
    }
}
