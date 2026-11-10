package app.caddis.domain.link

/**
 * MET-05: what to call a save when the page gave us no title: offline, blocked, or a challenge page.
 * Built from the link alone, with no network, and replaced by the real title the moment one arrives.
 */
object DefaultTitles {
    fun of(url: String): String =
        url.removePrefix("https://").removePrefix("http://").ifBlank { "Untitled" }
}
