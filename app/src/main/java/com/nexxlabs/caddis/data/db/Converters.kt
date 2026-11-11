package com.nexxlabs.caddis.data.db

import androidx.room.TypeConverter
import com.nexxlabs.caddis.domain.model.EditableField
import com.nexxlabs.caddis.domain.model.Platform
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantToLong(i: Instant?): Long? = i?.toEpochMilli()

    @TypeConverter
    fun longToInstant(l: Long?): Instant? = l?.let(Instant::ofEpochMilli)

    /** Only the label is stored; it is the site's name, so there is nothing else to rebuild. */
    @TypeConverter
    fun platformToId(p: Platform): String = p.label

    @TypeConverter
    fun idToPlatform(id: String): Platform = Platform.fromStored(id)

    @TypeConverter
    fun fieldsToString(s: Set<EditableField>): String = s.joinToString(",") { it.name }

    @TypeConverter
    fun stringToFields(s: String): Set<EditableField> =
        s.split(',').filter { it.isNotBlank() }.mapNotNull { n -> EditableField.entries.firstOrNull { it.name == n } }.toSet()
}
