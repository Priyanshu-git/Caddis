package app.caddis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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

        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
