package app.caddis.ui

import app.caddis.Fixture
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import app.caddis.ui.capture.CaptureSheet
import app.caddis.ui.capture.CaptureViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureViewModelTest {
    private lateinit var f: Fixture
    private lateinit var vm: CaptureViewModel

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = Fixture()
        f.repo.seedDefaultsIfNeeded()
        vm = CaptureViewModel(f.repo, CoroutineScope(Dispatchers.IO))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        f.close()
    }

    private suspend fun awaitSheet(): CaptureSheet = withTimeout(5_000) { vm.sheet.first { it != CaptureSheet.None } }

    @Test
    fun `shared text with a link saves the link and keeps the text as a title hint`() = runBlocking {
        assertTrue(vm.fromShare("Brown butter cookies https://www.instagram.com/reel/Cx9bT2/?igsh=x", null, "WhatsApp"))
        val s = awaitSheet() as CaptureSheet.Saved
        val item = f.repo.get(s.itemId)!!
        assertEquals(Platform.INSTAGRAM, item.platform)
        assertEquals("Brown butter cookies", item.title)
        assertEquals("WhatsApp", item.sourceApp)
        assertEquals("cook", item.intentId)
    }

    @Test
    fun `shared plain text becomes a note`() = runBlocking {
        assertTrue(vm.fromShare("Chargers, adapters, one good jacket", "Packing list", ""))
        val s = awaitSheet() as CaptureSheet.Saved
        val item = f.repo.get(s.itemId)!!
        assertEquals(ItemType.NOTE, item.type)
        assertEquals("Packing list", item.title)
    }

    @Test
    fun `nothing shared means nothing saved`() {
        assertFalse(vm.fromShare("  ", null, ""))
    }

    @Test
    fun `in-app add rejects non web links and flags duplicates`() = runBlocking {
        vm.openAdd(clipboard = "look https://youtu.be/abc")
        assertEquals("https://youtu.be/abc", (vm.sheet.value as CaptureSheet.Add).clipboard)

        vm.submitLink("tel:123").join()
        assertEquals("Only web links (http or https) can be saved.", (vm.sheet.value as CaptureSheet.Add).error)

        vm.submitLink("youtu.be/abc").join()
        assertTrue(vm.sheet.value is CaptureSheet.Saved)

        vm.openAdd(null)
        vm.submitLink("https://www.youtube.com/watch?v=abc").join()
        assertTrue(vm.sheet.value is CaptureSheet.Duplicate)
    }
}
