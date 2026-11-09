package app.caddis.domain.intent

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Platform
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IntentGuesserTest {
    private val g = IntentGuesser()

    @Test
    fun `videos default to watch and articles to read`() {
        g.guess(GuessInput(Platform.YOUTUBE, ContentKind.VIDEO)) shouldBe Intents.WATCH
        g.guess(GuessInput(Platform.INSTAGRAM, ContentKind.REEL)) shouldBe Intents.WATCH
        g.guess(GuessInput(Platform.MEDIUM, ContentKind.ARTICLE)) shouldBe Intents.READ
        g.guess(GuessInput(Platform.site("example.com"), ContentKind.WEBPAGE)) shouldBe Intents.READ
        g.guess(GuessInput(Platform.NOTE, ContentKind.NOTE)) shouldBe Intents.KEEP
    }

    @Test
    fun `keywords in the title beat the platform default`() {
        g.guess(GuessInput(Platform.INSTAGRAM, ContentKind.REEL, title = "Brown butter cookies, 3 ways")) shouldBe Intents.COOK
        g.guess(GuessInput(Platform.YOUTUBE, ContentKind.VIDEO, title = "Intro to Rust ownership")) shouldBe Intents.LEARN
        g.guess(GuessInput(Platform.site("example.com"), ContentKind.WEBPAGE, title = "Rome day trips")) shouldBe Intents.GO
        g.guess(GuessInput(Platform.X, ContentKind.POST, title = "Thread: noise-cancelling headphones under 15k")) shouldBe Intents.BUY
        g.guess(GuessInput(Platform.REDDIT, ContentKind.POST, title = "Desk setup ideas")) shouldBe Intents.TRY
    }

    @Test
    fun `keywords in the url count before details load`() {
        g.guess(GuessInput(Platform.site("example.com"), ContentKind.WEBPAGE, url = "https://food.example/recipes/dal")) shouldBe Intents.COOK
    }

    @Test
    fun `corrections shift the guess once there are enough of them`() {
        val key = CorrectionStats.key(Platform.INSTAGRAM, ContentKind.REEL)
        val once = CorrectionStats(mapOf(key to mapOf(Intents.TRY to 1)))
        val twice = CorrectionStats(mapOf(key to mapOf(Intents.TRY to 2, Intents.READ to 1)))
        g.guess(GuessInput(Platform.INSTAGRAM, ContentKind.REEL), once) shouldBe Intents.WATCH
        g.guess(GuessInput(Platform.INSTAGRAM, ContentKind.REEL), twice) shouldBe Intents.TRY
    }

    @Test
    fun `a deleted intent is never guessed`() {
        val noCook = IntentGuesser(Intents.defaults.map { it.id }.toSet() - Intents.COOK)
        noCook.guess(GuessInput(Platform.YOUTUBE, ContentKind.VIDEO, title = "Lemon orzo recipe")) shouldBe Intents.WATCH
    }
}
