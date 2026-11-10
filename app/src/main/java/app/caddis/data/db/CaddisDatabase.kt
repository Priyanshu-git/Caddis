package app.caddis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.caddis.domain.model.Platform

@Database(
    entities = [
        ItemEntity::class,
        ItemFts::class,
        IntentEntity::class,
        IntentRuleEntity::class,
        CollectionEntity::class,
        ItemCollectionRef::class,
        WeeklySummaryEntity::class,
        IntentCorrectionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CaddisDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun collections(): CollectionDao
    abstract fun intents(): IntentDao
    abstract fun weeks(): WeekDao

    companion object {
        const val NAME = "caddis.db"

        /** v2: the site icon shown when a save has no thumbnail. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN faviconUrl TEXT DEFAULT NULL")
            }
        }

        /**
         * v3: platform is no longer a closed enum; the column holds a lower-case name. Old enum names
         * become lower case and learned intent corrections keep their key under the new name. v4 then
         * replaces these with each site's own name, so this step never needs to know any site.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE items SET platform = lower(platform)")
                // "INSTAGRAM:REEL" -> "instagram:REEL", "WEB:WEBPAGE" -> "web:WEBPAGE".
                db.execSQL("UPDATE intent_corrections SET key = lower(substr(key, 1, instr(key, ':') - 1)) || substr(key, instr(key, ':')) WHERE instr(key, ':') > 0")
            }
        }

        /**
         * v4: no table changes. A platform is now the site's own name, read from its page. Every link gets
         * its host as a starting name and its details are marked "not fetched yet", so the normal scheduler
         * reads each page again and replaces the host with the real name (`chatgpt.com` → `ChatGPT`).
         * Fetched details never overwrite anything the person edited. Notes are untouched.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                class Row(val id: String, val url: String)
                val rows = db.query("SELECT id, url FROM items WHERE type = 'LINK' AND url IS NOT NULL").use { c ->
                    buildList { while (c.moveToNext()) add(Row(c.getString(0), c.getString(1))) }
                }
                for (r in rows) {
                    db.execSQL(
                        "UPDATE items SET platform = ?, detailsFetchedAt = NULL WHERE id = ?",
                        arrayOf<Any>(Platform.fromUrl(r.url).label, r.id),
                    )
                }
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
