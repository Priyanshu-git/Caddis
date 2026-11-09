package app.caddis.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE status != 'DELETED' ORDER BY createdAt DESC")
    fun observeLibrary(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE status = 'DELETED' ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE status = 'ACTIVE'")
    suspend fun active(): List<ItemEntity>

    /** CAP-06: the most recent live save with the same normalised URL. */
    @Query("SELECT * FROM items WHERE normalizedUrl = :normalized AND status != 'DELETED' ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByNormalizedUrl(normalized: String): ItemEntity?

    /** SRC-01/02/03: keyword search on the phone. [match] comes from SearchQuery.toFtsMatch. */
    @Query(
        """
        SELECT items.* FROM items
        JOIN items_fts ON items.rowid = items_fts.rowid
        WHERE items_fts MATCH :match AND items.status != 'DELETED'
        ORDER BY items.createdAt DESC
        """,
    )
    fun search(match: String): Flow<List<ItemEntity>>

    /** Links still waiting for details. Platforms that are never contacted are filtered by the caller. */
    @Query("SELECT * FROM items WHERE type = 'LINK' AND detailsFetchedAt IS NULL AND status != 'DELETED'")
    suspend fun needingDetails(): List<ItemEntity>

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM items WHERE status = 'DELETED' AND deletedAt < :cutoffMillis")
    suspend fun purgeDeletedBefore(cutoffMillis: Long): Int

    @Query("UPDATE items SET intentId = :to, updatedAt = :now, revision = revision + 1 WHERE intentId = :from")
    suspend fun reassignIntent(from: String, to: String, now: Long): Int

    @Query("SELECT COUNT(*) FROM items WHERE intentId = :intentId AND status != 'DELETED'")
    suspend fun countForIntent(intentId: String): Int

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY createdAt")
    suspend fun all(): List<CollectionEntity>

    @Query("SELECT * FROM item_collections")
    fun observeRefs(): Flow<List<ItemCollectionRef>>

    @Query("SELECT * FROM item_collections")
    suspend fun allRefs(): List<ItemCollectionRef>

    @Query("SELECT collectionId FROM item_collections WHERE itemId = :itemId")
    fun observeForItem(itemId: String): Flow<List<String>>

    @Query("SELECT * FROM collections WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CollectionEntity?

    @Upsert
    suspend fun upsert(c: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRef(ref: ItemCollectionRef)

    @Query("DELETE FROM item_collections WHERE itemId = :itemId AND collectionId = :collectionId")
    suspend fun removeRef(itemId: String, collectionId: String)

    @Query("DELETE FROM collections")
    suspend fun deleteAll()
}

@Dao
interface IntentDao {
    @Query("SELECT * FROM intents ORDER BY position")
    fun observeAll(): Flow<List<IntentEntity>>

    @Query("SELECT * FROM intents ORDER BY position")
    suspend fun all(): List<IntentEntity>

    @Query("SELECT * FROM intent_rules")
    fun observeRules(): Flow<List<IntentRuleEntity>>

    @Query("SELECT * FROM intent_rules")
    suspend fun rules(): List<IntentRuleEntity>

    @Query("SELECT COUNT(*) FROM intents")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(i: IntentEntity)

    @Upsert
    suspend fun upsertRule(r: IntentRuleEntity)

    @Query("DELETE FROM intents WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM intent_corrections")
    suspend fun corrections(): List<IntentCorrectionEntity>

    @Query("SELECT * FROM intent_corrections WHERE `key` = :key AND intentId = :intentId")
    suspend fun correction(key: String, intentId: String): IntentCorrectionEntity?

    @Upsert
    suspend fun upsertCorrection(c: IntentCorrectionEntity)

    @Query("DELETE FROM intent_corrections WHERE intentId = :intentId")
    suspend fun deleteCorrections(intentId: String)

    @Query("DELETE FROM intents")
    suspend fun deleteAll()

    @Query("DELETE FROM intent_corrections")
    suspend fun deleteAllCorrections()
}

@Dao
interface WeekDao {
    @Query("SELECT * FROM weekly_summaries WHERE weekStart = :weekStart")
    fun observe(weekStart: String): Flow<WeeklySummaryEntity?>

    @Query("SELECT * FROM weekly_summaries WHERE weekStart = :weekStart")
    suspend fun get(weekStart: String): WeeklySummaryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(w: WeeklySummaryEntity)

    @Query(
        """
        UPDATE weekly_summaries SET
          savedCount = MAX(0, savedCount + :saved),
          usedCount = MAX(0, usedCount + :used),
          deletedCount = MAX(0, deletedCount + :deleted)
        WHERE weekStart = :weekStart
        """,
    )
    suspend fun add(weekStart: String, saved: Int, used: Int, deleted: Int)

    @Query("DELETE FROM weekly_summaries")
    suspend fun deleteAll()
}
