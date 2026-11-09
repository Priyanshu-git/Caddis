package app.caddis.domain.share

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant

class ShareTextTest {
    private val link = Item(
        id = "i", type = ItemType.LINK, url = "https://youtu.be/abc123", normalizedUrl = "youtu.be/abc123",
        platform = Platform.YOUTUBE, kind = ContentKind.VIDEO, title = "How rivers carry stones",
        intentId = Intents.KEEP, createdAt = Instant.EPOCH,
    )

    @Test
    fun `a titled link shares title, link and the sign-off`() {
        ShareText.of(link) shouldBe "How rivers carry stones\nhttps://youtu.be/abc123\n\nShared via Caddis app"
    }

    @Test
    fun `no real title means the link goes first, not a guessed default`() {
        ShareText.of(link.copy(title = "")) shouldBe "https://youtu.be/abc123\n\nShared via Caddis app"
        ShareText.of(link.copy(title = "   ")) shouldBe "https://youtu.be/abc123\n\nShared via Caddis app"
    }

    @Test
    fun `titles are one line and capped`() {
        ShareText.of(link.copy(title = "Rivers\n  and\tstones"))!!.lines().first() shouldBe "Rivers and stones"
        val long = ShareText.of(link.copy(title = "a".repeat(300)))!!.lines().first()
        long.length shouldBe ShareText.MAX_TITLE_CHARS
        long.last() shouldBe '…'
        ShareText.subject(link.copy(title = "a".repeat(300)))!!.length shouldBe ShareText.MAX_TITLE_CHARS
    }

    @Test
    fun `private words are never shared`() {
        val text = ShareText.of(link.copy(reason = "for my sister", note = "# secret plan", description = "a long blurb"))!!
        text shouldNotContain "sister"
        text shouldNotContain "secret"
        text shouldNotContain "blurb"
    }

    @Test
    fun `a save without a link has nothing to share`() {
        val note = link.copy(type = ItemType.NOTE, url = null, platform = Platform.NOTE, kind = ContentKind.NOTE)
        ShareText.of(note) shouldBe null
        ShareText.subject(note) shouldBe null
    }

    @Test
    fun `the shared link drops tracking ids but keeps the rest`() {
        ShareText.cleanUrl("https://youtu.be/abc123?si=XyZ&t=90s") shouldBe "https://youtu.be/abc123?t=90s"
        ShareText.cleanUrl("https://www.instagram.com/reel/C9abc/?igsh=MWx&utm_source=ig_web") shouldBe "https://www.instagram.com/reel/C9abc/"
        ShareText.cleanUrl("https://example.com/a?b=1&fbclid=zzz&c=2#part") shouldBe "https://example.com/a?b=1&c=2#part"
        ShareText.cleanUrl("https://example.com/a?id=7#top") shouldBe "https://example.com/a?id=7#top"
        ShareText.cleanUrl("https://example.com/a") shouldBe "https://example.com/a"
    }

    @Test
    fun `the cleaned link is what gets shared`() {
        ShareText.of(link.copy(url = "https://youtu.be/abc123?si=XyZ", title = "")) shouldBe
            "https://youtu.be/abc123\n\nShared via Caddis app"
    }
}
