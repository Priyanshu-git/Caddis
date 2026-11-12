package com.nexxlabs.caddis.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.nexxlabs.caddis.Fixture
import com.nexxlabs.caddis.ui.item.ItemViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The note editor's autosave: "Saving…" while typing, one write when typing pauses, nothing lost on leaving. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ItemViewModelNoteTest {
    private lateinit var f: Fixture
    private lateinit var vm: ItemViewModel
    private lateinit var id: String
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = Fixture()
    }

    @After
    fun tearDown() {
        // Let any write still in flight finish, and stop the ViewModel's collectors, before the database goes away.
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        runBlocking { appScope.coroutineContext.job.children.forEach { it.join() } }
        appScope.cancel()
        Dispatchers.resetMain()
        f.close()
    }

    /** Opens the editor on a note that already holds [saved]. */
    private suspend fun open(saved: String = "") {
        f.repo.seedDefaultsIfNeeded()
        id = f.repo.saveNote("Packing", saved).id
        val actions = ItemActions(
            ApplicationProvider.getApplicationContext(), f.repo, Messenger(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), f.clock,
        )
        vm = ItemViewModel(f.repo, actions, Messenger(), appScope, SavedStateHandle(mapOf("id" to id)))
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.item.first { it != null } } }
    }

    /** Room answers on its own threads, so wait in real time for what the app scope wrote. */
    private suspend fun awaitNote(expected: String) = withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            while (f.repo.get(id)?.note != expected) delay(20)
        }
    }

    private suspend fun savedNote(): String = f.repo.get(id)!!.note

    @Test
    fun `typing shows Saving and the note is written once typing pauses`() = runTest {
        open()
        vm.noteChanged("Passport")
        assertTrue(vm.saving.value)

        advanceTimeBy(600)
        awaitNote("Passport")
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (vm.saving.value) delay(20) } }
        assertFalse(vm.saving.value)
    }

    @Test
    fun `a burst of edits is written as one save holding the last text`() = runTest {
        open()
        listOf("P", "Pa", "Pas", "Pass", "Passport").forEach { vm.noteChanged(it); advanceTimeBy(100) }
        advanceTimeBy(600)
        awaitNote("Passport")
        val revisionAfterBurst = f.repo.get(id)!!.revision

        advanceTimeBy(2_000)
        assertEquals("nothing else is written later", revisionAfterBurst, f.repo.get(id)!!.revision)
    }

    @Test
    fun `text equal to what is saved does not start a save`() = runTest {
        open("Passport")
        vm.noteChanged("Passport")
        assertFalse(vm.saving.value)
        val revision = f.repo.get(id)!!.revision

        advanceTimeBy(2_000)
        assertEquals(revision, f.repo.get(id)!!.revision)
    }

    @Test
    fun `typing and undoing within the pause leaves the saved note untouched`() = runTest {
        open("Passport")
        val revision = f.repo.get(id)!!.revision

        vm.noteChanged("Passport and charger")
        advanceTimeBy(200)
        vm.noteChanged("Passport")
        advanceTimeBy(2_000)
        withContext(Dispatchers.Default) { delay(300) }

        assertEquals("Passport", savedNote())
        assertEquals("the undone edit is not written", revision, f.repo.get(id)!!.revision)
        assertFalse("nothing is left saving", vm.saving.value)
    }

    @Test
    fun `leaving keeps the last keystroke without waiting for the pause`() = runTest {
        open()
        vm.noteChanged("Passport and charger")
        vm.flushNote()
        awaitNote("Passport and charger")
    }

    @Test
    fun `leaving after typing and undoing keeps the saved note`() = runTest {
        open("Passport")
        vm.noteChanged("Passport and charger")
        vm.noteChanged("Passport")
        vm.flushNote()
        withContext(Dispatchers.Default) { delay(300) }
        assertEquals("Passport", savedNote())
    }

    @Test
    fun `leaving with nothing typed writes nothing`() = runTest {
        open("Passport")
        val revision = f.repo.get(id)!!.revision
        vm.flushNote()
        withContext(Dispatchers.Default) { delay(300) }
        assertEquals(revision, f.repo.get(id)!!.revision)
    }

    @Test
    fun `clearing the whole note saves an empty note`() = runTest {
        open("Passport")
        vm.noteChanged("")
        advanceTimeBy(600)
        awaitNote("")
    }

    @Test
    fun `the editor opens on the saved note when nothing is pending`() = runTest {
        open("Passport")
        assertEquals("Passport", vm.currentNote())
    }

    @Test
    fun `the editor opens on words typed but not yet saved`() = runTest {
        open("Passport")
        vm.noteChanged("Passport and charger")
        assertEquals("Passport and charger", vm.currentNote())
    }

    @Test
    fun `the editor opens on an emptied note, not the saved words it replaced`() = runTest {
        open("Passport")
        vm.noteChanged("")
        assertEquals("", vm.currentNote())
    }

    @Test
    fun `later edits keep saving after the first one`() = runTest {
        open()
        vm.noteChanged("one")
        advanceTimeBy(600)
        awaitNote("one")
        vm.noteChanged("one\ntwo")
        advanceTimeBy(600)
        awaitNote("one\ntwo")
    }
}
