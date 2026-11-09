package app.caddis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.caddis.domain.link.PlatformDetector

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
    version = 3,
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
         * v3: platform is no longer a closed enum; the column holds a lower-case platform id.
         * Old names become ids, old `WEB` rows are read again from their link (so a Drive link becomes
         * Google Drive and an ordinary site becomes its own platform), and learned intent corrections
         * keep their key under the new id. Nothing the person wrote is touched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                class Row(val id: String, val url: String?, val platform: String, val kind: String)
                val rows = db.query("SELECT id, url, platform, kind FROM items").use { c ->
                    buildList {
                        while (c.moveToNext()) add(Row(c.getString(0), if (c.isNull(1)) null else c.getString(1), c.getString(2), c.getString(3)))
                    }
                }
                for (r in rows) {
                    val (platform, kind) = if (r.platform == "WEB") {
                        PlatformDetector.detect(r.url.orEmpty()).let { it.platform.id to it.kind.name }
                    } else {
                        r.platform.lowercase() to r.kind
                    }
                    db.execSQL("UPDATE items SET platform = ?, kind = ? WHERE id = ?", arrayOf<Any>(platform, kind, r.id))
                }
                // "INSTAGRAM:REEL" -> "instagram:REEL", "WEB:WEBPAGE" -> "web:WEBPAGE".
                db.execSQL("UPDATE intent_corrections SET key = lower(substr(key, 1, instr(key, ':') - 1)) || substr(key, instr(key, ':')) WHERE instr(key, ':') > 0")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
