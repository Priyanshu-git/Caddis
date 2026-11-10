package app.caddis.domain.details

import app.caddis.domain.link.Links
import app.caddis.domain.model.Item

/** The picture to show when a save has no thumbnail: the site's own icon. Notes have no site. */
object Favicons {
    fun forItem(item: Item): String? {
        if (item.isNote || item.platform.isNote) return null
        val url = item.url ?: return null
        return item.faviconUrl ?: rootIcon(url)
    }

    /** Most sites answer `/favicon.ico` even when the page never said so. */
    fun rootIcon(url: String): String? {
        val host = Links.host(url).takeIf { it.isNotBlank() } ?: return null
        return "https://$host/favicon.ico"
    }
}
