package com.nexxlabs.caddis.data

import com.nexxlabs.caddis.data.details.DetailsFetcher
import com.nexxlabs.caddis.data.details.FetchResult
import com.nexxlabs.caddis.data.details.UserAgentInterceptor
import com.nexxlabs.caddis.domain.model.ContentKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetailsFetcherTest {
    private val server = MockWebServer()
    private val fetcher = DetailsFetcher(OkHttpClient())

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun url() = server.url("/post").toString()

    @Test
    fun `reads page metadata and sends only the link`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("""<html><head><meta property="og:title" content="Rome day trips"><meta property="og:image" content="/t.jpg"></head></html>"""),
        )
        val r = fetcher.fetch(url())
        assertTrue(r is FetchResult.Ok)
        assertEquals("Rome day trips", (r as FetchResult.Ok).details.title)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals(0L, req.bodySize)
        assertTrue(req.getHeader("Cookie") == null)
    }

    @Test
    fun `server errors retry, client errors and non-html give up`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(FetchResult.Retry, fetcher.fetch(url()))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url()))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("x"))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url()))
    }

    @Test
    fun `instagram and facebook are fetched like any other site`() = runBlocking {
        val meta = DetailsFetcher(redirected())
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody("""<html><head><meta property="og:site_name" content="Instagram"><meta property="og:title" content="Oven Notes on Instagram"></head></html>"""),
        )
        val r = meta.fetch("https://www.instagram.com/reel/Cx9/") as FetchResult.Ok
        assertEquals("Instagram", r.details.siteName)
        assertEquals("Oven Notes on Instagram", r.details.title)
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(FetchResult.GiveUp, meta.fetch("https://www.facebook.com/reel/1"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the page names the site`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody("""<html><head><meta property="og:site_name" content="ChatGPT"><meta property="og:title" content="Pasta plan"></head></html>"""),
        )
        val d = (fetcher.fetch(url()) as FetchResult.Ok).details
        assertEquals("ChatGPT", d.siteName)
        assertEquals("Pasta plan", d.title)
    }

    @Test
    fun `a login wall that only says its own name still names the site`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody("""<html><head><title>Instagram</title><meta property="og:site_name" content="Instagram"></head></html>"""),
        )
        val d = (fetcher.fetch(url()) as FetchResult.Ok).details
        assertEquals("Instagram", d.siteName)
        assertEquals(null, d.title)
    }

    @Test
    fun `a pdf is recognised from the response without reading it`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF-1.7"))
        val d = (fetcher.fetch(url()) as FetchResult.Ok).details
        assertEquals(ContentKind.PDF, d.kind)
    }

    @Test
    fun `a bot-check page gives up so the save keeps its default title`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody("<html><head><title>Just a moment...</title></head><body>Checking your browser</body></html>"),
        )
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url()))
    }

    @Test
    fun `an unreachable host is a retry, never a crash`() = runBlocking {
        // Nothing listens on port 1: the connection is refused, which is "worth retrying later".
        assertEquals(FetchResult.Retry, fetcher.fetch("http://127.0.0.1:1/post"))
    }

    /** Sends every request to the mock server, whatever host the fetcher asked for, and records it. */
    private fun redirected(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val original = chain.request()
        val target = server.url(original.url.encodedPath + (original.url.encodedQuery?.let { "?$it" } ?: ""))
        chain.proceed(original.newBuilder().url(target).build())
    }.build()

    private val postUrl = "https://www.example.com/r/androiddev/comments/abc123/hello/"
    private fun withOEmbed(extraHead: String = "") =
        """<html><head><link rel="alternate" type="application/json+oembed" href="https://www.example.com/oembed?url=x">$extraHead</head></html>"""

    @Test
    fun `an oembed endpoint the page advertises is followed, and its title wins`() = runBlocking {
        val client = DetailsFetcher(redirected())
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html").setBody(
                withOEmbed("""<meta property="og:site_name" content="Example"><meta property="og:title" content="Other"><meta property="og:image" content="https://i.example.com/p.jpg">"""),
            ),
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"title":"Hello Compose","author_name":"u/redlentil","provider_name":"Elsewhere"}"""))
        val d = (client.fetch(postUrl) as FetchResult.Ok).details
        assertEquals("Hello Compose", d.title)
        assertEquals("u/redlentil", d.creator)
        assertEquals("https://i.example.com/p.jpg", d.thumbnailUrl)
        assertEquals("Example", d.siteName) // the site's own page names it, not the oEmbed provider
        assertEquals(null, d.oEmbedUrl)
        val page = server.takeRequest()
        assertTrue(page.getHeader("User-Agent")!!.contains("CaddisLinkPreview"))
        val oembed = server.takeRequest()
        assertEquals("/oembed", oembed.requestUrl?.encodedPath)
        assertTrue(oembed.getHeader("Authorization") == null && oembed.getHeader("Cookie") == null)
    }

    @Test
    fun `a page with no oembed link makes only one request`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("""<html><head><title>Plain</title></head></html>"""))
        fetcher.fetch(url())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the page still counts when the advertised oembed fails`() = runBlocking {
        val client = DetailsFetcher(redirected())
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(withOEmbed("""<meta property="og:title" content="From the page">""")))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals("From the page", (client.fetch(postUrl) as FetchResult.Ok).details.title)
    }

    @Test
    fun `blocked on both requests gives up or retries by status`() = runBlocking {
        val client = DetailsFetcher(redirected())
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(FetchResult.GiveUp, client.fetch(postUrl))
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(FetchResult.Retry, client.fetch(postUrl))
        // The page says nothing, and its oembed is only having a bad moment: try again later.
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(withOEmbed()))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(FetchResult.Retry, client.fetch(postUrl))
    }

    @Test
    fun `thumbnails identify Caddis but a request that names its own agent keeps it`() {
        val client = OkHttpClient.Builder().addInterceptor(UserAgentInterceptor()).build()
        server.enqueue(MockResponse().setBody("a"))
        server.enqueue(MockResponse().setBody("b"))
        client.newCall(okhttp3.Request.Builder().url(server.url("/img.jpg")).build()).execute().close()
        client.newCall(okhttp3.Request.Builder().url(server.url("/img.jpg")).header("User-Agent", "Custom/1").build()).execute().close()
        assertEquals(DetailsFetcher.USER_AGENT, server.takeRequest().getHeader("User-Agent"))
        assertEquals("Custom/1", server.takeRequest().getHeader("User-Agent"))
    }
}
