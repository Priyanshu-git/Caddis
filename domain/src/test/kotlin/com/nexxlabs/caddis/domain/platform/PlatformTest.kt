package com.nexxlabs.caddis.domain.platform

import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.intent.CorrectionStats
import com.nexxlabs.caddis.domain.link.PlatformDetector
import com.nexxlabs.caddis.domain.model.ContentKind
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.ItemType
import com.nexxlabs.caddis.domain.model.Platform
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class PlatformTest {

    @Test
    fun `before its page is read a platform is the host`() {
        Platform.fromUrl("https://chatgpt.com/share/6af24282-1abc-83ec-9b32-ee08b94575da").label shouldBe "chatgpt.com"
        Platform.fromUrl("https://www.nytimes.com/a").label shouldBe "nytimes.com"
        Platform.fromUrl("https://m.example.com/a").label shouldBe "example.com"
        Platform.fromUrl("https://m.com/a").label shouldBe "m.com"
        Platform.fromUrl("https://Docs.Google.com/document/d/1").label shouldBe "docs.google.com"
        Platform.fromUrl("not a url").label shouldBe "web"
    }

    @Test
    fun `platforms are the same when their names match ignoring case`() {
        Platform.named("ChatGPT") shouldBe Platform.named("chatgpt")
        Platform.named("ChatGPT").hashCode() shouldBe Platform.named("CHATGPT").hashCode()
        (Platform.named("ChatGPT") == Platform.named("Claude")) shouldBe false
        Platform.named("ChatGPT").id shouldBe "chatgpt"
    }

    @Test
    fun `a stored platform loads exactly as stored, and notes are recognised`() {
        Platform.fromStored("ChatGPT").label shouldBe "ChatGPT"
        Platform.fromStored("nytimes.com").label shouldBe "nytimes.com"
        Platform.fromStored("note") shouldBe Platform.NOTE
        Platform.fromStored("Note") shouldBe Platform.NOTE
        Platform.fromStored("").label shouldBe "web"
        Platform.NOTE.isNote shouldBe true
        Platform.named("Notes app").isNote shouldBe false
    }

    @Test
    fun `corrections are learned per site and kind`() {
        CorrectionStats.key(Platform.named("Instagram"), ContentKind.REEL) shouldBe "instagram:REEL"
        CorrectionStats.key(Platform.named("INSTAGRAM"), ContentKind.REEL) shouldBe "instagram:REEL"
        CorrectionStats.key(Platform.fromUrl("https://a.com/x"), ContentKind.WEBPAGE) shouldBe "a.com:WEBPAGE"
    }

    @Test
    fun `analytics only ever learn link or note, never a site a person browses`() {
        AnalyticsEvent.SaveCreated(Platform.named("my-private-blog.example"), AnalyticsEvent.SaveEntry.IN_APP, true)
            .params()["platform"] shouldBe "link"
        AnalyticsEvent.ItemOpened(Platform.named("ChatGPT"), AnalyticsEvent.OpenEntry.HOME).params()["platform"] shouldBe "link"
        AnalyticsEvent.SaveCreated(Platform.NOTE, AnalyticsEvent.SaveEntry.IN_APP, false).params()["platform"] shouldBe "note"
    }
}

class PlatformFacetsTest {
    private fun link(id: String, url: String, site: String? = null): Item {
        val d = PlatformDetector.detect(url)
        return Item(
            id = id, type = ItemType.LINK, url = url, normalizedUrl = url, platform = site?.let(Platform::named) ?: d.platform,
            kind = d.kind, intentId = "read", createdAt = Instant.EPOCH, status = ItemStatus.ACTIVE,
        )
    }

    private val note = Item(
        id = "n", type = ItemType.NOTE, url = null, normalizedUrl = null, platform = Platform.NOTE,
        kind = ContentKind.NOTE, intentId = "keep", createdAt = Instant.EPOCH,
    )

    @Test
    fun `an empty library has no platforms`() {
        PlatformFacets.of(emptyList()).shouldBeEmpty()
        PlatformFacets.of(listOf(note)).shouldBeEmpty()
    }

    @Test
    fun `lists only platforms that have saves, most first, then by name`() {
        val items = listOf(
            link("1", "https://drive.google.com/file/d/1", site = "Google Drive"),
            link("2", "https://www.nytimes.com/a", site = "The New York Times"),
            link("3", "https://www.nytimes.com/b", site = "The New York Times"),
            link("4", "https://github.com/a/b", site = "GitHub"),
            link("5", "https://romeguide.example/x"),
            note,
        )
        PlatformFacets.of(items).map { it.platform.label to it.count } shouldBe
            listOf("The New York Times" to 2, "GitHub" to 1, "Google Drive" to 1, "romeguide.example" to 1)
    }

    @Test
    fun `different hosts that call themselves the same thing are one platform`() {
        val items = listOf(
            link("1", "https://en.wikipedia.org/wiki/A", site = "Wikipedia"),
            link("2", "https://de.wikipedia.org/wiki/B", site = "Wikipedia"),
            link("3", "https://fr.wikipedia.org/wiki/C", site = "wikipedia"),
        )
        PlatformFacets.of(items).map { it.count } shouldBe listOf(3)
    }

    @Test
    fun `a long list folds after six, keeps the chosen platform in view, and never hides just one`() {
        fun facets(n: Int) = (1..n).map { PlatformFacet(Platform.named("site$it.com"), 100 - it) }
        val seven = facets(7)
        PlatformFacets.visible(seven, null, expanded = false) shouldBe seven // hiding one is not worth a button

        val ten = facets(10)
        PlatformFacets.visible(ten, null, expanded = false) shouldBe ten.take(6)
        PlatformFacets.visible(ten, ten[8].platform, expanded = false) shouldBe ten.take(6) + ten[8]
        PlatformFacets.visible(ten, ten[2].platform, expanded = false) shouldBe ten.take(6)
        PlatformFacets.visible(ten, null, expanded = true) shouldBe ten
    }

    @Test
    fun `a platform disappears with its last save`() {
        val before = listOf(link("1", "https://www.instagram.com/p/x/", site = "Instagram"), link("2", "https://github.com/a/b", site = "GitHub"))
        PlatformFacets.of(before).map { it.platform.id }.toSet() shouldBe setOf("instagram", "github")
        PlatformFacets.of(before.drop(1)).map { it.platform.id } shouldBe listOf("github")
    }
}
