package com.nexxlabs.caddis.journeys

import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Writing in the note editor: Return, formatting, the toolbar lighting up where the caret is, and
 * editing a note that already exists. Typing goes through the editor's input connection a call at a time,
 * the way a keyboard sends it.
 */
@HiltAndroidTest
class NoteEditorJourneyTest : NoteJourneyBase() {

    // ---------- Return ----------

    @Test
    fun return_afterAWord_startsANewLine_andDoesNotRepeatTheWord() {
        val id = openNote()
        commit("hello")
        enter()
        assertStored(id, "hello\n")
        assertEquals(1, occurrences(storedNote(id), "hello"))

        commit("world")
        assertStored(id, "hello\nworld")
    }

    @Test
    fun return_whileTheWordIsStillBeingComposed_doesNotRepeatIt() {
        val id = openNote()
        composing("hello")
        enter()
        assertStored(id, "hello\n")
        assertEquals(1, occurrences(shown(), "hello"))
    }

    @Test
    fun return_afterTheWordIsFinished_doesNotRepeatIt() {
        val id = openNote()
        composing("hello")
        finishWord()
        enter()
        assertStored(id, "hello\n")
        assertEquals(1, occurrences(shown(), "hello"))
    }

    @Test
    fun return_sentAsANewlineCharacter_doesNotRepeatTheWord() {
        val id = openNote()
        commit("hello")
        commit("\n")
        assertStored(id, "hello\n")
        assertEquals(1, occurrences(shown(), "hello"))
    }

    @Test
    fun return_sentTogetherWithTheWord_doesNotRepeatIt() {
        val id = openNote()
        commit("hello\n")
        assertStored(id, "hello\n")
        assertEquals(1, occurrences(shown(), "hello"))
    }

    @Test
    fun return_afterEveryWordOfSeveral_keepsEachLineOnce() {
        val id = openNote()
        listOf("alpha", "beta", "gamma").forEach { composing(it); finishWord(); enter() }
        assertStored(id, "alpha\nbeta\ngamma\n")
        listOf("alpha", "beta", "gamma").forEach { assertEquals(it, 1, occurrences(shown(), it)) }
    }

    @Test
    fun return_afterAPhrase_keepsItOnce() {
        val id = openNote()
        typeSlowly("buy milk and eggs")
        enter()
        assertStored(id, "buy milk and eggs\n")
        assertEquals(1, occurrences(shown(), "buy milk and eggs"))
    }

    @Test
    fun return_typedSlowlyLetterByLetter_andRepeated_keepsEveryLineOnce() {
        val id = openNote()
        typeSlowly("first line"); enter()
        typeSlowly("second line"); enter()
        typeSlowly("third line")
        assertStored(id, "first line\nsecond line\nthird line")
    }

    @Test
    fun return_twice_leavesABlankLine() {
        val id = openNote()
        commit("hello"); enter(); enter(); commit("x")
        assertStored(id, "hello\n\nx")
    }

    @Test
    fun return_inTheMiddleOfAWord_splitsTheLine() {
        val id = openNote("helloworld")
        select(5)
        enter()
        assertStored(id, "hello\nworld")
        assertEquals(1, occurrences(shown(), "hello"))
        assertEquals(1, occurrences(shown(), "world"))
    }

    @Test
    fun return_atTheEndOfASavedLine_addsALineBelow() {
        val id = openNote("first line")
        caretToEnd()
        enter()
        commit("second")
        assertStored(id, "first line\nsecond")
    }

    @Test
    fun return_atTheStartOfALine_pushesTheTextDown() {
        val id = openNote("hello")
        select(0)
        enter()
        assertStored(id, "\nhello")
        assertEquals(1, occurrences(shown(), "hello"))
    }

    @Test
    fun return_afterABoldWord_keepsTheNextLineBold() {
        val id = openNote()
        tool("Bold")
        commit("hello"); enter(); commit("next")
        assertStored(id, "**hello**\n**next**")
    }

    @Test
    fun return_inABulletList_addsAnItem() {
        val id = openNote()
        tool("Bulleted list")
        typeSlowly("one"); enter(); typeSlowly("two")
        assertStored(id, "- one\n- two")
    }

    @Test
    fun return_onAnEmptyBullet_leavesTheList() {
        val id = openNote()
        tool("Bulleted list")
        typeSlowly("one"); enter(); enter(); typeSlowly("plain")
        assertStored(id, "- one\nplain")
        assertFalse(lit("Bulleted list"))
    }

    @Test
    fun return_inANumberedList_numbersTheNextItem() {
        val id = openNote()
        tool("Numbered list")
        typeSlowly("one"); enter(); typeSlowly("two"); enter(); typeSlowly("three")
        assertStored(id, "1. one\n2. two\n3. three")
    }

    @Test
    fun aWordPutInAtOnce_insideABullet_isKeptWhole() {
        // What a tapped suggestion or a paste does: the whole word arrives in one go.
        val id = openNote()
        tool("Bulleted list")
        commit("one")
        assertStored(id, "- one")
    }

    @Test
    fun aWordPutInAtOnce_insideANumberedItem_isKeptWhole() {
        val id = openNote()
        tool("Numbered list")
        commit("one")
        assertStored(id, "1. one")
    }

    @Test
    fun backspaceAtTheStartOfALine_joinsItToThePreviousOne() {
        val id = openNote("hello\nworld")
        select(6)
        backspace()
        assertStored(id, "helloworld")
    }

    // ---------- formatting ----------

    @Test
    fun bold_isKeptAsMarkdown() {
        val id = openNote()
        tool("Bold"); commit("hello")
        assertStored(id, "**hello**")
    }

    @Test
    fun italic_isKeptAsMarkdown() {
        val id = openNote()
        tool("Italic"); commit("hello")
        assertStored(id, "*hello*")
    }

    @Test
    fun code_isKeptAsMarkdown() {
        val id = openNote()
        tool("Code"); commit("val x")
        assertStored(id, "`val x`")
    }

    @Test
    fun bulletedList_isKeptAsMarkdown() {
        val bullets = openNote()
        tool("Bulleted list"); typeSlowly("one"); enter(); typeSlowly("two")
        assertStored(bullets, "- one\n- two")
    }

    @Test
    fun turningBoldOffMidSentence_endsTheBoldText() {
        val id = openNote()
        tool("Bold"); commit("strong")
        tool("Bold"); commit(" plain")
        assertStored(id, "**strong** plain")
    }

    @Test
    fun boldAppliesToTheSelectedWordsOnly() {
        val id = openNote("hello world")
        select(0, 5)
        tool("Bold")
        assertStored(id, "**hello** world")
    }

    @Test
    fun italicAppliesToTheSelectedWordsOnly() {
        val id = openNote("hello world")
        select(6, 11)
        tool("Italic")
        assertStored(id, "hello *world*")
    }

    @Test
    fun boldOnSelectedBoldText_removesIt() {
        val id = openNote("**hello** world")
        select(0, 5)
        tool("Bold")
        assertStored(id, "hello world")
    }

    @Test
    fun aBulletButtonPressedTwice_turnsTheListOff() {
        val id = openNote()
        tool("Bulleted list"); tool("Bulleted list")
        typeSlowly("plain")
        assertStored(id, "plain")
    }

    // ---------- the toolbar lights up where the caret is ----------

    @Test
    fun toolbar_startsWithNothingLit() {
        openNote()
        listOf("Bold", "Italic", "Bulleted list", "Numbered list", "Code").forEach { assertFalse("$it should not be lit", lit(it)) }
    }

    @Test
    fun bold_lightsUpAsSoonAsItIsTapped_andGoesOutWhenTappedAgain() {
        openNote()
        tool("Bold")
        assertTrue(lit("Bold"))
        assertFalse(lit("Italic"))
        tool("Bold")
        assertFalse(lit("Bold"))
    }

    @Test
    fun italic_lightsUpAsSoonAsItIsTapped_andGoesOutWhenTappedAgain() {
        openNote()
        tool("Italic")
        assertTrue(lit("Italic"))
        assertFalse(lit("Bold"))
        tool("Italic")
        assertFalse(lit("Italic"))
    }

    @Test
    fun bold_staysLitWhileTypingBoldText() {
        openNote()
        tool("Bold")
        commit("hello")
        assertTrue(lit("Bold"))
    }

    @Test
    fun bold_goesOutWhenTheCaretIsInPlainText_andComesBackInsideBoldText() {
        openNote("plain **bold** plain")
        // The editor shows "plain bold plain": bold is characters 6 to 10.
        select(2)
        assertFalse("plain text at the start", lit("Bold"))
        select(8)
        assertTrue("inside the bold word", lit("Bold"))
        select(14)
        assertFalse("plain text after it", lit("Bold"))
        select(7)
        assertTrue("moving back in", lit("Bold"))
    }

    @Test
    fun italic_followsTheCaretToo() {
        openNote("plain *slanted* plain")
        select(2)
        assertFalse(lit("Italic"))
        select(9)
        assertTrue(lit("Italic"))
        assertFalse(lit("Bold"))
    }

    @Test
    fun boldAndItalic_bothLightForBoldItalicText() {
        openNote("a ***both*** b")
        select(4)
        assertTrue(lit("Bold"))
        assertTrue(lit("Italic"))
    }

    @Test
    fun bold_lightsForASelectionOfBoldText() {
        openNote("plain **bold** plain")
        select(6, 10)
        assertTrue(lit("Bold"))
    }

    @Test
    fun bulletsAndNumbers_lightWhileTheCaretIsInAList_andGoOutOutsideIt() {
        openNote()
        tool("Bulleted list")
        assertTrue(lit("Bulleted list"))
        assertFalse(lit("Numbered list"))
        commit("one")
        assertTrue(lit("Bulleted list"))
        tool("Bulleted list")
        assertFalse(lit("Bulleted list"))

        tool("Numbered list")
        assertTrue(lit("Numbered list"))
        assertFalse(lit("Bulleted list"))
    }

    @Test
    fun code_lightsWhileTheCaretIsInCode() {
        openNote()
        tool("Code")
        assertTrue(lit("Code"))
        commit("val x")
        assertTrue(lit("Code"))
        tool("Code")
        assertFalse(lit("Code"))
    }

    @Test
    fun theButtonsStayLit_whenTheNoteIsOpenedAgainWithTheCaretInBoldText() {
        val id = openNote("**strong** words")
        scenario.close()
        openEditor(id)
        select(3)
        assertTrue(lit("Bold"))
        select(12)
        assertFalse(lit("Bold"))
    }

    // ---------- editing a note that exists ----------

    @Test
    fun appendingToASavedNote_keepsTheOldTextAndAddsTheNew() {
        val id = openNote("Passport")
        caretToEnd()
        commit(" and charger")
        assertStored(id, "Passport and charger")
    }

    @Test
    fun insertingInTheMiddle_keepsBothSides() {
        val id = openNote("Passport charger")
        select(8)
        commit(" and")
        assertStored(id, "Passport and charger")
    }

    @Test
    fun deletingAWord_removesOnlyThatWord() {
        val id = openNote("Passport and charger")
        select(8, 12)
        deleteSelection()
        assertStored(id, "Passport charger")
    }

    @Test
    fun replacingTheSelection_swapsTheText() {
        val id = openNote("Passport and charger")
        select(13, 20)
        commit("adapter")
        assertStored(id, "Passport and adapter")
    }

    @Test
    fun backspace_removesTheLastLetter() {
        val id = openNote("Passport")
        caretToEnd()
        backspace()
        assertStored(id, "Passpor")
    }

    @Test
    fun deletingEverything_savesAnEmptyNote_andTheHintComesBack() {
        val id = openNote("Passport and charger")
        select(0, 20)
        deleteSelection()
        assertStored(id, "")
        awaitText("Write a note")
    }

    @Test
    fun afterDeletingEverything_youCanWriteAgain_andReturnStillWorks() {
        val id = openNote("Passport")
        select(0, 8)
        deleteSelection()
        commit("hello"); enter(); commit("world")
        assertStored(id, "hello\nworld")
    }

    @Test
    fun editingBoldText_keepsItBold() {
        val id = openNote("**strong** words")
        select(3)
        commit("X")
        assertStored(id, "**strXong** words")
    }

    @Test
    fun aSavedNote_isShownWithoutMarkdownMarks() {
        openNote("**bold** and *italic* and `code`")
        assertEquals("bold and italic and code", visible())
    }

    @Test
    fun savedLists_areShownWithTheirMarkers() {
        openNote("- one\n- two")
        val text = visible()
        assertTrue(text, text.contains("• one") && text.contains("• two"))
    }

    @Test
    fun aLongNote_isKeptWhole() {
        val id = openNote()
        val line = "The quick brown fox jumps over the lazy dog. "
        repeat(40) { commit(line) }
        val kept = storedNote(id)
        assertEquals(40, occurrences(kept, "quick brown fox"))
    }

    @Test
    fun accentsEmojiAndOtherScripts_areKept() {
        val id = openNote()
        commit("café 😀 日本語")
        assertStored(id, "café 😀 日本語")
    }

    @Test
    fun typedLetterByLetter_endsUpAsOneCleanLine() {
        val id = openNote()
        typeSlowly("Passport and charger")
        assertStored(id, "Passport and charger")
        assertEquals("Passport and charger", visible())
    }

    // ---------- characters that mean something in Markdown ----------

    @Test
    fun typedSpecialCharacters_comeBackAsTyped() {
        val id = openNote()
        commit("2*3*4 and <tag> and # not a heading")
        assertStored(id, "2\\*3\\*4 and \\<tag> and # not a heading")
        scenario.close()
        openEditor(id)
        assertEquals("2*3*4 and <tag> and # not a heading", visible())
    }

    @Test
    fun typedLineStarts_comeBackAsTyped() {
        val id = openNote()
        commit("# title"); enter(); commit("- item"); enter(); commit("1. step"); enter(); commit("> quote")
        assertStored(id, "\\# title\n\\- item\n1\\. step\n\\> quote")
        scenario.close()
        openEditor(id)
        assertEquals("# title - item 1. step > quote", visible())
    }

    @Test
    fun typedMarks_inBoldText_comeBackBoldAndAsTyped() {
        val id = openNote()
        tool("Bold"); commit("a*b_c"); tool("Bold"); commit(" and [x](y)")
        assertStored(id, "**a\\*b_c** and \\[x\\](y)")
        scenario.close()
        openEditor(id)
        assertEquals("a*b_c and [x](y)", visible())
        select(2)
        assertTrue("the bold word is still bold", lit("Bold"))
    }

    @Test
    fun typedMarks_inAListItem_comeBackAsTyped() {
        val id = openNote()
        tool("Bulleted list"); commit("2*3*4")
        assertStored(id, "- 2\\*3\\*4")
        scenario.close()
        openEditor(id)
        val text = visible()
        assertTrue(text, text.contains("• 2*3*4"))
    }

    @Test
    fun spaces_comeBackAsTyped() {
        val id = openNote()
        commit("a  b"); enter(); commit("  indented")
        assertStored(id, "a  b\n  indented")
        scenario.close()
        openEditor(id)
        assertEquals("a  b   indented", visible())
    }
}
