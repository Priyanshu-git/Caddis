package app.caddis.domain.details

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.EditableField
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class DetailsTest {

    private fun page(head: String) = "<html><head>$head</head><body></body></html>"
    private fun parse(head: String, url: String = "https://e.com/p") = DetailsParser.parsePage(page(head), url)

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
        val d = DetailsParser.parsePage(html, "https://medium.com/@miradas/sleep-debt")
        d.title shouldBe "Why sleep debt compounds"
        d.description shouldBe "A few short nights add up."
        d.creator shouldBe "Mira Das"
        d.thumbnailUrl shouldBe "https://medium.com/img/sleep.jpg"
        d.lengthSeconds shouldBe 360
    }

    @Test
    fun `the site names itself from its own page`() {
        parse("""<meta property="og:site_name" content="ChatGPT"><meta property="og:title" content="Pasta plan">""").siteName shouldBe "ChatGPT"
        parse("""<meta name="application-name" content="Google Drive"><title>Plan</title>""").siteName shouldBe "Google Drive"
        parse("""<meta property="og:site_name" content="  The   Guardian "><title>Plan</title>""").siteName shouldBe "The Guardian"
        // og:site_name wins over application-name
        parse("""<meta name="application-name" content="B"><meta property="og:site_name" content="A"><title>t</title>""").siteName shouldBe "A"
        parse("<title>t</title>").siteName shouldBe null
    }

    @Test
    fun `a site name that is not a name is ignored`() {
        parse("""<meta property="og:site_name" content="https://example.com"><title>t</title>""").siteName shouldBe null
        parse("""<meta property="og:site_name" content="example.com/blog"><title>t</title>""").siteName shouldBe null
        parse("""<meta property="og:site_name" content="${"x".repeat(41)}"><title>t</title>""").siteName shouldBe null
        parse("""<meta property="og:site_name" content="---"><title>t</title>""").siteName shouldBe null
        Platform.cleanSiteName("   ") shouldBe null
        Platform.cleanSiteName(null) shouldBe null
        Platform.cleanSiteName("x".repeat(40)) shouldBe "x".repeat(40)
    }

    @Test
    fun `the site name is not used as the creator`() {
        val d = parse("""<meta property="og:site_name" content="ChatGPT"><meta property="og:title" content="Pasta plan">""")
        d.creator shouldBe null
        parse("""<meta property="og:site_name" content="ChatGPT"><meta name="author" content="Mira"><title>t</title>""").creator shouldBe "Mira"
    }

    @Test
    fun `the page says what kind of thing it is`() {
        parse("""<meta property="og:type" content="video.other"><title>t</title>""").kind shouldBe ContentKind.VIDEO
        parse("""<meta property="og:type" content="video"><title>t</title>""").kind shouldBe ContentKind.VIDEO
        parse("""<meta name="twitter:card" content="player"><title>t</title>""").kind shouldBe ContentKind.VIDEO
        parse("""<meta property="og:type" content="article"><title>t</title>""").kind shouldBe ContentKind.ARTICLE
        parse("""<meta property="og:type" content="profile"><title>t</title>""").kind shouldBe ContentKind.PROFILE
        parse("""<meta property="og:type" content="website"><title>t</title>""").kind shouldBe null
        parse("<title>t</title>").kind shouldBe null
    }

    @Test
    fun `reads a video length from structured data or open graph`() {
        parse("""<meta itemprop="duration" content="PT12M3S"><meta property="og:title" content="Orzo">""").lengthSeconds shouldBe 723
        parse("""<meta property="og:video:duration" content="95"><meta property="og:title" content="Clip">""").lengthSeconds shouldBe 95
        parse("""<meta property="video:duration" content="0"><meta property="og:title" content="Clip">""").lengthSeconds shouldBe null
        DetailsParser.isoDurationSeconds("PT1H2M") shouldBe 3720
        DetailsParser.isoDurationSeconds("garbage") shouldBe null
    }

    @Test
    fun `an oembed endpoint is found in the page, not in a list of sites`() {
        val link = """<link rel="alternate" type="application/json+oembed" href="https://www.youtube.com/oembed?url=x&format=json">"""
        parse(link + "<title>t</title>").oEmbedUrl shouldBe "https://www.youtube.com/oembed?url=x&format=json"
        parse("""<link rel="alternate" type="application/json+oembed" href="/oembed?u=1"><title>t</title>""", "http://e.com/p").oEmbedUrl shouldBe null
        parse("""<link rel="alternate" type="application/json+oembed" href="/oembed?u=1"><title>t</title>""", "https://e.com/p").oEmbedUrl shouldBe "https://e.com/oembed?u=1"
        parse("""<link rel="alternate" type="text/xml+oembed" href="https://e.com/o.xml"><title>t</title>""").oEmbedUrl shouldBe null
        parse("<title>t</title>").oEmbedUrl shouldBe null
    }

    @Test
    fun `parses an oembed response from any site`() {
        val video = DetailsParser.parseOEmbed(
            """{"title":"One-pan lemon orzo","author_name":"Pasta Daily","thumbnail_url":"https://i.ytimg.com/vi/x/hq.jpg","provider_name":"YouTube","duration":723}""",
        )
        video.title shouldBe "One-pan lemon orzo"
        video.creator shouldBe "Pasta Daily"
        video.thumbnailUrl shouldBe "https://i.ytimg.com/vi/x/hq.jpg"
        video.siteName shouldBe "YouTube"
        video.lengthSeconds shouldBe 723

        val post = DetailsParser.parseOEmbed(
            """{"author_name":"Audio Bench","html":"<blockquote><p>Six pairs compared on comfort and battery.</p></blockquote>","provider_name":"Twitter"}""",
        )
        post.title shouldBe "Six pairs compared on comfort and battery."
        post.description shouldBe "Six pairs compared on comfort and battery."
        post.creator shouldBe "Audio Bench"
        post.siteName shouldBe "Twitter"

        DetailsParser.parseOEmbed("""{"author_name":"Audio Bench"}""").title shouldBe "Post by Audio Bench"
        DetailsParser.parseOEmbed("not json").isEmpty shouldBe true
    }

    @Test
    fun `challenge pages and site-wide titles are treated as no details`() {
        fun page(title: String, site: String = "") =
            "<html><head><title>$title</title>$site<meta property=\"og:image\" content=\"https://x.com/logo.png\"></head></html>"
        listOf("Just a moment...", "Attention Required! | Cloudflare", "Access denied", "Whoa there, pardner!", "403 Forbidden", "404").forEach { title ->
            DetailsParser.parsePage(page(title), "https://e.com/p").isEmpty shouldBe true
        }
        // A login wall that serves only the site's own name: no title, but we still learn what the site is called.
        val wall = DetailsParser.parsePage(page("Instagram", """<meta property="og:site_name" content="Instagram">"""), "https://e.com/p")
        wall.title shouldBe null
        wall.thumbnailUrl shouldBe null
        wall.siteName shouldBe "Instagram"
        // A challenge page does not get to name the site.
        DetailsParser.parsePage(page("Just a moment...", """<meta property="og:site_name" content="Cloudflare">"""), "https://e.com/p").siteName shouldBe null
    }

    @Test
    fun `real titles that merely start with a risky word are kept`() {
        fun title(t: String, site: String = "") = DetailsParser.parsePage("<html><head><title>$t</title>$site</head></html>", "https://e.com/p").title
        title("Error handling in Go") shouldBe "Error handling in Go"
        title("Blocked drains: a field guide") shouldBe "Blocked drains: a field guide"
        title("Forbidden fruit and other pies") shouldBe "Forbidden fruit and other pies"
        title("Reddit's API changes, explained", """<meta property="og:site_name" content="Reddit">""") shouldBe "Reddit's API changes, explained"
        title("Medium rare: how to steak", """<meta property="og:site_name" content="Medium">""") shouldBe "Medium rare: how to steak"
    }

    private val now = Instant.parse("2026-11-04T05:00:00Z")
    private val item = Item(
        id = "i", type = ItemType.LINK, url = "https://chatgpt.com/share/1", normalizedUrl = "chatgpt.com/share/1",
        platform = Platform.fromUrl("https://chatgpt.com/share/1"), kind = ContentKind.WEBPAGE, title = "My title",
        description = "Old description", intentId = Intents.READ, createdAt = now, userEdited = setOf(EditableField.TITLE),
    )

    @Test
    fun `merging details never overwrites what the person edited or blanks known fields`() {
        val merged = item.mergeDetails(LinkDetails(title = "Their title", description = null, creator = "Site"), now)
        merged.title shouldBe "My title"
        merged.description shouldBe "Old description"
        merged.creator shouldBe "Site"
        merged.detailsFetchedAt shouldBe now
        merged.revision shouldBe 2
    }

    @Test
    fun `merging details renames the platform to what the site calls itself`() {
        item.platform.label shouldBe "chatgpt.com"
        val named = item.mergeDetails(LinkDetails(siteName = "ChatGPT"), now)
        named.platform.label shouldBe "ChatGPT"
        named.platform shouldBe Platform.named("chatgpt")
        item.mergeDetails(LinkDetails(title = "x"), now).platform.label shouldBe "chatgpt.com"
    }

    @Test
    fun `a note never takes a site name`() {
        val note = item.copy(type = ItemType.NOTE, url = null, platform = Platform.NOTE, kind = ContentKind.NOTE)
        note.mergeDetails(LinkDetails(siteName = "ChatGPT"), now).platform shouldBe Platform.NOTE
    }

    @Test
    fun `a page only refines a plain webpage`() {
        item.mergeDetails(LinkDetails(kind = ContentKind.VIDEO), now).kind shouldBe ContentKind.VIDEO
        item.copy(kind = ContentKind.PDF).mergeDetails(LinkDetails(kind = ContentKind.VIDEO), now).kind shouldBe ContentKind.PDF
        item.mergeDetails(LinkDetails(title = "x"), now).kind shouldBe ContentKind.WEBPAGE
    }

    @Test
    fun `formats lengths`() {
        formatLength(723, isVideo = true) shouldBe "13 min"
        formatLength(360, isVideo = false) shouldBe "6 min read"
        formatLength(3900, isVideo = true) shouldBe "1 h 5 min"
        formatLength(null, isVideo = true) shouldBe ""
    }
}
