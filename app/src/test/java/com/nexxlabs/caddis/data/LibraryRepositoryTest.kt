package com.nexxlabs.caddis.data

import com.nexxlabs.caddis.Fixture
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent.SaveEntry
import com.nexxlabs.caddis.domain.details.LinkDetails
import com.nexxlabs.caddis.domain.link.LinkCheck
import com.nexxlabs.caddis.domain.link.Links
import com.nexxlabs.caddis.domain.model.ContentKind
import com.nexxlabs.caddis.domain.model.EditableField
import com.nexxlabs.caddis.domain.model.IntentSource
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.Platform
import com.nexxlabs.caddis.domain.model.ReturnRule
import com.nexxlabs.caddis.domain.schedule.Weeks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    private lateinit var f: Fixture
    private val repo get() = f.repo

    @Before fun setUp() = runBlocking { f = Fixture(); repo.seedDefaultsIfNeeded() }
    @After fun tearDown() = f.close()

    private fun link(url: String) = Links.check(url) as LinkCheck.Valid

    private suspend fun save(url: String, hint: String = "", entry: SaveEntry = SaveEntry.IN_APP) =
        (repo.saveLink(link(url), entry, titleHint = hint) as SaveOutcome.Saved).item

    private suspend fun week() = repo.observeWeek(Weeks.weekStart(f.clock.instant(), f.clock.zone)).first()

    @Test
    fun `seeds the eight default intents and their rules once`() = runBlocking {
        repo.seedDefaultsIfNeeded()
        assertEquals(8, repo.observeIntents().first().size)
        assertEquals(7, repo.observeRules().first().size) // Just keep has no rule
    }

    @Test
    fun `a share is saved instantly with a guess, then details are scheduled`() = runBlocking {
        val item = save("https://www.youtube.com/watch?v=Lm0&si=x", entry = SaveEntry.SHARE_SHEET)
        // Nothing is known about the site until its page is read: the host is the platform.
        assertEquals("youtube.com", item.platform.label)
        assertEquals(Intents.READ, item.intentId)
        assertEquals(listOf(item.id), f.scheduler.scheduled)
        assertEquals(1, week().savedCount)
        val ev = f.analytics.events.single() as AnalyticsEvent.SaveCreated
        assertEquals(SaveEntry.SHARE_SHEET, ev.entry)
    }

    @Test
    fun `every link is sent for details, and starts with the site address as its creator`() = runBlocking {
        val item = save("https://www.instagram.com/oven.notes/reel/Cx9/")
        assertEquals(listOf(item.id), f.scheduler.scheduled)
        assertEquals("instagram.com", item.creator)
    }

    @Test
    fun `the same link is flagged as a duplicate, and can still be saved again`() = runBlocking {
        val first = save("https://www.instagram.com/reel/Cx9bT2/?igsh=1")
        val dup = repo.saveLink(link("instagram.com/reel/Cx9bT2"), SaveEntry.IN_APP)
        assertTrue(dup is SaveOutcome.Duplicate)
        assertEquals(first.id, (dup as SaveOutcome.Duplicate).existing.id)
        val again = repo.saveLink(dup.link, SaveEntry.IN_APP, allowDuplicate = true)
        assertTrue(again is SaveOutcome.Saved)
        assertEquals(2, repo.observeLibrary().first().size)
    }

    @Test
    fun `search matches any words in any order across fields, as prefixes, and skips Trash`() = runBlocking {
        val cookies = save("https://example.com/a", hint = "Brown butter cookies")
        repo.setReason(cookies.id, "Try for Saturday brunch")
        val dal = repo.saveNote("Weeknight dal", "Red **lentils**, one pot")
        save("https://example.com/c", hint = "Rome day trips")

        assertEquals(listOf(cookies.id), repo.search("cookies butter").first().map { it.id })
        assertEquals(listOf(cookies.id), repo.search("satur").first().map { it.id })
        assertEquals(listOf(dal.id), repo.search("lentils").first().map { it.id })
        assertEquals(listOf(cookies.id), repo.search("example.com/a").first().map { it.id }.filter { it == cookies.id })
        assertTrue(repo.search("   ").first().isEmpty())

        repo.delete(cookies.id, AnalyticsEvent.DeleteSource.ITEM_PAGE)
        assertTrue(repo.search("cookies").first().isEmpty())
    }

    @Test
    fun `done and undo move the weekly used count`() = runBlocking {
        val item = save("https://example.com/x")
        repo.markDone(item.id)
        assertEquals(ItemStatus.DONE, repo.get(item.id)!!.status)
        assertEquals(1, week().usedCount)
        repo.markActive(item.id)
        assertEquals(ItemStatus.ACTIVE, repo.get(item.id)!!.status)
        assertEquals(0, week().usedCount)
    }

    @Test
    fun `delete goes to Trash, restore brings it back as it was, purge after 30 days`() = runBlocking {
        val done = save("https://example.com/done")
        repo.markDone(done.id)
        repo.delete(done.id, AnalyticsEvent.DeleteSource.ITEM_PAGE)
        assertEquals(1, week().deletedCount)
        assertEquals(1, repo.observeTrash().first().size)
        repo.restore(done.id)
        assertEquals(ItemStatus.DONE, repo.get(done.id)!!.status)
        assertEquals(0, week().deletedCount)

        val gone = save("https://example.com/gone")
        repo.delete(gone.id, AnalyticsEvent.DeleteSource.TIDY_UP)
        f.clock.advance(Duration.ofDays(29))
        assertEquals(0, repo.purgeExpiredTrash())
        f.clock.advance(Duration.ofDays(2))
        assertEquals(1, repo.purgeExpiredTrash())
        assertNull(repo.get(gone.id))
    }

    @Test
    fun `changing a guessed intent is remembered and shifts later guesses`() = runBlocking {
        repeat(2) {
            val reel = save("https://www.instagram.com/reel/r$it/")
            repo.setIntent(reel.id, Intents.COOK)
            assertEquals(IntentSource.USER, repo.get(reel.id)!!.intentSource)
        }
        val next = save("https://www.instagram.com/reel/new/")
        assertEquals(Intents.COOK, next.intentId)
    }

    @Test
    fun `fetched details never overwrite the person's edits and refine a guessed intent`() = runBlocking {
        val item = save("https://example.com/post")
        repo.setTitle(item.id, "My own title")
        repo.applyDetails(item.id, LinkDetails(title = "Their title", description = "An easy lemon orzo recipe", lengthSeconds = 300))
        val after = repo.get(item.id)!!
        assertEquals("My own title", after.title)
        assertTrue(EditableField.TITLE in after.userEdited)
        assertEquals("An easy lemon orzo recipe", after.description)
        assertEquals(Intents.COOK, after.intentId)
        assertNotNull(after.detailsFetchedAt)
    }

    @Test
    fun `deleting an intent moves its saves to Just keep, which cannot be deleted`() = runBlocking {
        val r = repo.createIntent("Gift", "Gifted") as LibraryRepository.NameResult.Ok
        val intents = repo.observeIntents().first()
        assertEquals("Gift", intents[intents.size - 2].name) // just before Just keep
        assertEquals(Intents.KEEP, intents.last().id)
        assertTrue(repo.observeRules().first().containsKey(r.id))

        val item = save("https://example.com/g")
        repo.setIntent(item.id, r.id)
        assertEquals(1, repo.deleteIntent(r.id))
        assertEquals(Intents.KEEP, repo.get(item.id)!!.intentId)
        assertEquals(0, repo.deleteIntent(Intents.KEEP))
        assertTrue(repo.createIntent("cook", "") is LibraryRepository.NameResult.Error)
    }

    @Test
    fun `collections have unique names, many per item, deleting keeps the items`() = runBlocking {
        val a = (repo.createCollection("Weekend recipes") as LibraryRepository.NameResult.Ok).id
        val b = (repo.createCollection("Gift ideas") as LibraryRepository.NameResult.Ok).id
        assertTrue(repo.createCollection("weekend RECIPES") is LibraryRepository.NameResult.Error)
        val item = save("https://example.com/i")
        repo.setInCollection(item.id, a, true)
        repo.setInCollection(item.id, b, true)
        assertEquals(setOf(a, b), repo.observeItem(item.id).first()!!.collectionIds)
        repo.deleteCollection(a)
        assertEquals(setOf(b), repo.observeItem(item.id).first()!!.collectionIds)
        assertNotNull(repo.get(item.id))
    }

    @Test
    fun `bring back later sets a future return, surfacing is counted once a day`() = runBlocking {
        val item = save("https://example.com/l")
        val label = repo.bringBackLater(item.id, ReturnRule.TONIGHT)
        assertEquals("tonight at 7 pm", label)
        assertTrue(repo.get(item.id)!!.returnAt!!.isAfter(f.clock.instant()))

        repo.markSurfaced(listOf(item.id))
        repo.markSurfaced(listOf(item.id))
        assertEquals(1, repo.get(item.id)!!.surfacedCount)
        f.clock.advance(Duration.ofDays(1))
        repo.markSurfaced(listOf(item.id))
        assertEquals(2, repo.get(item.id)!!.surfacedCount)
    }

    @Test
    fun `export and delete all data`() = runBlocking {
        save("https://example.com/e", hint = "Exported")
        assertTrue(repo.exportJson().contains("Exported"))
        assertTrue(repo.exportHtml().contains("https://example.com/e"))
        repo.deleteAllData()
        assertTrue(repo.observeLibrary().first().isEmpty())
        assertEquals(8, repo.observeIntents().first().size)
    }

    @Test
    fun `edited details survive a refresh, the favicon is stored, and a cleared field refills`() = runBlocking {
        val item = save("https://example.com/post")
        repo.applyDetails(item.id, LinkDetails(title = "Their title", creator = "Site", faviconUrl = "https://example.com/i.png"))
        repo.saveDetails(item.id, null, com.nexxlabs.caddis.domain.details.DetailEdits("My title", "My summary", ""))
        var now = repo.observeItem(item.id).first()!!
        assertEquals(setOf(EditableField.TITLE, EditableField.DESCRIPTION), now.userEdited)
        assertEquals("https://example.com/i.png", now.faviconUrl)

        repo.applyDetails(item.id, LinkDetails(title = "Newer title", description = "Their summary", creator = "Site again"))
        now = repo.observeItem(item.id).first()!!
        assertEquals("My title", now.title)
        assertEquals("My summary", now.description)
        assertEquals("Site again", now.creator)
    }

    private fun edits(title: String = "", description: String = "", creator: String = "") =
        com.nexxlabs.caddis.domain.details.DetailEdits(title, description, creator)

    @Test
    fun `editing the link re-reads the platform, re-guesses the intent and fetches again`() = runBlocking {
        val item = save("https://example.com/how-to-read-faster")
        repo.applyDetails(item.id, LinkDetails(title = "How to read faster", creator = "example.com"))
        f.scheduler.scheduled.clear()
        val now = repo.get(item.id)!!

        val r = repo.saveDetails(item.id, "youtu.be/abc", edits(now.title, now.description, now.creator))
        assertEquals(LibraryRepository.DetailsResult.Saved(refetching = true), r)
        val moved = repo.get(item.id)!!
        assertEquals(Platform.named("youtu.be"), moved.platform)
        assertEquals("youtu.be/abc", moved.normalizedUrl)
        assertEquals(Intents.READ, moved.intentId)
        assertEquals("", moved.title)
        assertNull(moved.detailsFetchedAt)
        assertEquals(listOf(item.id), f.scheduler.scheduled)
    }

    @Test
    fun `a late fetch for the old link is dropped`() = runBlocking {
        val item = save("https://example.com/a")
        repo.saveDetails(item.id, "https://other.org/b", edits(creator = "example.com"))
        repo.applyDetails(item.id, LinkDetails(title = "Old page"), forUrl = "https://example.com/a")
        repo.markDetailsAttempted(item.id, forUrl = "https://example.com/a")
        val now = repo.get(item.id)!!
        assertEquals("", now.title)
        assertNull(now.detailsFetchedAt)
        repo.applyDetails(item.id, LinkDetails(title = "New page"), forUrl = "https://other.org/b")
        assertEquals("New page", repo.get(item.id)!!.title)
    }

    @Test
    fun `a bad or taken link is refused and nothing changes`() = runBlocking {
        val a = save("https://example.com/a")
        val b = save("https://example.com/b")
        assertEquals(LibraryRepository.DetailsResult.BadLink("That doesn't look like a web link."), repo.saveDetails(a.id, "not a link", edits("Mine", creator = "example.com")))
        assertTrue(repo.saveDetails(a.id, "https://www.example.com/b/", edits()) is LibraryRepository.DetailsResult.LinkTaken)
        val after = repo.get(a.id)!!
        assertEquals("https://example.com/a", after.url)
        assertEquals("", after.title)
        assertEquals(b.normalizedUrl, repo.get(b.id)!!.normalizedUrl)
    }

    @Test
    fun `a link changed to instagram is fetched like any other`() = runBlocking {
        val item = save("https://example.com/a")
        f.scheduler.scheduled.clear()
        val r = repo.saveDetails(item.id, "https://instagram.com/oven.notes/reel/xyz", edits(creator = "example.com"))
        assertEquals(LibraryRepository.DetailsResult.Saved(refetching = true), r)
        assertEquals(Platform.named("instagram.com"), repo.get(item.id)!!.platform)
        assertEquals(listOf(item.id), f.scheduler.scheduled)
    }

    @Test
    fun `the page names the platform and a video page changes the guess`() = runBlocking {
        val item = save("https://chatgpt.com/share/6af24282-1abc-83ec-9b32-ee08b94575da")
        assertEquals("chatgpt.com", item.platform.label)
        repo.applyDetails(item.id, LinkDetails(title = "Pasta plan", siteName = "ChatGPT"), forUrl = item.url)
        val named = repo.get(item.id)!!
        assertEquals("ChatGPT", named.platform.label)
        assertEquals("Pasta plan", named.title)
        assertEquals("ChatGPT", repo.observeItem(item.id).first()!!.platform.label) // stored and read back from the database

        val video = save("https://www.youtube.com/watch?v=Lm0")
        assertEquals(Intents.READ, video.intentId)
        repo.applyDetails(video.id, LinkDetails(title = "Lemon orzo", siteName = "YouTube", kind = ContentKind.VIDEO))
        val watched = repo.get(video.id)!!
        assertEquals("YouTube", watched.platform.label)
        assertEquals(ContentKind.VIDEO, watched.kind)
        assertEquals(Intents.COOK, watched.intentId) // "orzo" is a cooking word, which beats the default
    }

    @Test
    fun `an intent the person chose is not re-guessed when the page arrives`() = runBlocking {
        val item = save("https://www.youtube.com/watch?v=Lm0")
        repo.setIntent(item.id, Intents.TRY)
        repo.applyDetails(item.id, LinkDetails(title = "A video", siteName = "YouTube", kind = ContentKind.VIDEO))
        assertEquals(Intents.TRY, repo.get(item.id)!!.intentId)
        assertEquals("YouTube", repo.get(item.id)!!.platform.label)
    }

    @Test
    fun `any site is its own platform and survives a round trip through the database`() = runBlocking {
        val drive = save("https://drive.google.com/file/d/1AbC/view")
        val site = save("https://www.nytimes.com/2025/01/01/world/story.html")
        repo.applyDetails(drive.id, LinkDetails(siteName = "Google Drive", title = "Plan.pdf"))
        val storedDrive = repo.observeItem(drive.id).first()!!
        assertEquals("Google Drive", storedDrive.platform.label)
        val storedSite = repo.get(site.id)!!
        assertEquals("nytimes.com", storedSite.platform.label)
        assertEquals("nytimes.com", storedSite.creator)
        assertEquals(listOf("SaveCreated"), f.analytics.events.take(1).map { it::class.simpleName })
        assertEquals("link", (f.analytics.events.last() as AnalyticsEvent.SaveCreated).params()["platform"])
    }

    @Test
    fun `missing details are scheduled for every link, whatever the site`() = runBlocking {
        val ig = save("https://www.instagram.com/oven.notes/reel/Cx9/")
        val threads = save("https://www.threads.net/@someone")
        val site = save("https://example.com/a")
        f.scheduler.scheduled.clear()
        repo.scheduleMissingDetails()
        assertEquals(setOf(ig.id, threads.id, site.id), f.scheduler.scheduled.toSet())
    }
}
