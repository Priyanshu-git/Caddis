package app.caddis.data

import app.caddis.data.details.DetailsFetcher
import app.caddis.data.details.FetchResult
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
        val r = fetcher.fetch(url(), Platform.WEB)
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
        assertEquals(FetchResult.Retry, fetcher.fetch(url(), Platform.WEB))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.WEB))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("x"))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.WEB))
    }

    @Test
    fun `instagram and facebook are never requested`() = runBlocking {
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.INSTAGRAM))
        assertEquals(FetchResult.GiveUp, fetcher.fetch(url(), Platform.FACEBOOK))
        assertEquals(0, server.requestCount)
    }
}
