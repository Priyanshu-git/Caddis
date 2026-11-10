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

    @Test
    fun `a link with no title is shown without its scheme, whatever the site`() {
        DefaultTitles.of("https://www.bbcgoodfood.com/recipes/classic-lasagne") shouldBe "www.bbcgoodfood.com/recipes/classic-lasagne"
        DefaultTitles.of("http://example.com/a") shouldBe "example.com/a"
        DefaultTitles.of("https://www.instagram.com/reel/C9abcdEFGhi/") shouldBe "www.instagram.com/reel/C9abcdEFGhi/"
        DefaultTitles.of("") shouldBe "Untitled"
    }

    @Test
    fun `display title prefers a real title, then the default, then the note fallback`() {
        val base = Item(
            id = "i", type = ItemType.LINK, url = "https://example.com/a", normalizedUrl = "example.com/a",
            platform = Platform.fromUrl("https://example.com/a"), kind = ContentKind.WEBPAGE, intentId = Intents.KEEP, createdAt = Instant.EPOCH,
        )
        base.displayTitle shouldBe "example.com/a"
        base.copy(title = "Wild places").displayTitle shouldBe "Wild places"
        base.copy(type = ItemType.NOTE, url = null, platform = Platform.NOTE, kind = ContentKind.NOTE).displayTitle shouldBe "Untitled note"
    }
}
