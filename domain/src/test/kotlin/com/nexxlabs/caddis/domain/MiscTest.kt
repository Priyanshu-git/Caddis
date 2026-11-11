package com.nexxlabs.caddis.domain

import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.analytics.ageBucket
import com.nexxlabs.caddis.domain.export.Exporter
import com.nexxlabs.caddis.domain.model.Collection
import com.nexxlabs.caddis.domain.model.ContentKind
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.ItemType
import com.nexxlabs.caddis.domain.model.Platform
import com.nexxlabs.caddis.domain.notes.Markdown
import com.nexxlabs.caddis.domain.search.SearchQuery
import com.nexxlabs.caddis.domain.search.resultBucket
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

private val NOW = Instant.parse("2026-11-04T05:38:00Z")

private fun link(id: String, title: String, coll: Set<String> = emptySet(), status: ItemStatus = ItemStatus.ACTIVE) = Item(
    id = id, type = ItemType.LINK, url = "https://e.com/$id", normalizedUrl = "e.com/$id", platform = Platform.named("example.com"),
    kind = ContentKind.WEBPAGE, title = title, intentId = Intents.COOK, reason = "for <Saturday>", createdAt = NOW,
    collectionIds = coll, status = status, note = "**Brown** the butter",
)

class SearchTest {
    @Test
    fun `builds a prefix AND query from any words`() {
        SearchQuery.toFtsMatch("Butter  COOKIES!") shouldBe "butter* cookies*"
        SearchQuery.toFtsMatch("   ") shouldBe null
        SearchQuery.toFtsMatch("\"quoted\" OR -x") shouldBe "quoted* or* x*"
    }

    @Test
    fun `explains which field matched`() {
        val i = link("1", "Cookies")
        SearchQuery.explain(i, "cook") shouldBe "Matched in the title"
        SearchQuery.explain(i, "butter") shouldBe "Matched in your note"
        SearchQuery.explain(i, "saturday") shouldBe "Matched in your reason"
        resultBucket(0) shouldBe "0"
        resultBucket(7) shouldBe "6-20"
    }
}

class MarkdownTest {
    @Test
    fun `strips markdown to plain text`() {
        Markdown.toPlainText("### Brown butter\n\n- 140 g **butter**\n> chill [30 min](https://x)") shouldBe "Brown butter 140 g butter chill 30 min"
        Markdown.preview("a".repeat(200), 10) shouldBe "aaaaaaaaaa…"
    }
}

class ExporterTest {
    @Test
    fun `json export has every item and collection`() {
        val c = Collection("c1", "Weekend recipes", NOW)
        val out = Exporter.toJson(listOf(link("1", "A", setOf("c1")), link("2", "B")), Intents.defaults, listOf(c), NOW)
        val root = Json.parseToJsonElement(out).jsonObject
        root["items"]!!.jsonArray.size shouldBe 2
        root["collections"]!!.jsonArray.size shouldBe 1
        out shouldContain "\"format\": \"caddis-export\""
    }

    @Test
    fun `html bookmarks are escaped, grouped and skip deleted`() {
        val c = Collection("c1", "Weekend & recipes", NOW)
        val html = Exporter.toHtmlBookmarks(
            listOf(link("1", "Cookies <3", setOf("c1")), link("2", "Loose"), link("3", "Gone", status = ItemStatus.DELETED)),
            listOf(c), Intents.defaults,
        )
        html shouldContain "<!DOCTYPE NETSCAPE-Bookmark-file-1>"
        html shouldContain "<H3>Weekend &amp; recipes</H3>"
        html shouldContain "Cookies &lt;3"
        html shouldContain "<H3>All saves</H3>"
        html shouldNotContain "Gone"
    }
}

class AnalyticsTest {
    @Test
    fun `custom intents are reported as custom and ages are bucketed`() {
        AnalyticsEvent.IntentChanged(Intents.WATCH, "gift-123").params() shouldBe mapOf("from" to "watch", "to" to "custom")
        ageBucket(NOW.minus(Duration.ofDays(10)), NOW) shouldBe "7-13d"
        ageBucket(NOW, NOW) shouldBe "0d"
        ageBucket(NOW.minus(Duration.ofDays(400)), NOW) shouldBe "90d+"
    }
}
