package com.nexxlabs.caddis.domain.library

import com.nexxlabs.caddis.domain.model.ContentKind
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.ItemType
import com.nexxlabs.caddis.domain.model.Platform
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class LibrarySectionsTest {
    private fun at(day: Int) = Instant.parse("2026-11-%02dT10:00:00Z".format(day))

    private fun item(id: String, saved: Int, status: ItemStatus = ItemStatus.ACTIVE, done: Int? = null) = Item(
        id = id, type = ItemType.LINK, url = "https://example.com/$id", normalizedUrl = "https://example.com/$id",
        platform = Platform.named("Example"), kind = ContentKind.ARTICLE, intentId = "read",
        createdAt = at(saved), status = status, doneAt = done?.let(::at),
    )

    @Test
    fun `an empty library has nothing in either section`() {
        val s = LibrarySections.of(emptyList())
        s.active.shouldBeEmpty()
        s.completed.shouldBeEmpty()
        s.shownCompleted(showAll = false).shouldBeEmpty()
        s.hiddenCompleted(showAll = false) shouldBe 0
    }

    @Test
    fun `finished saves leave the active list and keep the order it came in`() {
        val items = listOf(
            item("c", saved = 9),
            item("b", saved = 8, ItemStatus.DONE, done = 10),
            item("a", saved = 7),
        )
        val s = LibrarySections.of(items)
        s.active.map { it.id } shouldBe listOf("c", "a")
        s.completed.map { it.id } shouldBe listOf("b")
    }

    @Test
    fun `completed is ordered by when it was finished, not when it was saved`() {
        val items = listOf(
            item("newSaveOldFinish", saved = 20, ItemStatus.DONE, done = 21),
            item("oldSaveNewFinish", saved = 1, ItemStatus.DONE, done = 25),
            item("middle", saved = 5, ItemStatus.DONE, done = 23),
        )
        LibrarySections.of(items).completed.map { it.id } shouldBe listOf("oldSaveNewFinish", "middle", "newSaveOldFinish")
    }

    @Test
    fun `saves finished at the same moment fall back to newest saved first`() {
        val items = listOf(
            item("older", saved = 2, ItemStatus.DONE, done = 25),
            item("newer", saved = 3, ItemStatus.DONE, done = 25),
        )
        LibrarySections.of(items).completed.map { it.id } shouldBe listOf("newer", "older")
    }

    @Test
    fun `a finished save with no finish time still lands in completed`() {
        val s = LibrarySections.of(listOf(item("x", saved = 4, ItemStatus.DONE, done = null)))
        s.completed.map { it.id } shouldBe listOf("x")
    }

    @Test
    fun `deleted saves belong to neither section`() {
        val s = LibrarySections.of(listOf(item("gone", saved = 1, ItemStatus.DELETED), item("kept", saved = 2)))
        s.active.map { it.id } shouldBe listOf("kept")
        s.completed.shouldBeEmpty()
    }

    @Test
    fun `the open section shows the latest three and counts the rest until show all`() {
        val items = (1..5).map { item("d$it", saved = it, ItemStatus.DONE, done = it + 10) }
        val s = LibrarySections.of(items)

        s.shownCompleted(showAll = false).map { it.id } shouldBe listOf("d5", "d4", "d3")
        s.hiddenCompleted(showAll = false) shouldBe 2
        s.shownCompleted(showAll = true).map { it.id } shouldBe listOf("d5", "d4", "d3", "d2", "d1")
        s.hiddenCompleted(showAll = true) shouldBe 0
    }

    @Test
    fun `no show all link when every completed save already fits`() {
        val items = (1..3).map { item("d$it", saved = it, ItemStatus.DONE, done = it + 10) }
        LibrarySections.of(items).hiddenCompleted(showAll = false) shouldBe 0
    }
}
