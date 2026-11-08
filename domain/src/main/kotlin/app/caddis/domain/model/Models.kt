package app.caddis.domain.model

import app.caddis.domain.link.DefaultTitles
import java.time.DayOfWeek
import java.time.Instant

/** Where a save came from. LIB-03 filters use [label]. */
enum class Platform(val label: String) {
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    REDDIT("Reddit"),
    YOUTUBE("YouTube"),
    MEDIUM("Medium"),
    X("X"),
    WEB("Web"),
    NOTE("Note");

    /** MET-01: the app never calls Meta for these. */
    val isMeta: Boolean get() = this == INSTAGRAM || this == FACEBOOK

    companion object {
        /** The platforms a person can filter by (notes are not a platform filter). */
        val filterable = listOf(INSTAGRAM, FACEBOOK, REDDIT, YOUTUBE, MEDIUM, X, WEB)
    }
}

enum class ContentKind(val label: String, val isVideo: Boolean = false) {
    REEL("Reel", isVideo = true),
    VIDEO("Video", isVideo = true),
    SHORT("Short", isVideo = true),
    POST("Post"),
    CHANNEL("Channel"),
    PROFILE("Profile"),
    SUBREDDIT("Subreddit"),
    ARTICLE("Article"),
    WEBPAGE("Webpage"),
    PDF("PDF"),
    NOTE("Note"),
}

enum class ItemType { LINK, NOTE }

/** PRD data model: replaces v1's trash flag. */
enum class ItemStatus { ACTIVE, DONE, DELETED }

enum class IntentSource { GUESSED, USER }

/** When an item may appear on Home (BRB-02/03). */
enum class ReturnRule { DEFAULT, TONIGHT, WEEKEND, NEXT_WEEK, DATE, NEVER }

enum class TimeOfDay(val label: String, val startHour: Int, val endHour: Int) {
    MORNING("Morning", 5, 12),
    AFTERNOON("Afternoon", 12, 17),
    EVENING("Evening", 17, 23);

    fun contains(hour: Int): Boolean = hour in startHour until endHour

    companion object {
        fun of(hour: Int): TimeOfDay = entries.firstOrNull { it.contains(hour) } ?: EVENING
    }
}

/** Fields the person can edit; automation never overwrites these once edited (MET-03, principle 5). */
enum class EditableField { TITLE, DESCRIPTION, CREATOR, THUMBNAIL, LENGTH }

data class Item(
    val id: String,
    val type: ItemType,
    val url: String?,
    val normalizedUrl: String?,
    val platform: Platform,
    val kind: ContentKind,
    val title: String = "",
    val description: String = "",
    val creator: String = "",
    val thumbnailUrl: String? = null,
    /** The site's own icon, read from the page; shown when there is no thumbnail. */
    val faviconUrl: String? = null,
    val lengthSeconds: Int? = null,
    val note: String = "",
    val intentId: String,
    val intentSource: IntentSource = IntentSource.GUESSED,
    val reason: String = "",
    val sourceApp: String = "",
    val sourceFrom: String = "",
    val status: ItemStatus = ItemStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val doneAt: Instant? = null,
    val deletedAt: Instant? = null,
    val returnRule: ReturnRule = ReturnRule.DEFAULT,
    val returnAt: Instant? = null,
    val lastSurfacedAt: Instant? = null,
    val surfacedCount: Int = 0,
    val lastOpenedAt: Instant? = null,
    val lastReviewedAt: Instant? = null,
    val detailsFetchedAt: Instant? = null,
    val userEdited: Set<EditableField> = emptySet(),
    val collectionIds: Set<String> = emptySet(),
    val revision: Long = 1,
) {
    val isNote: Boolean get() = type == ItemType.NOTE

    /** Display title: what we know, falling back to a readable default built from the link (MET-05). */
    val displayTitle: String
        get() = title.ifBlank { url?.let { DefaultTitles.of(platform, kind, it) } ?: "Untitled note" }

    /** Most recent moment the person touched this item; drives tidy-up (FIN-03). */
    val lastTouchedAt: Instant
        get() = listOfNotNull(createdAt, lastOpenedAt, lastReviewedAt).max()
}

data class Intent(
    val id: String,
    val name: String,
    val doneLabel: String,
    val isDefault: Boolean,
    val position: Int,
) {
    /** INT-05: Just keep cannot be deleted. */
    val isProtected: Boolean get() = id == Intents.KEEP
}

/** INT-04 return rule. [days] empty means "never returns". A null rule (Just keep) means stays put. */
data class IntentRule(
    val intentId: String,
    val days: Set<DayOfWeek>,
    val timeOfDay: TimeOfDay,
    val maxPerDay: Int = 1,
) {
    init {
        require(maxPerDay in 1..3) { "maxPerDay must be 1..3" }
    }
}

data class Collection(
    val id: String,
    val name: String,
    val createdAt: Instant,
)

/** WK-01: a stored snapshot per Monday–Sunday week. */
data class WeeklySummary(
    val weekStart: java.time.LocalDate,
    val savedCount: Int,
    val usedCount: Int,
    val deletedCount: Int,
)
