package app.caddis.domain.link

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Platform
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class LinksTest {

    @Test
    fun `accepts a bare host and adds https`() {
        val r = Links.check("youtube.com/watch?v=abc").shouldBeInstanceOf<LinkCheck.Valid>()
        r.url shouldBe "https://youtube.com/watch?v=abc"
        r.normalized shouldBe "youtube.com/watch?v=abc"
    }

    @ParameterizedTest
    @ValueSource(strings = ["tel:123", "mailto:a@b.com", "javascript:alert(1)", "file:///sdcard/x", "intent://scan"])
    fun `rejects non web schemes`(raw: String) {
        Links.check(raw).shouldBeInstanceOf<LinkCheck.Invalid>().message shouldBe "Only web links (http or https) can be saved."
    }

    @ParameterizedTest
    @ValueSource(strings = ["hello", "not a link", "localhost", "https://"])
    fun `rejects things that are not web links`(raw: String) {
        Links.check(raw).shouldBeInstanceOf<LinkCheck.Invalid>()
    }

    @Test
    fun `empty input asks for a link`() {
        Links.check("  ").shouldBeInstanceOf<LinkCheck.Invalid>().message shouldBe "Paste or type a link first."
    }

    @ParameterizedTest
    @CsvSource(
        "https://www.instagram.com/reel/Cx9bT2/?igsh=abc, instagram.com/reel/Cx9bT2",
        "http://youtube.com/watch?v=Lm0&utm_source=x&si=zz, youtube.com/watch?v=Lm0",
        "https://youtu.be/Lm0?si=abc, youtube.com/watch?v=Lm0",
        "https://m.youtube.com/watch?v=Lm0, youtube.com/watch?v=Lm0",
        "https://twitter.com/a/status/1?s=20, x.com/a/status/1",
        "https://Example.com/Path/#frag, example.com/Path",
        "https://example.com/p?b=2&a=1, example.com/p?a=1&b=2",
    )
    fun `normalises links so duplicates match`(input: String, expected: String) {
        Links.normalize(input) shouldBe expected
    }

    @Test
    fun `extracts the first link from shared text`() {
        Links.extractFirst("Look at this! https://youtu.be/abc?si=1. So good") shouldBe "https://youtu.be/abc?si=1"
        Links.extractFirst("no links here") shouldBe null
    }
}

class PlatformDetectorTest {

    @ParameterizedTest
    @CsvSource(
        "https://www.instagram.com/reel/Cx9bT2/, INSTAGRAM, REEL, ",
        "https://www.instagram.com/p/Cx9bT2/, INSTAGRAM, POST, ",
        "https://www.instagram.com/oven.notes/, INSTAGRAM, PROFILE, @oven.notes",
        "https://www.instagram.com/oven.notes/reel/Cx9/, INSTAGRAM, REEL, @oven.notes",
        "https://www.facebook.com/reel/123, FACEBOOK, REEL, ",
        "https://fb.watch/abc, FACEBOOK, VIDEO, ",
        "https://www.facebook.com/greenhands/posts/1, FACEBOOK, POST, greenhands",
        "https://www.youtube.com/watch?v=x, YOUTUBE, VIDEO, ",
        "https://youtu.be/x, YOUTUBE, VIDEO, ",
        "https://www.youtube.com/shorts/x, YOUTUBE, SHORT, ",
        "https://www.youtube.com/@LenaCodes, YOUTUBE, CHANNEL, @LenaCodes",
        "https://www.reddit.com/r/Cooking/comments/1dal25/x, REDDIT, POST, r/Cooking",
        "https://www.reddit.com/r/Cooking/, REDDIT, SUBREDDIT, r/Cooking",
        "https://medium.com/@miradas/sleep-debt, MEDIUM, ARTICLE, @miradas",
        "https://x.com/audiobench/status/1789, X, POST, @audiobench",
        "https://twitter.com/audiobench, X, PROFILE, @audiobench",
        "https://example.com/report.pdf, WEB, PDF, ",
        "https://romeguide.example/day-trips, WEB, WEBPAGE, ",
    )
    fun `detects platform, kind and handle from the url`(url: String, platform: Platform, kind: ContentKind, handle: String?) {
        val d = PlatformDetector.detect(url)
        d.platform shouldBe platform
        d.kind shouldBe kind
        d.handle shouldBe handle
    }
}
