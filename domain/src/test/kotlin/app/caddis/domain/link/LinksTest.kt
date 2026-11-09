package app.caddis.domain.link

import app.caddis.domain.model.ContentKind
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
        "https://www.instagram.com/reel/Cx9bT2/, instagram, REEL, ",
        "https://www.instagram.com/p/Cx9bT2/, instagram, POST, ",
        "https://www.instagram.com/oven.notes/, instagram, PROFILE, @oven.notes",
        "https://www.instagram.com/oven.notes/reel/Cx9/, instagram, REEL, @oven.notes",
        "https://www.facebook.com/reel/123, facebook, REEL, ",
        "https://fb.watch/abc, facebook, VIDEO, ",
        "https://www.facebook.com/greenhands/posts/1, facebook, POST, greenhands",
        "https://www.youtube.com/watch?v=x, youtube, VIDEO, ",
        "https://youtu.be/x, youtube, VIDEO, ",
        "https://www.youtube.com/shorts/x, youtube, SHORT, ",
        "https://www.youtube.com/@LenaCodes, youtube, CHANNEL, @LenaCodes",
        "https://www.reddit.com/r/Cooking/comments/1dal25/x, reddit, POST, r/Cooking",
        "https://www.reddit.com/r/Cooking/, reddit, SUBREDDIT, r/Cooking",
        "https://medium.com/@miradas/sleep-debt, medium, ARTICLE, @miradas",
        "https://x.com/audiobench/status/1789, x, POST, @audiobench",
        "https://twitter.com/audiobench, x, PROFILE, @audiobench",
        "https://mobile.twitter.com/audiobench, x, PROFILE, @audiobench",
    )
    fun `detects recognised services, kind and handle from the url`(url: String, platformId: String, kind: ContentKind, handle: String?) {
        val d = PlatformDetector.detect(url)
        d.platform.id shouldBe platformId
        d.platform.isKnownService shouldBe true
        d.kind shouldBe kind
        d.handle shouldBe handle
    }

    @ParameterizedTest
    @CsvSource(
        "https://drive.google.com/file/d/1AbC/view?usp=sharing, google-drive, Google Drive, FILE",
        "https://drive.google.com/drive/folders/1AbC, google-drive, Google Drive, FOLDER",
        "https://drive.google.com/drive/my-drive, google-drive, Google Drive, FOLDER",
        "https://docs.google.com/document/d/1AbC/edit, google-docs, Google Docs, DOCUMENT",
        "https://docs.google.com/spreadsheets/d/1AbC/edit, google-sheets, Google Sheets, DOCUMENT",
        "https://docs.google.com/presentation/d/1AbC/edit, google-slides, Google Slides, DOCUMENT",
        "https://docs.google.com/forms/d/1AbC/viewform, google-forms, Google Forms, DOCUMENT",
        "https://www.google.com/maps/place/Rome, google-maps, Google Maps, WEBPAGE",
        "https://github.com/JetBrains/kotlin, github, GitHub, WEBPAGE",
        "https://open.spotify.com/track/abc, spotify, Spotify, WEBPAGE",
        "https://www.threads.net/@someone, threads, Threads, PROFILE",
    )
    fun `recognises services beyond the original few`(url: String, id: String, label: String, kind: ContentKind) {
        val d = PlatformDetector.detect(url)
        d.platform.id shouldBe id
        d.platform.label shouldBe label
        d.kind shouldBe kind
    }

    @ParameterizedTest
    @CsvSource(
        "https://romeguide.example/day-trips, romeguide.example, WEBPAGE",
        "https://www.nytimes.com/2025/01/01/world/x.html, nytimes.com, WEBPAGE",
        "https://blog.example.com/post, example.com, WEBPAGE",
        "https://m.example.com/post, example.com, WEBPAGE",
        "https://news.bbc.co.uk/story, bbc.co.uk, WEBPAGE",
        "https://shop.example.com.au/p, example.com.au, WEBPAGE",
        "https://example.com/report.pdf, example.com, PDF",
        "http://192.168.1.5/page, 192.168.1.5, WEBPAGE",
        "https://docs.google.com/unknown/thing, google.com, WEBPAGE",
    )
    fun `every other site is its own platform named by its domain`(url: String, id: String, kind: ContentKind) {
        val d = PlatformDetector.detect(url)
        d.platform.id shouldBe id
        d.platform.label shouldBe id
        d.platform.isKnownService shouldBe false
        d.kind shouldBe kind
        d.handle shouldBe null
    }

    @Test
    fun `an unrelated host is not mistaken for a recognised one`() {
        PlatformDetector.detect("https://notinstagram.com/p/x").platform.id shouldBe "notinstagram.com"
        PlatformDetector.detect("https://myfacebook.com.au/p").platform.id shouldBe "myfacebook.com.au"
    }

    @Test
    fun `creator falls back to the site for ordinary sites only`() {
        PlatformDetector.detect("https://www.nytimes.com/a").creator shouldBe "nytimes.com"
        PlatformDetector.detect("https://github.com/a/b").creator shouldBe "github.com"
        PlatformDetector.detect("https://www.instagram.com/oven.notes/").creator shouldBe "@oven.notes"
        PlatformDetector.detect("https://www.youtube.com/watch?v=x").creator shouldBe ""
        PlatformDetector.detect("https://drive.google.com/file/d/1").creator shouldBe ""
    }

    @Test
    fun `a link with no usable host still gets a platform`() {
        PlatformDetector.detect("not a url").platform.id shouldBe "web"
    }
}
