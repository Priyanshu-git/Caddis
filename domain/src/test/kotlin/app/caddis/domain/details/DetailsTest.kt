package app.caddis.domain.details

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.EditableField
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

class DetailsTest {

    @Test
    fun `meta platforms are never fetched`() {
        DetailsPlanner.plan(Platform.INSTAGRAM, "https://instagram.com/reel/x") shouldBe FetchPlan.Skip
        DetailsPlanner.plan(Platform.FACEBOOK, "https://facebook.com/reel/x") shouldBe FetchPlan.Skip
        DetailsPlanner.plan(Platform.YOUTUBE, "https://youtu.be/x").shouldBeInstanceOf<FetchPlan.OEmbed>().endpoint shouldContain "youtube.com/oembed"
        DetailsPlanner.plan(Platform.X, "https://x.com/a/status/1").shouldBeInstanceOf<FetchPlan.OEmbed>().endpoint shouldContain "publish.twitter.com"
        DetailsPlanner.plan(Platform.MEDIUM, "https://medium.com/@a/b") shouldBe FetchPlan.Page
    }

    @Test
    fun `parses open graph tags and medium reading time`() {
        val html = """
            <html><head>
              <title>fallback</title>
              <meta property="og:title" content="Why sleep debt   compounds">
              <meta property="og:description" content="A few short nights add up.">
              <meta property="og:image" content="/img/sleep.jpg">
              <meta name="author" content="Mira Das">
              <meta name="twitter:data1" content="6 min read">
            </head><body></body></html>
        """.trimIndent()
        val d = DetailsParser.parsePage(html, "https://medium.com/@miradas/sleep-debt", Platform.MEDIUM)
        d.title shouldBe "Why sleep debt compounds"
        d.description shouldBe "A few short nights add up."
        d.creator shouldBe "Mira Das"
        d.thumbnailUrl shouldBe "https://medium.com/img/sleep.jpg"
        d.lengthSeconds shouldBe 360
    }

    @Test
    fun `reads youtube length from structured data`() {
        val html = """<html><head><meta itemprop="duration" content="PT12M3S"><meta property="og:title" content="Orzo"></head></html>"""
        DetailsParser.parsePage(html, "https://youtube.com/watch?v=x", Platform.YOUTUBE).lengthSeconds shouldBe 723
        DetailsParser.isoDurationSeconds("PT1H2M") shouldBe 3720
        DetailsParser.isoDurationSeconds("garbage") shouldBe null
    }

    @Test
    fun `parses youtube and x oembed`() {
        val yt = DetailsParser.parseOEmbed(
            """{"title":"One-pan lemon orzo","author_name":"Pasta Daily","thumbnail_url":"https://i.ytimg.com/vi/x/hq.jpg"}""",
            Platform.YOUTUBE,
        )
        yt.title shouldBe "One-pan lemon orzo"
        yt.creator shouldBe "Pasta Daily"
        yt.thumbnailUrl shouldBe "https://i.ytimg.com/vi/x/hq.jpg"

        val x = DetailsParser.parseOEmbed(
            """{"author_name":"Audio Bench","author_url":"https://twitter.com/audiobench","html":"<blockquote><p>Six pairs compared on comfort and battery.</p></blockquote>"}""",
            Platform.X,
        )
        x.title shouldBe "Six pairs compared on comfort and battery."
        x.creator shouldBe "@audiobench"
        DetailsParser.parseOEmbed("not json", Platform.X).isEmpty shouldBe true
    }

    @Test
    fun `merging details never overwrites what the person edited or blanks known fields`() {
        val now = Instant.parse("2026-11-04T05:00:00Z")
        val item = Item(
            id = "i", type = ItemType.LINK, url = "https://e.com", normalizedUrl = "e.com", platform = Platform.WEB,
            kind = ContentKind.WEBPAGE, title = "My title", description = "Old description", intentId = Intents.READ,
            createdAt = now, userEdited = setOf(EditableField.TITLE),
        )
        val merged = item.mergeDetails(LinkDetails(title = "Their title", description = null, creator = "Site"), now)
        merged.title shouldBe "My title"
        merged.description shouldBe "Old description"
        merged.creator shouldBe "Site"
        merged.detailsFetchedAt shouldBe now
        merged.revision shouldBe 2
    }

    @Test
    fun `formats lengths`() {
        formatLength(723, isVideo = true) shouldBe "13 min"
        formatLength(360, isVideo = false) shouldBe "6 min read"
        formatLength(3900, isVideo = true) shouldBe "1 h 5 min"
        formatLength(null, isVideo = true) shouldBe ""
    }
}
