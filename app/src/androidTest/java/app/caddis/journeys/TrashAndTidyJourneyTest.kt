package app.caddis.journeys

import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.model.Intents
import app.caddis.domain.model.ItemStatus
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Delete is never final at once (Trash for 30 days); Tidy-up helps you let old saves go. */
@HiltAndroidTest
class TrashAndTidyJourneyTest : JourneyTest() {

    private fun delete(id: String) = runBlocking { repo.delete(id, AnalyticsEvent.DeleteSource.ITEM_PAGE) }

    // ---------- Trash ----------

    @Test
    fun deletedSaves_waitInTrash_andCanBeRestored() {
        val id = save("https://example.com/pasta", "Pasta night").id
        delete(id)
        launchApp()
        goTo("Library")
        awaitText("0 saves and notes · newest first")

        tapDescribed("Trash")
        assertShowing("Trash", "Removed for good after 30 days.", "Pasta night", "30 days left")

        tapButton("Restore")
        eventually("restored") { item(id).status == ItemStatus.ACTIVE }
        awaitText("Nothing here. Deleted saves stay here for 30 days.")
        back()
        awaitText("Pasta night")
    }

    @Test
    fun deleteForever_needsASecondTap_andCannotBeUndone() {
        val id = save("https://example.com/pasta", "Pasta night").id
        delete(id)
        launchApp()
        goTo("Profile")
        tapText("Trash")
        awaitText("Pasta night")

        tapText("Delete forever")
        awaitText("Sure? Tap again")
        assertEquals("one tap changes nothing", ItemStatus.DELETED, item(id).status)
        tapText("Sure? Tap again")

        eventually("purged") { runBlocking { repo.get(id) } == null }
        awaitText("Nothing here. Deleted saves stay here for 30 days.")
    }

    @Test
    fun trash_isEmptiedAfterThirtyDays_whenTheAppOpens() {
        val id = save("https://example.com/pasta", "Pasta night").id
        delete(id)
        daysLater(29)
        assertEquals(0, runBlocking { repo.purgeExpiredTrash() })
        assertNotNull(runBlocking { repo.get(id) })

        daysLater(2)
        assertEquals(1, runBlocking { repo.purgeExpiredTrash() })
        assertNull(runBlocking { repo.get(id) })
        assertTrue(trash().isEmpty())
    }

    @Test
    fun restoringADoneSave_putsItBackAsDone() {
        val id = save("https://example.com/pasta", "Pasta night").id
        runBlocking { repo.markDone(id) }
        delete(id)
        launchApp()
        goTo("Library")
        tapDescribed("Trash")
        tapButton("Restore")

        eventually("restored as done") { item(id).status == ItemStatus.DONE }
    }

    // ---------- Tidy-up ----------

    private fun twoOldSaves() {
        save("https://example.com/old-1", "Old one", Intents.KEEP)
        save("https://example.com/old-2", "Old two", Intents.KEEP)
        daysLater(100)
    }

    @Test
    fun tidyUp_walksThroughOldSaves_keepingOrDeletingEach() {
        twoOldSaves()
        launchApp()
        awaitText("Time for a tidy-up")

        tapButton("Review")
        assertShowing("Tidy-up", "1 of 2")

        tapButton("Keep")
        awaitText("2 of 2")
        tapButton("Delete")

        assertShowing("All tidy", "1 kept, 1 deleted.")
        eventually("one deleted, one kept") {
            library().let { live -> live.size == 1 && live.single().lastReviewedAt != null } && trash().size == 1
        }
        assertTrue(analytics.events.any { it is AnalyticsEvent.TidyUpFinished && it.kept == 1 && it.deleted == 1 })
        assertTrue(analytics.events.any { it is AnalyticsEvent.ItemDeleted && it.source == AnalyticsEvent.DeleteSource.TIDY_UP })
    }

    @Test
    fun afterTidyUp_theCardIsGone_andBackToLibraryWorks() {
        twoOldSaves()
        launchApp()
        tapButton("Review")
        tapButton("Keep")
        awaitText("2 of 2")
        tapButton("Keep")
        awaitText("All tidy")

        tapButton("Back to library")
        awaitText("2 saves and notes · newest first")
        goTo("Home")
        awaitText("Today")
        assertTrue(!exists(androidx.compose.ui.test.hasText("Time for a tidy-up")))
    }

    @Test
    fun aKeptSave_isNotOfferedAgainForNinetyDays() {
        val id = save("https://example.com/old-1", "Old one", Intents.KEEP).id
        daysLater(100)
        runBlocking { repo.keep(id) }
        val first = launchApp()

        awaitText("Today")
        assertTrue(!exists(androidx.compose.ui.test.hasText("Time for a tidy-up")))

        // Ninety-one days later, opening the app again offers it once more.
        first.close()
        daysLater(91)
        launchApp()
        awaitText("Time for a tidy-up")
    }
}
