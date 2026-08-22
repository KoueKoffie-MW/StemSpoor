package com.example.recme.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key-value configuration entity for dynamic settings (e.g. segment merge gap, model choices).
 */
@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val key: String,
    val stringValue: String? = null,
    val longValue: Long? = null,
    val doubleValue: Double? = null,
    val booleanValue: Boolean? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
