package app.caddis.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.EditableField
import app.caddis.domain.model.IntentSource
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import app.caddis.domain.model.ReturnRule
import app.caddis.domain.model.TimeOfDay
import java.time.Instant

/**
 * One save or note. Every record carries [revision] and [updatedAt] so the later sync phase
 * (outbox + revision-aware pull, tech arch §2) can be added without a data migration of meaning.
 */
@Entity(
    tableName = "items",
    indices = [Index("normalizedUrl"), Index("status"), Index("createdAt")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val type: ItemType,
    val url: String?,
    val normalizedUrl: String?,
    val platform: Platform,
    val kind: ContentKind,
    val title: String,
    val description: String,
    val creator: String,
    val thumbnailUrl: String?,
    /** Added in version 2. */
    @ColumnInfo(defaultValue = "NULL") val faviconUrl: String? = null,
    val lengthSeconds: Int?,
    val note: String,
    val intentId: String,
    val intentSource: IntentSource,
    val reason: String,
    val sourceApp: String,
    val sourceFrom: String,
    val status: ItemStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val doneAt: Instant?,
    val deletedAt: Instant?,
    val returnRule: ReturnRule,
    val returnAt: Instant?,
    val lastSurfacedAt: Instant?,
    val surfacedCount: Int,
    val lastOpenedAt: Instant?,
    val lastReviewedAt: Instant?,
    val detailsFetchedAt: Instant?,
    val userEdited: Set<EditableField>,
    val revision: Long,
)

/** SRC-01: full-text index over every searchable field. Room keeps it in sync with triggers. */
@Fts4(contentEntity = ItemEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "items_fts")
data class ItemFts(
    val title: String,
    val description: String,
    val creator: String,
    val url: String?,
    val note: String,
    val reason: String,
    val sourceApp: String,
    val sourceFrom: String,
)

@Entity(tableName = "intents")
data class IntentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val doneLabel: String,
    val isDefault: Boolean,
    val position: Int,
    val updatedAt: Instant,
    val revision: Long,
)

/** INT-04: [days] is a bitmask, Monday = bit 0. */
@Entity(
    tableName = "intent_rules",
    foreignKeys = [ForeignKey(entity = IntentEntity::class, parentColumns = ["id"], childColumns = ["intentId"], onDelete = ForeignKey.CASCADE)],
)
data class IntentRuleEntity(
    @PrimaryKey val intentId: String,
    val days: Int,
    val timeOfDay: TimeOfDay,
    val maxPerDay: Int,
)

@Entity(tableName = "collections", indices = [Index(value = ["name"], unique = true)])
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
)

/** LIB-02: an item can be in many collections; deleting a collection keeps its items. */
@Entity(
    tableName = "item_collections",
    primaryKeys = ["itemId", "collectionId"],
    indices = [Index("collectionId")],
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ItemCollectionRef(
    val itemId: String,
    val collectionId: String,
)

/** WK-01: stored snapshot so a past week reads the same when reopened. [weekStart] is ISO yyyy-MM-dd (a Monday). */
@Entity(tableName = "weekly_summaries")
data class WeeklySummaryEntity(
    @PrimaryKey val weekStart: String,
    val savedCount: Int,
    val usedCount: Int,
    val deletedCount: Int,
)

/** Local count of intent corrections per platform:kind; shifts future guesses (tech arch §2). */
@Entity(tableName = "intent_corrections", primaryKeys = ["key", "intentId"])
data class IntentCorrectionEntity(
    val key: String,
    val intentId: String,
    val count: Int,
)
