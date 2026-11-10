package app.caddis.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.caddis.Fixture
import app.caddis.data.LibraryRepository
import app.caddis.data.SaveOutcome
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.details.LinkDetails
import app.caddis.domain.link.LinkCheck
import app.caddis.domain.link.Links
import app.caddis.domain.model.Platform
import app.caddis.ui.library.Filters
import app.caddis.ui.library.LibraryState
import app.caddis.ui.library.LibraryViewModel
import app.caddis.ui.library.StatusFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** LIB-03: the platform filter lists what the library has, not a fixed set. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    private lateinit var f: Fixture
    private lateinit var vm: LibraryViewModel

    @Before
    fun setUp() = runBlocking {
        // Real time, not virtual: the search flow debounces, and virtual time would never advance under runBlocking.
        Dispatchers.setMain(Dispatchers.Unconfined)
        f = Fixture()
        f.repo.seedDefaultsIfNeeded()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val actions = ItemActions(app, f.repo, Messenger(), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), f.clock)
        vm = LibraryViewModel(f.repo, actions, f.analytics, SavedStateHandle())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        f.close()
    }

    private suspend fun save(url: String) =
        (f.repo.saveLink(Links.check(url) as LinkCheck.Valid, AnalyticsEvent.SaveEntry.IN_APP) as SaveOutcome.Saved).item

    private suspend fun state(until: (LibraryState) -> Boolean): LibraryState = withTimeout(5_000) { vm.state.first(until) }

    private fun LibraryState.ids() = platforms.map { it.platform.id }

    @Test
    fun `there is no platform to filter by until something is saved`() = runBlocking {
        assertTrue(state { true }.platforms.isEmpty())
        f.repo.saveNote("Just a thought")
        assertTrue(state { it.items.size == 1 }.platforms.isEmpty())
    }

    @Test
    fun `a platform appears when a link from it is saved and not before`() = runBlocking {
        save("https://drive.google.com/file/d/1AbC/view")
        save("https://www.nytimes.com/a")
        val s = state { it.platforms.size == 2 }
        assertEquals(setOf("drive.google.com", "nytimes.com"), s.ids().toSet())
        assertTrue("instagram.com" !in s.ids())

        save("https://www.instagram.com/p/Cx9/")
        assertTrue("instagram.com" in state { it.platforms.size == 3 }.ids())
    }

    @Test
    fun `a platform takes the name its site gives it, and sites with the same name become one`() = runBlocking {
        val drive = save("https://drive.google.com/file/d/1AbC/view")
        val docs = save("https://docs.google.com/document/d/1AbC/edit")
        val en = save("https://en.wikipedia.org/wiki/River")
        val de = save("https://de.wikipedia.org/wiki/Fluss")
        assertEquals(4, state { it.platforms.size == 4 }.platforms.size)

        f.repo.applyDetails(drive.id, LinkDetails(siteName = "Google Drive"))
        f.repo.applyDetails(docs.id, LinkDetails(siteName = "Google Docs"))
        f.repo.applyDetails(en.id, LinkDetails(siteName = "Wikipedia"))
        f.repo.applyDetails(de.id, LinkDetails(siteName = "Wikipedia"))
        val s = state { it.platforms.size == 3 }
        assertEquals(setOf("google drive", "google docs", "wikipedia"), s.ids().toSet())
        assertEquals(2, s.platforms.first { it.platform.id == "wikipedia" }.count)
        assertEquals("Wikipedia", s.platforms.first { it.platform.id == "wikipedia" }.platform.label)
    }

    @Test
    fun `a platform goes with its last save, and so does a filter on it`() = runBlocking {
        val ig = save("https://www.instagram.com/p/Cx9/")
        save("https://github.com/a/b")
        state { it.platforms.size == 2 }
        vm.setPlatform(Platform.named("instagram.com"))
        assertEquals(1, state { it.filters.platform == Platform.named("instagram.com") }.items.size)

        f.repo.delete(ig.id, AnalyticsEvent.DeleteSource.ITEM_PAGE)
        val s = state { it.platforms.size == 1 }
        assertEquals(listOf("github.com"), s.ids())
        assertNull(s.filters.platform)
        assertEquals(1, s.items.size)
    }

    @Test
    fun `choosing a platform narrows the library to it`() = runBlocking {
        save("https://drive.google.com/file/d/1AbC/view")
        save("https://www.nytimes.com/a")
        save("https://www.nytimes.com/b")
        state { it.platforms.size == 2 }
        vm.setPlatform(Platform.named("nytimes.com"))
        val s = state { it.filters.platform != null }
        assertEquals(2, s.items.size)
        assertEquals("nytimes.com", s.platforms.first().platform.id)
        assertEquals(2, s.platforms.first().count)
    }

    @Test
    fun `the filter button counts what is inside the sheet, not the platform shown in the row`() {
        val f = Filters(intentId = "cook", platform = Platform.named("YouTube"), status = StatusFilter.DONE)
        assertEquals(2, f.sheetCount(platformInRow = true))
        assertEquals(3, f.sheetCount())
        assertEquals(0, Filters().sheetCount(platformInRow = true))
        assertEquals(0, Filters(platform = Platform.named("YouTube")).sheetCount(platformInRow = true))
    }
}
