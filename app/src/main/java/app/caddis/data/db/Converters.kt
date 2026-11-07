package app.caddis.data.db

import androidx.room.TypeConverter
import app.caddis.domain.model.EditableField
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantToLong(i: Instant?): Long? = i?.toEpochMilli()

    @TypeConverter
    fun longToInstant(l: Long?): Instant? = l?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fieldsToString(s: Set<EditableField>): String = s.joinToString(",") { it.name }

    @TypeConverter
    fun stringToFields(s: String): Set<EditableField> =
        s.split(',').filter { it.isNotBlank() }.mapNotNull { n -> EditableField.entries.firstOrNull { it.name == n } }.toSet()
}
