package app.caddis.domain.link

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class DefaultTitlesTest {

    private fun title(url: String): String {
        val d = PlatformDetector.detect(url)
        return DefaultTitles.of(d.platform, d.kind, url)
    }

    @Test
    fun `instagram and facebook get readable titles from the url alone`() {
        title("https://www.instagram.com/reel/C9abcdEFGhi/") shouldBe "Instagram reel"
        title("https://www.instagram.com/p/C9abcdEFGhi/") shouldBe "Instagram post"
        title("https://www.instagram.com/natgeo/") shouldBe "@natgeo on Instagram"
        title("https://www.instagram.com/natgeo/reel/C9abcdEFGhi/") shouldBe "Instagram reel by @natgeo"
        title("https://www.facebook.com/nasa/posts/12345") shouldBe "Facebook post by nasa"
        title("https://www.facebook.com/reel/12345") shouldBe "Facebook reel"
        title("https://fb.watch/abc/") shouldBe "Facebook video"
    }

    @Test
    fun `reddit and medium use the slug in the url`() {
        title("https://www.reddit.com/r/androiddev/comments/abc123/what_is_your_favourite_compose_tip/") shouldBe
            "What is your favourite compose tip · r/androiddev"
        title("https://www.reddit.com/r/androiddev/comments/abc123/") shouldBe "Reddit post · r/androiddev"
        title("https://www.reddit.com/r/androiddev/") shouldBe "r/androiddev on Reddit"
        title("https://redd.it/abc123") shouldBe "Reddit post"
        title("https://medium.com/@mira/why-sleep-debt-compounds-3f2a9c1b7d4e") shouldBe "Why sleep debt compounds · @mira"
        title("https://medium.com/@mira") shouldBe "Medium article · @mira"
        title("https://medium.com/@someone/a-post-123") shouldBe "A post 123 · @someone"
    }

    @Test
    fun `slugs are decoded, capped and never numeric noise`() {
        title("https://medium.com/@a/caf%C3%A9-notes-3f2a9c1b7d4e") shouldBe "Café notes · @a"
        title("https://medium.com/@a/123456") shouldBe "Medium article · @a"
        val long = "word-".repeat(40).trimEnd('-')
        title("https://medium.com/@a/$long").removeSuffix(" · @a").length shouldBe 80
    }

    @Test
    fun `x and youtube have defaults too`() {
        title("https://x.com/jack/status/20") shouldBe "Post by @jack"
        title("https://x.com/jack") shouldBe "@jack on X"
        title("https://www.youtube.com/watch?v=abc") shouldBe "YouTube video"
        title("https://www.youtube.com/shorts/abc") shouldBe "YouTube short"
        title("https://www.youtube.com/@pastadaily") shouldBe "@pastadaily on YouTube"
    }

    @Test
    fun `other web links keep the link without its scheme`() {
        title("https://www.bbcgoodfood.com/recipes/classic-lasagne") shouldBe "www.bbcgoodfood.com/recipes/classic-lasagne"
    }

    @Test
    fun `display title prefers a real title, then the default, then the note fallback`() {
        val base = Item(
            id = "i", type = ItemType.LINK, url = "https://www.instagram.com/natgeo/", normalizedUrl = "instagram.com/natgeo",
            platform = Platform.INSTAGRAM, kind = ContentKind.PROFILE, intentId = Intents.KEEP, createdAt = Instant.EPOCH,
        )
        base.displayTitle shouldBe "@natgeo on Instagram"
        base.copy(title = "Wild places").displayTitle shouldBe "Wild places"
        base.copy(type = ItemType.NOTE, url = null, platform = Platform.NOTE, kind = ContentKind.NOTE).displayTitle shouldBe "Untitled note"
    }
}
