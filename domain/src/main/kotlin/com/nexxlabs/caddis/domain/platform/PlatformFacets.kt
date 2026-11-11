package com.nexxlabs.caddis.domain.platform

import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.Platform

/** One platform that appears in the library, with how many saves it has. */
data class PlatformFacet(val platform: Platform, val count: Int)

/**
 * LIB-03: the platforms a person can filter by. Read from the saves themselves, so a platform shows up
 * when there is a link from it and disappears when the last one is gone. Notes have no platform to filter by.
 */
object PlatformFacets {
    /** Most saves first; ties by name, ignoring case. */
    fun of(items: List<Item>): List<PlatformFacet> =
        items.filter { !it.isNote && !it.platform.isNote }
            .groupingBy { it.platform }.eachCount()
            .map { (platform, count) -> PlatformFacet(platform, count) }
            .sortedWith(
                compareByDescending<PlatformFacet> { it.count }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.platform.label }
                    .thenBy { it.platform.id },
            )

    const val FOLD_AFTER = 6

    /**
     * The platforms to show before "More": the first [limit], plus the selected one if it would be
     * hidden. A fold that hides a single platform is not worth a button, so everything shows then.
     */
    fun visible(facets: List<PlatformFacet>, selected: Platform?, expanded: Boolean, limit: Int = FOLD_AFTER): List<PlatformFacet> {
        if (expanded || facets.size <= limit + 1) return facets
        val top = facets.take(limit)
        val pinned = selected?.let { s -> facets.firstOrNull { it.platform == s } }
        return if (pinned != null && pinned !in top) top + pinned else top
    }
}
