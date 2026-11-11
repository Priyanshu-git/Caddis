package com.nexxlabs.caddis.journeys

import android.content.ClipData
import android.content.ClipboardManager
import androidx.test.platform.app.InstrumentationRegistry
import com.nexxlabs.caddis.domain.details.LinkDetails
import com.nexxlabs.caddis.domain.model.IntentSource
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.ItemType
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Add inside the app: the plus button, a link or a note, and the sheet that confirms the save. */
@HiltAndroidTest
class AddJourneyTest : JourneyTest() {

    private fun openAdd() {
        tapDescribed("Add a link or note")
        awaitText("Continue")
    }

    private fun addLink(url: String) {
        openAdd()
        type("Link", url)
        tapButton("Continue")
    }

    @Test
    fun savingALink_keepsItAtOnce_andGuessesWhyFromTheLink() {
        launchApp()
        addLink("https://example.com/sourdough-for-beginners")

        assertShowing("Saved to Caddis", "Guessed from a webpage. Tap to change.")
        val saved = library().single()
        assertEquals("https://example.com/sourdough-for-beginners", saved.url)
        assertEquals(Intents.COOK, saved.intentId)
        assertEquals(IntentSource.GUESSED, saved.intentSource)
        assertTrue("details were asked for", saved.id in details.scheduled)
        // Nothing could be read, so the sheet shows a title built from the link.
        awaitText("example.com/sourdough-for-beginners", substring = true)
    }

    @Test
    fun theSaveSheet_takesOneTapToChangeWhy_andAThoughtForFutureYou() {
        launchApp()
        addLink("https://example.com/sourdough-for-beginners")
        awaitText("Saved to Caddis")

        tapButton("Watch")
        awaitText("Set by you.")
        type("A thought for future you", "for the long weekend")
        tapButton("Done")
        awaitNoText("Saved to Caddis")

        eventually("intent and reason kept") {
            library().single().let { it.intentId == Intents.WATCH && it.reason == "for the long weekend" }
        }
        assertEquals(IntentSource.USER, library().single().intentSource)
    }

    @Test
    fun theSaveSheet_canNameWhoSentIt_andFileItInACollection() {
        runBlocking { repo.createCollection("Dinner ideas") }
        launchApp()
        addLink("https://example.com/one-pan-pasta")
        awaitText("Saved to Caddis")

        tapButton("+ Who sent it")
        type("From", "Rohan")
        tapButton("+ Add to collection")
        tapButton("Dinner ideas")
        awaitText("Hide collections")
        tapButton("Done")
        awaitNoText("Saved to Caddis")

        eventually("sender and collection kept") {
            library().single().let { it.sourceFrom == "Rohan" && it.collectionIds.size == 1 }
        }
    }

    @Test
    fun theSaveSheet_letsYouRewriteTheTitle_andItIsYoursFromThen() {
        launchApp()
        addLink("https://example.com/one-pan-pasta")
        awaitText("Saved to Caddis")

        tapButton("Edit title")
        replaceText("Title", "Tuesday pasta")
        awaitText("Your title is kept when details load.")
        tapButton("Done")
        awaitNoText("Saved to Caddis")

        eventually("title kept") { library().single().title == "Tuesday pasta" }
    }

    @Test
    fun details_arriveInTheOpenSheet_replacingTheSkeleton() {
        val url = "https://example.com/slow-sourdough"
        details.hold()
        details.serve(url, LinkDetails(title = "Slow sourdough, fast hands", creator = "Oven Notes"))
        launchApp()
        addLink(url)

        awaitText("Loading details")
        details.release()
        awaitText("Slow sourdough, fast hands")
        awaitNoText("Loading details")
        eventually("details applied") { library().single().title == "Slow sourdough, fast hands" }
    }

    @Test
    fun aLinkAlreadySaved_isFlagged_andSavingAgainKeepsBothCopies() {
        save("https://example.com/pasta", title = "Pasta night")
        launchApp()
        addLink("example.com/pasta")

        assertShowing("Already in your library", "Pasta night")
        awaitText("Same link, saved on", substring = true)
        assertEquals(1, library().size)

        tapButton("Save again")
        awaitText("Saved to Caddis")
        assertEquals(2, library().size)
    }

    @Test
    fun aLinkAlreadySaved_canOpenTheEarlierSave() {
        save("https://example.com/pasta", title = "Pasta night")
        launchApp()
        addLink("https://example.com/pasta")
        awaitText("Already in your library")

        tapButton("Open earlier save")
        awaitText("WHY YOU KEPT IT")
        awaitText("Pasta night")
        assertEquals(1, library().size)
    }

    @Test
    fun continueWithNothingTyped_asksForALink() {
        launchApp()
        openAdd()
        tapButton("Continue")

        awaitText("Paste or type a link first.")
        assertTrue(library().isEmpty())
    }

    @Test
    fun typedText_thatIsNotALink_canBeSavedAsANoteInstead() {
        launchApp()
        addLink("buy milk and eggs")

        awaitText("This doesn’t seem like a web URL. Save as a note instead?")
        tapButton("Save as note")

        awaitText("Saved to Caddis")
        val note = library().single()
        assertEquals(ItemType.NOTE, note.type)
        assertEquals("buy milk and eggs", note.title)
    }

    @Test
    fun aNote_isWrittenAndKeptAsYouType() {
        launchApp()
        openAdd()
        tapTab("Note")
        type("Note title", "Packing list")
        tapButton("Continue")

        await(field("Note"))
        type("Note", "Passport and charger")
        awaitText("Saved")
        eventually("note body kept") { library().single().note.contains("Passport and charger") }
        assertEquals("Packing list", library().single().title)
        assertEquals(ItemType.NOTE, library().single().type)
    }

    @Test
    fun theClipboard_isOnlyOfferedAfterTappingAdd_andCanBeUsed() {
        val url = "https://example.com/from-the-clipboard"
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("link", url))
        }
        launchApp()
        openAdd()

        awaitText("From your clipboard")
        tapButton("Use")
        tapButton("Continue")
        awaitText("Saved to Caddis")
        assertEquals(url, library().single().url)
    }
}
