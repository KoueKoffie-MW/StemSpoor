package com.example.recme.data.db.converters

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverter for serializing and deserializing List<String> fields.
 */
class StringListConverter {

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.let { Json.encodeToString(it) } ?: "[]"
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
