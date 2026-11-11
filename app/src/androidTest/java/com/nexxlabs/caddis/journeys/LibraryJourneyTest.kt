package com.nexxlabs.caddis.journeys

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performImeAction
import com.nexxlabs.caddis.domain.model.Intents
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Library: everything you kept, searched and filtered on the phone, and grouped into collections. */
@HiltAndroidTest
class LibraryJourneyTest : JourneyTest() {

    private lateinit var sourdoughId: String
    private lateinit var rustId: String

    @Before
    fun seed() {
        sourdoughId = save("https://example.com/sourdough-basics", "Sourdough basics", Intents.COOK).id
        testClock.advance(java.time.Duration.ofMinutes(1))
        rustId = save("https://www.youtube.com/watch?v=abc123", "Rust ownership explained", Intents.LEARN).id
        // A save is named by its host until its page is read; the page says it is YouTube.
        runBlocking { repo.applyDetails(rustId, com.nexxlabs.caddis.domain.details.LinkDetails(siteName = "YouTube")) }
        testClock.advance(java.time.Duration.ofMinutes(1))
        saveNote("Gift ideas", "scarf, mug, a good pen")
    }

    /** The library read, which (unlike `repo.get`) includes which collections a save is in. */
    private fun inLibrary(id: String) = library().first { it.id == id }

    private fun openLibrary() {
        launchApp()
        goTo("Library")
        awaitText("Search your saves")
    }

    private fun search(words: String) {
        tapDescribed("Search your saves")
        type("Search saves", words)
    }

    @Test
    fun theLibrary_listsEverythingYouKept() {
        openLibrary()

        assertShowing("3 saves and notes · newest first", "Sourdough basics", "Rust ownership explained", "Gift ideas")
    }

    @Test
    fun search_findsWordsInTitles_andSaysWhere() {
        openLibrary()
        search("sourdough")

        awaitText("1 result")
        assertShowing("Sourdough basics", "Matched in the title")
        assertTrue(!exists(hasText("Rust ownership explained")))
    }

    @Test
    fun search_looksInNotesToo() {
        openLibrary()
        search("scarf")

        awaitText("1 result")
        assertShowing("Gift ideas", "Matched in your note")
    }

    @Test
    fun search_withNoMatch_saysSo_andCancelGoesBack() {
        openLibrary()
        search("zzzz")

        awaitText("Nothing matches. Try fewer words.")
        tapButton("Cancel")
        awaitText("3 saves and notes · newest first")
    }

    @Test
    fun search_remembersRecentWords_untilTheAppCloses() {
        openLibrary()
        search("sourdough")
        awaitText("1 result")
        compose.onNode(field("Search saves")).performImeAction()
        tapButton("Cancel")

        tapDescribed("Search your saves")
        awaitText("RECENT")
        assertShowing("sourdough")
        // The words stay in memory only, and never go to analytics.
        assertTrue(analytics.events.none { e -> e.params().values.any { it.contains("sourdough") } })
    }

    @Test
    fun filter_byIntent_narrowsTheList_andResetClearsIt() {
        openLibrary()

        tapButton("Filter")
        awaitText("Reset")
        tapButton("Cook")
        awaitText("Show 1 save")
        tapButton("Show 1 save")
        awaitText("1 save for Cook · newest first")
        assertShowing("Sourdough basics")
        assertTrue(!exists(hasText("Rust ownership explained")))

        tapButton("Filter · 1")
        tapButton("Reset")
        tapButton("Show 3 saves")
        awaitText("3 saves and notes · newest first")
    }

    @Test
    fun theChipRow_filtersByPlatform_andTappingAgainClearsIt() {
        openLibrary()

        tapButton("YouTube")
        awaitText("1 save from YouTube · newest first")
        assertShowing("Rust ownership explained")
        assertTrue(!exists(hasText("Sourdough basics")))

        tapButton("YouTube")
        awaitText("3 saves and notes · newest first")
    }

    @Test
    fun aFilterThatMatchesNothing_offersToClearIt() {
        runBlocking { repo.markDone(sourdoughId) }
        openLibrary()

        tapButton("Filter")
        tapButton("Cook")
        // Cook saves are all done, and Active hides them.
        tapButton("Active")
        awaitText("Nothing matches these filters.")
        tapButton("Show 0 saves")
        tapButton("Clear filters")
        awaitText("3 saves and notes · newest first")
    }

    @Test
    fun aFinishedSave_staysInTheLibrary_taggedWithItsDoneWord() {
        runBlocking { repo.markDone(rustId) }
        openLibrary()

        assertShowing("Rust ownership explained", "Learned")
    }

    @Test
    fun collections_canBeCreated_listedWithCounts_andOpenedAsAShelf() {
        openLibrary()
        tapTab("Collections")
        tapButton("Manage collections")

        type("New collection name", "Weekend cooking")
        tapButton("Create")
        assertShowing("Weekend cooking", "0 saves")
        back()

        awaitText("Weekend cooking")
        // File the sourdough save under it from its own page.
        tapTab("All saves")
        tapText("Sourdough basics")
        awaitText("WHY YOU KEPT IT")
        tapButton("+ Add")
        tapButton("Weekend cooking")
        eventually("filed") { inLibrary(sourdoughId).collectionIds.size == 1 }
        back()

        tapTab("Collections")
        awaitText("1 save")
        tapText("Weekend cooking")
        assertShowing("Weekend cooking", "1 save", "Sourdough basics")
        assertTrue(!exists(hasText("Rust ownership explained")))
    }

    @Test
    fun collections_cannotShareAName() {
        openLibrary()
        tapTab("Collections")
        tapButton("Manage collections")

        type("New collection name", "Reading list")
        tapButton("Create")
        awaitText("Reading list")
        type("New collection name", "Reading list")
        tapButton("Create")

        awaitText("You already have a collection with that name.")
    }

    @Test
    fun collections_canBeRenamed_andDeletedWithoutLosingTheirSaves() {
        val id = (runBlocking { repo.createCollection("Weekend cooking") } as com.nexxlabs.caddis.data.LibraryRepository.NameResult.Ok).id
        runBlocking { repo.setInCollection(sourdoughId, id, true) }
        openLibrary()
        tapTab("Collections")
        tapButton("Manage collections")

        tapDescribed("Rename or delete Weekend cooking")
        replaceText("Collection name", "Slow cooking")
        tapButton("Save")
        awaitText("Slow cooking")

        tapDescribed("Rename or delete Slow cooking")
        tapButton("Delete")
        awaitText("Sure? Saves stay")
        tapButton("Sure? Saves stay")

        awaitNoText("Slow cooking")
        eventually("collection gone") { runBlocking { repo.observeCollections().first() }.isEmpty() }
        assertEquals(3, library().size)
        assertTrue(inLibrary(sourdoughId).collectionIds.isEmpty())
    }
}
