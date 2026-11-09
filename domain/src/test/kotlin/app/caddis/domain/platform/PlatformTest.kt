package app.caddis.domain.platform

import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.details.DetailsPlanner
import app.caddis.domain.details.Favicons
import app.caddis.domain.details.FetchPlan
import app.caddis.domain.intent.CorrectionStats
import app.caddis.domain.link.PlatformDetector
import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

class PlatformTest {

    @Test
    fun `a platform is rebuilt from its id alone`() {
        val drive = PlatformDetector.detect("https://drive.google.com/file/d/1").platform
        for (p in listOf(Platform.INSTAGRAM, Platform.YOUTUBE, Platform.NOTE, Platform.site("nytimes.com"), drive)) {
            val back = Platform.fromId(p.id)
            back shouldBe p
            back.label shouldBe p.label
            back.neverContact shouldBe p.neverContact
        }
    }

    @Test
    fun `equality is by id`() {
        Platform.site("example.com") shouldBe Platform.fromId("example.com")
        (Platform.site("a.com") == Platform.site("b.com")) shouldBe false
    }

    @Test
    fun `ids the registry no longer knows still load as ordinary sites`() {
        val p = Platform.fromId("some-old-service")
        p.label shouldBe "Some-old-service"
        p.isKnownService shouldBe false
        p.neverContact shouldBe false
    }

    @Test
    fun `only Meta is never contacted`() {
        val never = PlatformRegistry.specs.filter { it.contact is Contact.Never }.map { it.id }.sorted()
        never shouldBe listOf("facebook", "instagram", "threads")
        Platform.INSTAGRAM.neverContact shouldBe true
        Platform.site("example.com").neverContact shouldBe false
        Platform.NOTE.neverContact shouldBe false
    }

    @Test
    fun `meta hosts and their image hosts get no site icon`() {
        PlatformRegistry.isNeverContactHost("scontent.cdninstagram.com") shouldBe true
        PlatformRegistry.isNeverContactHost("static.xx.fbcdn.net") shouldBe true
        PlatformRegistry.isNeverContactHost("example.com") shouldBe false
        Favicons.rootIcon("https://www.threads.net/@a") shouldBe null
        Favicons.rootIcon("https://drive.google.com/file/d/1") shouldBe "https://drive.google.com/favicon.ico"
    }

    @Test
    fun `fetch plan comes from the platform`() {
        DetailsPlanner.plan(Platform.fromId("threads"), "https://threads.net/@a") shouldBe FetchPlan.Skip
        DetailsPlanner.plan(Platform.NOTE, "x") shouldBe FetchPlan.Skip
        DetailsPlanner.plan(Platform.fromId("google-drive"), "https://drive.google.com/file/d/1") shouldBe FetchPlan.Page
        DetailsPlanner.plan(Platform.site("nytimes.com"), "https://nytimes.com/a") shouldBe FetchPlan.Page
        val reddit = DetailsPlanner.plan(Platform.REDDIT, "https://reddit.com/r/a?b=1").shouldBeInstanceOf<FetchPlan.OEmbed>()
        reddit.endpoint shouldBe "https://www.reddit.com/oembed?url=https%3A%2F%2Freddit.com%2Fr%2Fa%3Fb%3D1"
        reddit.alsoPage shouldBe true
    }

    @Test
    fun `analytics never carries a domain a person browsed`() {
        AnalyticsEvent.SaveCreated(Platform.site("my-private-blog.example"), AnalyticsEvent.SaveEntry.IN_APP, true)
            .params()["platform"] shouldBe "other"
        AnalyticsEvent.ItemOpened(Platform.site("nytimes.com"), AnalyticsEvent.OpenEntry.HOME).params()["platform"] shouldBe "other"
        AnalyticsEvent.SaveCreated(Platform.YOUTUBE, AnalyticsEvent.SaveEntry.IN_APP, true).params()["platform"] shouldBe "youtube"
        AnalyticsEvent.SaveCreated(Platform.NOTE, AnalyticsEvent.SaveEntry.IN_APP, false).params()["platform"] shouldBe "note"
    }

    @Test
    fun `corrections for ordinary sites are pooled, recognised services stay separate`() {
        CorrectionStats.key(Platform.site("a.com"), ContentKind.WEBPAGE) shouldBe "web:WEBPAGE"
        CorrectionStats.key(Platform.site("b.org"), ContentKind.WEBPAGE) shouldBe "web:WEBPAGE"
        CorrectionStats.key(Platform.INSTAGRAM, ContentKind.REEL) shouldBe "instagram:REEL"
    }

    @Test
    fun `every registry id is unique and lower case`() {
        PlatformRegistry.specs.map { it.id }.let { it.size shouldBe it.toSet().size }
        PlatformRegistry.specs.all { s -> s.id == s.id.lowercase() && s.hosts.all { it == it.lowercase() } } shouldBe true
    }
}

class PlatformFacetsTest {
    private fun link(id: String, url: String): Item {
        val d = PlatformDetector.detect(url)
        return Item(
            id = id, type = ItemType.LINK, url = url, normalizedUrl = url, platform = d.platform, kind = d.kind,
            intentId = "read", createdAt = Instant.EPOCH, status = ItemStatus.ACTIVE,
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
            link("1", "https://drive.google.com/file/d/1"),
            link("2", "https://www.nytimes.com/a"),
            link("3", "https://www.nytimes.com/b"),
            link("4", "https://github.com/a/b"),
            link("5", "https://romeguide.example/x"),
            note,
        )
        PlatformFacets.of(items).map { it.platform.label to it.count } shouldBe
            listOf("nytimes.com" to 2, "GitHub" to 1, "Google Drive" to 1, "romeguide.example" to 1)
        PlatformFacets.of(items).none { it.platform == Platform.INSTAGRAM } shouldBe true
    }

    @Test
    fun `a long list folds after six, keeps the chosen platform in view, and never hides just one`() {
        fun facets(n: Int) = (1..n).map { PlatformFacet(Platform.site("site$it.com"), 100 - it) }
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
        val before = listOf(link("1", "https://www.instagram.com/p/x/"), link("2", "https://github.com/a/b"))
        PlatformFacets.of(before).map { it.platform.id }.toSet() shouldBe setOf("instagram", "github")
        PlatformFacets.of(before.drop(1)).map { it.platform.id } shouldBe listOf("github")
    }
}
