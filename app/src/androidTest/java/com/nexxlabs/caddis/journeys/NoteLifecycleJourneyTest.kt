package com.nexxlabs.caddis.journeys

import androidx.compose.ui.test.hasText
import com.nexxlabs.caddis.domain.model.ItemType
import com.nexxlabs.caddis.domain.model.Intents
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A note from start to finish: creating it, saving as you type, leaving and coming back, its title,
 * intent and collections, and notes on a saved link.
 */
@HiltAndroidTest
class NoteLifecycleJourneyTest : NoteJourneyBase() {

    /** Reaches a note the way a person does: the Library tab, then the note's row. */
    private fun openFromLibrary(title: String) {
        start()
        goTo("Library")
        awaitText(title)
        tapText(title)
        focusBody()
    }

    // ---------- creating ----------

    @Test
    fun addingANoteWithATitle_opensTheEditorOnIt() {
        start()
        tapDescribed("Add a link or note")
        tapTab("Note")
        type("Note title", "Packing list")
        tapButton("Continue")

        await(field("Note"))
        assertShowing("Write a note", "Saved")
        val note = library().single()
        assertEquals(ItemType.NOTE, note.type)
        assertEquals("Packing list", note.title)
        assertEquals("", note.note)
    }

    @Test
    fun addingANoteWithoutATitle_isShownAsUntitled() {
        start()
        tapDescribed("Add a link or note")
        tapTab("Note")
        tapButton("Continue")

        await(field("Note"))
        assertShowing("Untitled note")
        assertEquals(ItemType.NOTE, library().single().type)
    }

    @Test
    fun aNewNote_startsEmpty_andNothingIsWrittenUntilYouType() {
        val note = saveNote("Packing list")
        val revision = item(note.id).revision
        openEditor(note.id)
        awaitText("Write a note")
        leaveEditor()
        awaitNoText("Write a note")
        assertEquals("opening and leaving writes nothing", revision, item(note.id).revision)
    }

    // ---------- saving as you type ----------

    @Test
    fun typing_showsSaving_thenSaved_andTheNoteIsKept() {
        val id = openNote()
        commit("Passport and charger")
        awaitText("Saving…")
        awaitText("Saved")
        assertStored(id, "Passport and charger")
    }

    @Test
    fun leavingRightAfterTyping_keepsTheLastWords() {
        val id = openNote()
        commit("Passport")
        leaveEditor()
        assertStored(id, "Passport")
    }

    @Test
    fun openingASavedNote_andLeaving_leavesItExactlyAsItWas() {
        val id = openNote("**bold** and *italic*\n- one\n- two")
        val before = item(id)
        scenario.close()
        openEditor(id)
        leaveEditor()
        Thread.sleep(1_200) // longer than the autosave pause, so a stray write would have happened by now
        val after = item(id)
        assertEquals(before.note, after.note)
        assertEquals(before.revision, after.revision)
    }

    @Test
    fun whatYouWrote_isThereWhenYouComeBack() {
        val id = openNote()
        commit("first thought"); enter(); commit("second thought")
        assertStored(id, "first thought\nsecond thought")
        scenario.close()

        openEditor(id)
        assertEquals("first thought second thought", visible())
    }

    @Test
    fun writingAgainAfterComingBack_keepsEverything() {
        val id = openNote()
        commit("one")
        assertStored(id, "one")
        scenario.close()

        openEditor(id)
        caretToEnd()
        enter()
        commit("two")
        assertStored(id, "one\ntwo")
        scenario.close()

        openEditor(id)
        caretToEnd()
        enter()
        commit("three")
        assertStored(id, "one\ntwo\nthree")
    }

    @Test
    fun formattingSurvivesComingBack() {
        val id = openNote()
        tool("Bold"); commit("strong"); tool("Bold"); commit(" and plain")
        assertStored(id, "**strong** and plain")
        scenario.close()

        openEditor(id)
        select(2)
        assertTrue("bold is lit inside the bold word", lit("Bold"))
        select(14)
        assertFalse("and not in the plain words", lit("Bold"))
    }

    @Test
    fun switchingBoldAndItalicOnAndOffWhileTyping_isKeptWhenReopenedFromTheLibrary() {
        // Bold on, "a"; italic on, "b"; bold off, "c"; italic off, "d"; back to the Library; open the note again.
        val id = saveNote("Styles").id
        openFromLibrary("Styles")
        tool("Bold"); commit("a")
        tool("Italic"); commit("b")
        tool("Bold"); commit("c")
        tool("Italic"); commit("d")
        assertStored(id, "**a*b***<!---->*c*d")
        leaveEditor()
        awaitText("Styles")
        tapText("Styles")
        focusBody()

        assertEquals("abcd", visible())
        select(1); assertTrue("a is bold", lit("Bold")); assertFalse("a is not italic", lit("Italic"))
        select(2); assertTrue("b is bold", lit("Bold")); assertTrue("b is italic", lit("Italic"))
        select(3); assertFalse("c is not bold", lit("Bold")); assertTrue("c is italic", lit("Italic"))
        select(4); assertFalse("d is not bold", lit("Bold")); assertFalse("d is not italic", lit("Italic"))
    }

    @Test
    fun turningTheScreen_rightAfterTyping_keepsTheText() {
        val id = openNote()
        commit("Passport")
        scenario.recreate()
        await(field("Note"))
        assertEquals("Passport", visible())
        assertStored(id, "Passport")
    }

    @Test
    fun turningTheScreen_thenTypingMore_doesNotLoseWhatCameBefore() {
        val id = openNote()
        commit("Passport")
        scenario.recreate()
        focusBody()
        caretToEnd()
        commit(" and charger")
        assertStored(id, "Passport and charger")
    }

    // ---------- the title ----------

    @Test
    fun theTitle_canBeChanged_andIsKept() {
        val id = openNote(title = "Packing list")
        replaceText("Note title", "Trip packing")
        eventually("title kept") { item(id).title == "Trip packing" }
    }

    @Test
    fun changingTheTitle_doesNotTouchTheBody() {
        val id = openNote()
        commit("Passport")
        replaceText("Note title", "Trip packing")
        assertStored(id, "Passport")
        eventually("title kept") { item(id).title == "Trip packing" }
    }

    @Test
    fun aClearedTitle_showsAsUntitled_inTheLibrary() {
        val id = saveNote("Packing list").id
        openFromLibrary("Packing list")
        replaceText("Note title", "")
        eventually("title cleared") { item(id).title.isBlank() }
        leaveEditor()
        awaitText("Untitled note")
    }

    // ---------- intent and collections ----------

    @Test
    fun aNote_canBeGivenAnIntent() {
        val id = openNote()
        tapText("Just keep")
        awaitText("Why are you keeping it?")
        tapText("Cook")
        tapButton("Done")

        eventually("intent kept") { item(id).intentId == Intents.COOK }
        awaitText("Cook")
    }

    @Test
    fun aNote_canBePutInACollection_andTakenOut() {
        runBlocking { repo.createCollection("Gifts") }
        val id = openNote()
        tapDescribed("Add to a collection")
        tapText("Gifts")
        eventually("added to Gifts") { library().first { it.id == id }.collectionIds.isNotEmpty() }

        tapText("Gifts")
        eventually("taken out of Gifts") { library().first { it.id == id }.collectionIds.isEmpty() }
    }

    // ---------- a note on a saved link ----------

    @Test
    fun aLinksNote_showsOnItsItemPage_afterWriting() {
        val link = save("https://example.com/sourdough-basics", "Sourdough basics")
        start(route = "item/${link.id}")
        awaitText("WHY YOU KEPT IT")
        tapText("Add a note")
        await(field("Note"))
        focusBody()
        commit("Use the wild starter"); enter(); commit("Bake at 230")
        assertStored(link.id, "Use the wild starter\nBake at 230")
        leaveEditor()

        awaitText("Use the wild starter Bake at 230")
        assertFalse(exists(hasText("Add a note")))
    }

    @Test
    fun aLinksNoteWithMarksInIt_showsThemAsTyped_andIsFoundByItsWords() {
        val link = save("https://example.com/sourdough-basics", "Sourdough basics")
        start(route = "item/${link.id}")
        tapText("Add a note")
        focusBody()
        commit("2*3*4 equals <24>")
        assertStored(link.id, "2\\*3\\*4 equals \\<24>")
        leaveEditor()
        awaitText("2*3*4 equals <24>")
    }

    @Test
    fun aLinksNote_canBeEditedFromThePreview() {
        val link = save("https://example.com/sourdough-basics", "Sourdough basics")
        runBlocking { repo.setNote(link.id, "Use the wild starter") }
        start(route = "item/${link.id}")
        awaitText("Use the wild starter")

        tapText("Use the wild starter")
        focusBody()
        caretToEnd()
        commit(" today")
        assertStored(link.id, "Use the wild starter today")
        leaveEditor()
        awaitText("Use the wild starter today")
    }

    @Test
    fun clearingALinksNote_bringsBackTheHint() {
        val link = save("https://example.com/sourdough-basics", "Sourdough basics")
        runBlocking { repo.setNote(link.id, "Use the wild starter") }
        start(route = "item/${link.id}")
        tapText("Use the wild starter")
        focusBody()
        select(0, "Use the wild starter".length)
        deleteSelection()
        assertStored(link.id, "")
        leaveEditor()

        awaitText("Add a note")
    }

    @Test
    fun aNoteOfOnlyBlankLines_showsTheHintOnTheItemPage() {
        val link = save("https://example.com/sourdough-basics", "Sourdough basics")
        start(route = "item/${link.id}")
        tapText("Add a note")
        focusBody()
        enter(); enter()
        leaveEditor()

        awaitText("Add a note")
    }

    @Test
    fun aLinksNote_keepsItsLinkDetails() {
        val link = save("https://example.com/sourdough-basics", "Sourdough basics")
        start(route = "note/${link.id}")
        focusBody()
        commit("Use the wild starter")
        assertStored(link.id, "Use the wild starter")
        val after = item(link.id)
        assertEquals("Sourdough basics", after.title)
        assertEquals(ItemType.LINK, after.type)
        assertEquals(link.url, after.url)
    }

    // ---------- finding it again ----------

    @Test
    fun aNoteYouJustWrote_canBeFoundInTheLibrary_byItsWords() {
        val id = saveNote("Gifts").id
        openFromLibrary("Gifts")
        commit("lanterns and ribbon")
        assertStored(id, "lanterns and ribbon")
        leaveEditor()

        awaitText("Search your saves")
        tapDescribed("Search your saves")
        type("Search saves", "lanterns")
        awaitText("1 result")
        assertShowing("Gifts", "Matched in your note")
    }

    @Test
    fun aNoteInTheLibrary_opensInTheEditor_withItsText() {
        val note = saveNote("Gift ideas", "scarf, mug, a good pen")
        start()
        goTo("Library")
        awaitText("Gift ideas")
        tapText("Gift ideas")

        await(field("Note"))
        assertEquals("scarf, mug, a good pen", visible())
    }

    @Test
    fun editingANote_thenSearchingForTheOldWords_findsNothing() {
        val id = saveNote("Gifts", "scarf").id
        openFromLibrary("Gifts")
        caretToEnd()
        backspace(5)
        commit("candle")
        assertStored(id, "candle")
        leaveEditor()

        awaitText("Search your saves")
        tapDescribed("Search your saves")
        type("Search saves", "scarf")
        awaitText("Nothing matches. Try fewer words.")
    }
}
