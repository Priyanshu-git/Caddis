package app.caddis.data.details

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Identifies Caddis on every request that does not set its own User-Agent. Thumbnails load through
 * Coil on the shared client; without this they went out as a bare `okhttp/x`, which hosts such as
 * Wikimedia refuse, so those saves showed no picture.
 */
class UserAgentInterceptor(private val userAgent: String = DetailsFetcher.USER_AGENT) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("User-Agent") != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
    }
}
