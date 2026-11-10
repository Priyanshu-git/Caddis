package app.caddis.domain.details

import app.caddis.domain.link.LinkCheck
import app.caddis.domain.link.PlatformDetector
import app.caddis.domain.model.EditableField
import app.caddis.domain.model.Item
import java.time.Instant

/** What the person typed on the Edit details sheet (MET-03). */
data class DetailEdits(
    val title: String,
    val description: String,
    val creator: String,
)

object EditLimits {
    const val TITLE = 200
    const val DESCRIPTION = 1000
    const val CREATOR = 80
}

/**
 * MET-03 / principle 5. A changed field becomes "yours" and automation never overwrites it. Clearing
 * a field hands it back: it is emptied and the next details fetch may fill it again. An unchanged
 * field keeps its current state, so opening and saving the sheet changes nothing.
 */
fun Item.withEdits(e: DetailEdits, now: Instant): Item {
    var edited = userEdited
    fun apply(field: EditableField, current: String, typed: String, max: Int): String {
        val v = typed.replace(Regex("\\s+"), " ").trim().take(max)
        if (v == current) return current
        edited = if (v.isEmpty()) edited - field else edited + field
        return v
    }
    val title = apply(EditableField.TITLE, title, e.title, EditLimits.TITLE)
    val description = apply(EditableField.DESCRIPTION, description, e.description, EditLimits.DESCRIPTION)
    val creator = apply(EditableField.CREATOR, creator, e.creator, EditLimits.CREATOR)
    if (title == this.title && description == this.description && creator == this.creator) return this
    return copy(
        title = title, description = description, creator = creator, userEdited = edited,
        updatedAt = now, revision = revision + 1,
    )
}

/**
 * MET-03: the person changed the link itself. Platform and kind are read again from the new URL.
 * If it now points somewhere else, everything fetched for the old link is dropped (fields the
 * person edited stay) and [Item.detailsFetchedAt] is cleared so details are fetched again. A
 * cosmetic change (same duplicate key, e.g. tracking parameters removed) keeps the details.
 */
fun Item.withLink(link: LinkCheck.Valid, now: Instant): Item {
    if (isNote || link.url == url) return this
    val d = PlatformDetector.detect(link.url)
    if (link.normalized == normalizedUrl) {
        return copy(url = link.url, updatedAt = now, revision = revision + 1)
    }
    fun <T> keep(field: EditableField, current: T, fresh: T): T = if (field in userEdited) current else fresh
    return copy(
        url = link.url,
        normalizedUrl = link.normalized,
        platform = d.platform,
        kind = d.kind,
        title = keep(EditableField.TITLE, title, ""),
        description = keep(EditableField.DESCRIPTION, description, ""),
        creator = keep(EditableField.CREATOR, creator, d.creator),
        thumbnailUrl = keep(EditableField.THUMBNAIL, thumbnailUrl, null),
        lengthSeconds = keep(EditableField.LENGTH, lengthSeconds, null),
        faviconUrl = null,
        detailsFetchedAt = null,
        updatedAt = now,
        revision = revision + 1,
    )
}

/** Whether this save still needs its page read. */
fun Item.needsDetails(): Boolean = !isNote && url != null && detailsFetchedAt == null
