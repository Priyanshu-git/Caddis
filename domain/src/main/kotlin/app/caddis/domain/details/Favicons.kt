package app.caddis.domain.details

import app.caddis.domain.link.Links
import app.caddis.domain.model.Item
import app.caddis.domain.platform.PlatformRegistry

/**
 * The picture to show when a save has no thumbnail: the site's own icon. Instagram and Facebook
 * never get one, because loading it would call Meta (MET-01); notes have no site.
 */
object Favicons {
    fun forItem(item: Item): String? {
        if (item.isNote || item.platform.neverContact || item.platform.isNote) return null
        val url = item.url ?: return null
        return item.faviconUrl?.takeUnless { isMetaHost(Links.host(it)) } ?: rootIcon(url)
    }

    /** Most sites answer `/favicon.ico` even when the page never said so. */
    fun rootIcon(url: String): String? {
        val host = Links.host(url).takeIf { it.isNotBlank() && !isMetaHost(it) } ?: return null
        return "https://$host/favicon.ico"
    }

    private fun isMetaHost(host: String): Boolean = PlatformRegistry.isNeverContactHost(host)
}
