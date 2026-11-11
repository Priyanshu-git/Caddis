package com.nexxlabs.caddis.journeys

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.ItemType
import com.nexxlabs.caddis.share.ShareActivity
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Save from another app's share sheet: the save is written first, the sheet asks one question. */
@HiltAndroidTest
class ShareSheetJourneyTest : JourneyTest() {

    private fun share(text: String?, subject: String? = null): ActivityScenario<ShareActivity> {
        val intent = Intent(context, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
        text?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        return track(ActivityScenario.launch(intent))
    }

    /** The sheet is done once the activity is finishing (or already gone). */
    private fun ActivityScenario<ShareActivity>.awaitFinished() {
        try {
            eventually("the share sheet closes") {
                state == Lifecycle.State.DESTROYED || runCatching {
                    var finishing = false
                    onActivity { finishing = it.isFinishing }
                    finishing
                }.getOrDefault(true)
            }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message} (state=$state)\nOn screen:\n${screen()}", e)
        }
    }

    @Test
    fun aSharedLink_isSaved_withTheSurroundingWordsAsItsTitle() {
        share("Ten minute pasta https://example.com/pasta-recipe?utm_source=chat")

        assertShowing("Saved to Caddis", "Ten minute pasta", "Guessed from a webpage. Tap to change.")
        val saved = library().single()
        assertEquals("Ten minute pasta", saved.title)
        assertEquals(Intents.COOK, saved.intentId)
        assertTrue(analytics.events.any { it is AnalyticsEvent.SaveCreated && it.entry == AnalyticsEvent.SaveEntry.SHARE_SHEET })
    }

    @Test
    fun theSave_survivesTheSheetBeingSwipedAwayAtOnce() {
        val scenario = share("https://example.com/pasta-recipe")
        scenario.close()

        eventually("the save is in the library") { library().size == 1 }
        assertEquals("https://example.com/pasta-recipe", library().single().url)
    }

    @Test
    fun choosingWhy_andAThought_thenDone_closesTheSheetAndKeepsEverything() {
        val scenario = share("https://example.com/headphones-review")
        awaitText("Saved to Caddis")

        tapButton("Try")
        type("A thought for future you", "before the sale ends")
        tapButton("Done")

        scenario.awaitFinished()
        eventually("choices kept") { library().single().let { it.intentId == Intents.TRY && it.reason == "before the sale ends" } }
    }

    @Test
    fun sharedText_withoutALink_isKeptAsANote() {
        share("Remember to call the plumber\nabout the kitchen tap")

        awaitText("Saved to Caddis")
        val note = library().single()
        assertEquals(ItemType.NOTE, note.type)
        assertEquals("Remember to call the plumber", note.title)
        assertTrue(note.note.contains("about the kitchen tap"))
    }

    @Test
    fun sharingALinkTwice_offersTheEarlierSave_orASecondCopy() {
        save("https://example.com/pasta-recipe", title = "Pasta, the quick way")
        share("https://example.com/pasta-recipe")

        assertShowing("Already in your library", "Pasta, the quick way")
        tapButton("Save again")
        awaitText("Saved to Caddis")
        assertEquals(2, library().size)
    }

    @Test
    fun sharingALinkTwice_canOpenTheEarlierSaveInTheApp() {
        runBlockingOnboard()
        save("https://example.com/pasta-recipe", title = "Pasta, the quick way")
        share("https://example.com/pasta-recipe")
        awaitText("Already in your library")

        tapButton("Open earlier save")
        awaitText("WHY YOU KEPT IT")
        awaitText("Pasta, the quick way")
    }

    @Test
    fun sharingNothingUseful_savesNothing() {
        val scenario = share("   ")
        scenario.awaitFinished()
        assertTrue(library().isEmpty())
    }

    private fun runBlockingOnboard() = kotlinx.coroutines.runBlocking { settings.setOnboarded() }
}
