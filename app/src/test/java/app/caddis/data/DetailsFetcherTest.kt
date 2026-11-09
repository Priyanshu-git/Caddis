package app.caddis.data

import app.caddis.data.details.DetailsFetcher
import app.caddis.data.details.FetchResult
import app.caddis.data.details.UserAgentInterceptor
import app.caddis.domain.model.Platform
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
        val r = fetcher.fetch(url(), Platform.site("127.0.0.1"))
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
        assertEquals(FetchResult.Retry, fetcher.fetch(url(), Platform.site("127.0.0.1")))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.site("127.0.0.1")))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("x"))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.site("127.0.0.1")))
    }

    @Test
    fun `instagram and facebook are never requested`() = runBlocking {
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.INSTAGRAM))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.FACEBOOK))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a bot-check page gives up so the save keeps its default title`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody("<html><head><title>Just a moment...</title></head><body>Checking your browser</body></html>"),
        )
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.MEDIUM))
    }

    @Test
    fun `an unreachable host is a retry, never a crash`() = runBlocking {
        // Nothing listens on port 1: the connection is refused, which is "worth retrying later".
        assertEquals(FetchResult.Retry, fetcher.fetch("http://127.0.0.1:1/post", Platform.site("127.0.0.1")))
    }

    /** Sends every request to the mock server, whatever host the fetcher asked for, and records it. */
    private fun redirected(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val original = chain.request()
        val target = server.url(original.url.encodedPath + (original.url.encodedQuery?.let { "?$it" } ?: ""))
        chain.proceed(original.newBuilder().url(target).build())
    }.build()

    private val redditUrl = "https://www.reddit.com/r/androiddev/comments/abc123/hello/"

    @Test
    fun `reddit title comes from the public oembed even when the page is blocked`() = runBlocking {
        val reddit = DetailsFetcher(redirected())
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"title":"Hello Compose","author_name":"u/redlentil"}"""))
        server.enqueue(MockResponse().setResponseCode(403))
        val r = reddit.fetch(redditUrl, Platform.REDDIT)
        assertTrue(r is FetchResult.Ok)
        assertEquals("Hello Compose", (r as FetchResult.Ok).details.title)
        assertEquals("u/redlentil", r.details.creator)
        val oembed = server.takeRequest()
        assertEquals("/oembed", oembed.requestUrl?.encodedPath)
        assertTrue(oembed.getHeader("User-Agent")!!.contains("CaddisLinkPreview"))
        assertTrue(oembed.getHeader("Authorization") == null && oembed.getHeader("Cookie") == null)
    }

    @Test
    fun `reddit page adds the thumbnail to the oembed title`() = runBlocking {
        val reddit = DetailsFetcher(redirected())
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"title":"Hello Compose"}"""))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody("""<html><head><title>x</title><meta property="og:title" content="Other"><meta property="og:image" content="https://i.redd.it/p.jpg"></head></html>"""),
        )
        val d = (reddit.fetch(redditUrl, Platform.REDDIT) as FetchResult.Ok).details
        assertEquals("Hello Compose", d.title)
        assertEquals("https://i.redd.it/p.jpg", d.thumbnailUrl)
    }

    @Test
    fun `reddit blocked on both requests gives up or retries by status`() = runBlocking {
        val reddit = DetailsFetcher(redirected())
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(FetchResult.GiveUp, reddit.fetch(redditUrl, Platform.REDDIT))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(FetchResult.Retry, reddit.fetch(redditUrl, Platform.REDDIT))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(FetchResult.Retry, reddit.fetch(redditUrl, Platform.REDDIT))
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
