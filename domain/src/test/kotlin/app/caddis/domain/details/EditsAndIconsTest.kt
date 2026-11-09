package app.caddis.domain.details

import app.caddis.domain.link.Links
import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.EditableField
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class EditsAndIconsTest {
    private val now = Instant.parse("2026-11-08T05:00:00Z")

    private fun link(url: String, platform: Platform = Platform.site("example.com"), favicon: String? = null) = Item(
        id = "i", type = ItemType.LINK, url = url, normalizedUrl = url, platform = platform,
        kind = ContentKind.WEBPAGE, intentId = Intents.READ, createdAt = now, faviconUrl = favicon,
        title = "Fetched title", description = "Fetched description", creator = "Site",
    )

    @Test
    fun `reads the touch icon first, then any icon, resolved against the page`() {
        val html = """<html><head><title>T</title>
            <link rel="shortcut icon" href="/favicon-32.png">
            <link rel="apple-touch-icon" sizes="180x180" href="/apple.png">
            </head></html>"""
        DetailsParser.parsePage(html, "https://blog.example.com/post", Platform.site("example.com")).faviconUrl shouldBe "https://blog.example.com/apple.png"
        val plain = """<html><head><title>T</title><link rel="icon" type="image/svg+xml" href="/i.svg"><link rel="Shortcut Icon" href="https://cdn.example.com/f.ico"></head></html>"""
        DetailsParser.parsePage(plain, "https://example.com/", Platform.site("example.com")).faviconUrl shouldBe "https://cdn.example.com/f.ico"
    }

    @Test
    fun `an icon alone is not details`() {
        val html = """<html><head><link rel="icon" href="/f.png"></head></html>"""
        DetailsParser.parsePage(html, "https://example.com/", Platform.site("example.com")).isEmpty shouldBe true
    }

    @Test
    fun `favicon falls back to the site root and never points at meta`() {
        Favicons.forItem(link("https://www.example.com/a/b")) shouldBe "https://example.com/favicon.ico"
        Favicons.forItem(link("https://example.com/a", favicon = "https://example.com/i.png")) shouldBe "https://example.com/i.png"
        Favicons.forItem(link("https://instagram.com/reel/x", Platform.INSTAGRAM)) shouldBe null
        Favicons.forItem(link("https://facebook.com/x", Platform.FACEBOOK)) shouldBe null
        Favicons.forItem(link("https://example.com/a", favicon = "https://static.xx.fbcdn.net/i.png")) shouldBe "https://example.com/favicon.ico"
        Favicons.forItem(link("https://example.com").copy(type = ItemType.NOTE, url = null, platform = Platform.NOTE)) shouldBe null
    }

    @Test
    fun `merging details keeps a known favicon when the fetch has none`() {
        val item = link("https://example.com", favicon = "https://example.com/i.png")
        item.mergeDetails(LinkDetails(title = "New"), now).faviconUrl shouldBe "https://example.com/i.png"
        item.mergeDetails(LinkDetails(faviconUrl = "https://example.com/j.png"), now).faviconUrl shouldBe "https://example.com/j.png"
    }

    @Test
    fun `edited fields become yours and survive the next fetch`() {
        val edited = link("https://example.com").withEdits(DetailEdits("  My   title ", "Fetched description", "Site"), now)
        edited.title shouldBe "My title"
        edited.userEdited shouldBe setOf(EditableField.TITLE)
        edited.revision shouldBe 2
        edited.mergeDetails(LinkDetails(title = "Their title", description = "New description"), now).let {
            it.title shouldBe "My title"
            it.description shouldBe "New description"
        }
    }

    @Test
    fun `clearing a field hands it back to Caddis and saving unchanged does nothing`() {
        val mine = link("https://example.com").copy(userEdited = setOf(EditableField.CREATOR), creator = "Me")
        val cleared = mine.withEdits(DetailEdits("Fetched title", "Fetched description", ""), now)
        cleared.creator shouldBe ""
        cleared.userEdited shouldBe emptySet()
        cleared.mergeDetails(LinkDetails(creator = "Site"), now).creator shouldBe "Site"

        val same = link("https://example.com")
        same.withEdits(DetailEdits("Fetched title", "Fetched description", "Site"), now) shouldBe same
    }

    @Test
    fun `edits are capped`() {
        link("https://example.com").withEdits(DetailEdits("x".repeat(500), "", ""), now).title.length shouldBe EditLimits.TITLE
    }

    @Test
    fun `typed text that is not a link can become a note`() {
        Links.couldBeNote("buy oat milk") shouldBe true
        Links.couldBeNote("mailto:a@b.com") shouldBe true
        Links.couldBeNote("   ") shouldBe false
        Links.couldBeNote("example.com/page") shouldBe false
        Links.noteTitleFrom("\n  Buy oat milk  \nand eggs") shouldBe "Buy oat milk"
        Links.noteTitleFrom("y".repeat(120)).length shouldBe 80
    }

    private fun valid(u: String) = Links.check(u) as app.caddis.domain.link.LinkCheck.Valid

    @Test
    fun `a new link re-reads the platform and drops what was fetched for the old one`() {
        val old = link("https://example.com/a", favicon = "https://example.com/i.png")
            .copy(thumbnailUrl = "https://example.com/t.jpg", lengthSeconds = 300, detailsFetchedAt = now)
        val moved = old.withLink(valid("youtu.be/abc"), now)
        moved.platform shouldBe Platform.YOUTUBE
        moved.kind shouldBe ContentKind.VIDEO
        moved.url shouldBe "https://youtu.be/abc"
        moved.normalizedUrl shouldBe "youtube.com/watch?v=abc"
        moved.title shouldBe ""
        moved.description shouldBe ""
        moved.thumbnailUrl shouldBe null
        moved.lengthSeconds shouldBe null
        moved.faviconUrl shouldBe null
        moved.detailsFetchedAt shouldBe null
        moved.needsDetails() shouldBe true
        moved.revision shouldBe 2
    }

    @Test
    fun `a new link keeps what the person wrote`() {
        val mine = link("https://example.com/a").copy(title = "My title", userEdited = setOf(EditableField.TITLE), detailsFetchedAt = now)
        val moved = mine.withLink(valid("https://other.org/b"), now)
        moved.title shouldBe "My title"
        moved.creator shouldBe "other.org"
        moved.platform shouldBe Platform.site("other.org")
    }

    @Test
    fun `instagram links read the handle and are never refetched`() {
        val moved = link("https://example.com/a").withLink(valid("https://www.instagram.com/oven.notes/reel/xyz/"), now)
        moved.platform shouldBe Platform.INSTAGRAM
        moved.needsDetails() shouldBe false
    }

    @Test
    fun `a cosmetic link change keeps the details`() {
        val item = link("https://example.com/a?utm_source=x").copy(normalizedUrl = "example.com/a", detailsFetchedAt = now)
        val tidy = item.withLink(valid("https://example.com/a"), now)
        tidy.url shouldBe "https://example.com/a"
        tidy.title shouldBe "Fetched title"
        tidy.detailsFetchedAt shouldBe now
        item.withLink(valid("https://example.com/a?utm_source=x"), now) shouldBe item
    }
}
