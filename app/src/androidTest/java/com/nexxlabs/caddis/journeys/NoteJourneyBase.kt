package com.nexxlabs.caddis.journeys

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.nexxlabs.caddis.MainActivity

/**
 * What the note journeys share: opening the editor on a note, typing the way a keyboard does (through the
 * editor's own input connection, one call at a time, as an IME would), and reading back both what the
 * editor shows and what was kept in the library.
 */
abstract class NoteJourneyBase : JourneyTest() {

    protected lateinit var scenario: ActivityScenario<MainActivity>
    private var conn: InputConnection? = null

    /** Saves a standalone note and opens its editor with the caret in the body. */
    protected fun openNote(body: String = "", title: String = "Packing list"): String {
        val note = saveNote(title, body)
        openEditor(note.id)
        return note.id
    }

    /** Opens the app (on a screen if given), keeping the scenario so the keyboard can be reached. */
    protected fun start(route: String? = null) {
        scenario = launchApp(route = route)
    }

    protected fun openEditor(id: String) {
        start("note/$id")
        focusBody()
    }

    /** Taps into the body; the keyboard connection is made again for the new focus. */
    protected fun focusBody() {
        await(field("Note"))
        compose.onAllNodes(field("Note"))[0].performClick()
        compose.waitForIdle()
        conn = null
    }

    // ---------- typing like a keyboard ----------

    private fun focusedView(): View {
        var v: View? = null
        scenario.onActivity { v = it.window.decorView.findFocus() }
        return checkNotNull(v) { "The note body has no focus\nOn screen:\n${screen()}" }
    }

    /** One call from the keyboard to the editor, then the screen settles before the next. */
    protected fun ime(block: InputConnection.() -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val ic = conn ?: focusedView().onCreateInputConnection(EditorInfo())!!.also { conn = it }
            ic.block()
        }
        compose.waitForIdle()
    }

    /** A word (or more) that the keyboard has finished with. */
    protected fun commit(text: String) = ime { commitText(text, 1) }

    /** A word still being composed, the way suggestions are shown. */
    protected fun composing(text: String) = ime { setComposingText(text, 1) }

    protected fun finishWord() = ime { finishComposingText() }

    /** The Enter key. */
    protected fun enter() = ime {
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    protected fun backspace(count: Int = 1) = ime { deleteSurroundingText(count, 0) }

    protected fun select(start: Int, end: Int = start) = ime { setSelection(start, end) }

    protected fun caretToEnd() = select(visible().length)

    /** Types [text] one character at a time, as a hardware keyboard would. */
    protected fun typeSlowly(text: String) = text.forEach { commit(it.toString()) }

    /** A toolbar button. It changes the editor behind the keyboard's back, so the next call reconnects. */
    protected fun tool(description: String) {
        tapDescribed(description)
        conn = null
        // A list adds its bullet or number to the text, so the keyboard starts over from the new text.
        if (description.endsWith("list")) focusBody()
    }

    /** Removes whatever is selected (a keyboard replaces the selection with nothing). */
    protected fun deleteSelection() = commit("")

    /** Leaves the editor the way a person does: the first Back puts the keyboard away, the next one goes back. */
    protected fun leaveEditor() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        back()
    }

    // ---------- reading back ----------

    /** What the editor holds, as the screen reader sees it (line breaks show as spaces). */
    protected fun shown(): String = compose.onAllNodes(field("Note"))[0].fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    protected fun visible(): String = shown().trimEnd()

    /** Whether a toolbar button is lit (selected) right now. */
    protected fun lit(description: String): Boolean =
        compose.onNode(described(description)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** Waits for the autosave and checks the Markdown that was kept. */
    protected fun assertStored(id: String, expected: String) {
        var last = ""
        try {
            eventually("the note to be kept as [$expected]") { last = item(id).note; last == expected }
        } catch (e: AssertionError) {
            throw AssertionError("Expected the kept note to be [${esc(expected)}] but it is [${esc(last)}]", e)
        }
    }

    /** The kept note once saving has settled, for checks on more than the whole text. */
    protected fun storedNote(id: String): String {
        eventually("saving to finish") { !exists(androidx.compose.ui.test.hasText("Saving…")) }
        return item(id).note
    }

    protected fun occurrences(text: String, word: String): Int = Regex(Regex.escape(word)).findAll(text).count()

    private fun esc(s: String) = s.replace("\n", "\\n")
}
