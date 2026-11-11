package com.nexxlabs.caddis.domain.link

import com.nexxlabs.caddis.domain.model.ContentKind
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
        "https://youtu.be/Lm0?si=abc, youtu.be/Lm0",
        "https://m.youtube.com/watch?v=Lm0, youtube.com/watch?v=Lm0",
        "https://twitter.com/a/status/1?s=20, twitter.com/a/status/1",
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
        "https://chatgpt.com/share/6af24282-1abc-83ec-9b32-ee08b94575da, chatgpt.com, WEBPAGE",
        "https://www.nytimes.com/2025/01/01/world/x.html, nytimes.com, WEBPAGE",
        "https://www.instagram.com/reel/Cx9bT2/, instagram.com, WEBPAGE",
        "https://youtu.be/x, youtu.be, WEBPAGE",
        "https://drive.google.com/file/d/1AbC/view, drive.google.com, WEBPAGE",
        "https://docs.google.com/spreadsheets/d/1AbC/edit, docs.google.com, WEBPAGE",
        "https://m.example.com/post, example.com, WEBPAGE",
        "https://mobile.example.com/post, example.com, WEBPAGE",
        "https://amp.example.com/post, example.com, WEBPAGE",
        "https://blog.example.com/post, blog.example.com, WEBPAGE",
        "https://example.com/report.pdf, example.com, PDF",
        "https://example.com/Report.PDF?download=1, example.com, PDF",
        "http://192.168.1.5/page, 192.168.1.5, WEBPAGE",
    )
    fun `before the page is read, a platform is just the host and the kind is webpage or pdf`(url: String, label: String, kind: ContentKind) {
        val d = PlatformDetector.detect(url)
        d.platform.label shouldBe label
        d.kind shouldBe kind
    }

    @Test
    fun `no site is special before its page is read`() {
        val a = PlatformDetector.detect("https://www.instagram.com/reel/Cx9bT2/")
        val b = PlatformDetector.detect("https://www.example.com/reel/Cx9bT2/")
        a.kind shouldBe b.kind
        a.creator shouldBe "instagram.com"
    }

    @Test
    fun `creator starts as the site address`() {
        PlatformDetector.detect("https://www.nytimes.com/a").creator shouldBe "nytimes.com"
        PlatformDetector.detect("https://www.nytimes.com/a").host shouldBe "nytimes.com"
    }

    @Test
    fun `a link with no usable host still gets a platform`() {
        PlatformDetector.detect("not a url").platform.label shouldBe "web"
    }
}
