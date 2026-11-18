package com.nexxlabs.caddis.journeys

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.details.LinkDetails
import com.nexxlabs.caddis.domain.link.LinkCheck
import com.nexxlabs.caddis.domain.link.Links
import com.nexxlabs.caddis.domain.model.EditableField
import com.nexxlabs.caddis.domain.model.IntentSource
import com.nexxlabs.caddis.domain.model.Intents as IntentIds
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.ReturnRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The item page and the note editor: why you kept it, your own words, finishing, delaying and deleting. */
@HiltAndroidTest
class ItemJourneyTest : JourneyTest() {

    private val url = "https://example.com/sourdough-basics"
    private lateinit var id: String
    private var intentsStarted = false

    @Before
    fun seed() {
        id = save(url, "Sourdough basics", IntentIds.COOK, reason = "for the long weekend").id
    }

    @After
    fun releaseIntents() {
        if (intentsStarted) Intents.release()
    }

    private fun stubExternalIntents() {
        Intents.init()
        intentsStarted = true
        intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    private fun openItem() {
        launchApp(route = "item/$id")
        awaitText("WHY YOU KEPT IT")
    }

    @Test
    fun theItemPage_showsWhatYouKept_andWhy() {
        openItem()

        assertShowing("Sourdough basics", "“for the long weekend”", "Open", "Made it", "Delete")
        assertTrue(exists(described("Intent: Cook. Change")))
        assertTrue(exists(described("Edit details")))
        assertTrue(exists(described("Share link")))
        assertTrue(exists(described("Bring back later")))
    }

    @Test
    fun changingWhy_isOneTap_andBecomesYourChoice() {
        openItem()

        tapDescribed("Intent: Cook. Change")
        awaitText("Why are you keeping it?")
        tap(hasText("Watch") and role(Role.RadioButton))
        eventually("intent saved") { item(id).intentId == IntentIds.WATCH }
        tapButton("Done")
        assertEquals(IntentSource.USER, item(id).intentSource)
        // The Done button now speaks the new intent.
        awaitText("Watched")
        assertTrue(analytics.events.any { it is AnalyticsEvent.IntentChanged && it.toIntent == IntentIds.WATCH })
    }

    @Test
    fun theReason_canBeRewritten() {
        openItem()

        tapButton("Edit")
        replaceText("Reason", "to bake with Mum")
        tapButton("Save")

        awaitText("“to bake with Mum”")
        eventually("reason saved") { item(id).reason == "to bake with Mum" }
    }

    @Test
    fun editDetails_keepsYourWords_whenDetailsRefresh() {
        openItem()

        tapDescribed("Edit details")
        awaitText("Your words are kept when details refresh.", substring = true)
        replaceText("Title", "Better bread")
        replaceText("By", "Oven Notes")
        tapButton("Save")

        awaitText("Better bread")
        eventually("edits saved") { item(id).let { it.title == "Better bread" && it.creator == "Oven Notes" } }
        assertTrue(EditableField.TITLE in item(id).userEdited)

        // A refresh brings in what the person did not write, and leaves their words alone.
        details.serve(url, LinkDetails(title = "Title from the site", description = "From the site"))
        tapButton("Refresh")
        awaitText("Refreshing details. Your edits are kept.")
        eventually("details applied") { item(id).description == "From the site" }
        assertEquals("Better bread", item(id).title)
        assertEquals("Oven Notes", item(id).creator)
    }

    @Test
    fun editDetails_canMoveTheLink_andFetchesAgain() {
        openItem()
        val before = details.scheduled.size

        tapDescribed("Edit details")
        replaceText("Link", "https://example.com/sourdough-advanced")
        awaitText("Caddis will read the new link and fetch its details again.")
        tapButton("Save")

        awaitText("Link changed. Fetching its details.")
        eventually("link moved") { item(id).url == "https://example.com/sourdough-advanced" }
        assertTrue(details.scheduled.size > before)
    }

    @Test
    fun editDetails_refusesALinkThatIsAlreadySaved() {
        save("https://example.com/other", "Other save")
        openItem()

        tapDescribed("Edit details")
        replaceText("Link", "https://example.com/other")
        tapButton("Save")

        awaitText("Another save already has this link: “Other save”.")
        assertEquals(url, item(id).url)
    }

    @Test
    fun editDetails_refusesSomethingThatIsNotALink() {
        openItem()

        tapDescribed("Edit details")
        replaceText("Link", "buy milk")
        tapButton("Save")

        awaitText((Links.check("buy milk") as LinkCheck.Invalid).message)
        assertEquals(url, item(id).url)
    }

    @Test
    fun done_thenMarkActive() {
        openItem()

        tapButton("Made it")
        awaitText("Marked “Made it”.")
        eventually("done") { item(id).status == ItemStatus.DONE }
        awaitText("Mark active")

        tapButton("Mark active")
        eventually("active again") { item(id).status == ItemStatus.ACTIVE }
        awaitText("Made it")
    }

    @Test
    fun delete_movesToTrash_withUndo() {
        openItem()

        tapButton("Delete")
        awaitText("Moved “Sourdough basics” to Trash")
        awaitText("Today")
        eventually("deleted") { item(id).status == ItemStatus.DELETED }

        undo()
        eventually("restored") { item(id).status == ItemStatus.ACTIVE }
    }

    @Test
    fun bringBackLater_setsWhenItReturns_withUndo() {
        openItem()

        tapDescribed("Bring back later")
        awaitText("Bring it back")
        tapText("This weekend")
        awaitText("Back Saturday morning.")
        eventually("weekend") { item(id).returnRule == ReturnRule.WEEKEND }
        assertNotNull(item(id).returnAt)

        undo()
        eventually("back to the intent's rule") { item(id).returnAt == null }
    }

    @Test
    fun open_sendsTheSavedLinkToTheBrowserOrItsApp() {
        stubExternalIntents()
        openItem()

        tapButton("Open")

        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(url)))
        eventually("opened is recorded") { item(id).lastOpenedAt != null }
    }

    @Test
    fun shareLink_opensTheShareSheet() {
        stubExternalIntents()
        openItem()

        tapDescribed("Share link")

        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    @Test
    fun aNote_isWrittenRightOnTheItemPage_withoutLeavingIt() {
        openItem()

        await(field("Note"))
        type("Note", "Use the wild starter")

        eventually("note kept") { item(id).note == "Use the wild starter" }
        // Still on the item page, with the text in its box.
        assertTrue(exists(hasText("WHY YOU KEPT IT")))
    }

    @Test
    fun aStandaloneNote_hasATitle_anIntent_andACollection() {
        val note = saveNote("Gift ideas", "scarf")
        launchApp(route = "note/${note.id}")

        await(field("Note title"))
        // The intent and an empty collection chip sit right under the title.
        assertShowing("Just keep", "Collection")
        assertTrue(exists(described("Add to a collection")))
        assertTrue(!exists(hasText("Edited visually, stored as Markdown.")))
        replaceText("Note title", "Gift list")
        eventually("title kept") { item(note.id).title == "Gift list" }
    }

    @Test
    fun theItemPage_listsYourCollectionsAsChips_andMovesASaveBetweenThem() {
        val a = runBlocking { repo.createCollection("Dinner ideas") } as com.nexxlabs.caddis.data.LibraryRepository.NameResult.Ok
        runBlocking { repo.createCollection("Gifts") }
        runBlocking { repo.setInCollection(id, a.id, true) }
        openItem()

        // Resting: only the collections it is in, and nothing to change until Edit is pressed.
        assertShowing("COLLECTIONS", "Dinner ideas")
        assertTrue(!exists(hasText("Gifts")))
        assertTrue(!exists(hasText("New")))

        tapDescribed("Edit collections")
        assertShowing("New", "Gifts", "Dinner ideas")
        tapButton("Gifts")
        eventually("filed in Gifts") { library().first { it.id == id }.collectionIds.size == 2 }
        tapButton("Dinner ideas")
        eventually("taken out of Dinner ideas") { library().first { it.id == id }.collectionIds.let { ids -> a.id !in ids && ids.size == 1 } }

        tapDescribed("Finish editing collections")
        awaitGone(hasText("New"))
        assertShowing("Gifts")
        assertTrue(!exists(hasText("Dinner ideas")))
    }

    @Test
    fun theItemPage_canMakeANewCollection_andUndoIt() {
        openItem()

        tapDescribed("Add to a collection")
        tapButton("New")
        type("New collection name", "Weekend cooking")
        tapDescribed("Create collection")
        eventually("made and filed") { library().first { it.id == id }.collectionIds.size == 1 }
        awaitText("Created “Weekend cooking” and added.")

        tapText("Undo")
        eventually("made and then undone") {
            library().first { it.id == id }.collectionIds.isEmpty() && runBlocking { repo.observeCollections().first() }.isEmpty()
        }
    }
}
