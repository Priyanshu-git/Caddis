package app.caddis.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
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
    }
}
