package com.example.recme.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Vault index entry for local semantic vector search and hybrid FTS retrieval (MOD-05).
 */
@Entity(
    tableName = "vault_index",
    indices = [
        Index("type"),
        Index("date"),
        Index("recordingId"),
        Index("segmentId")
    ]
)
data class VaultIndexEntity(
    @PrimaryKey val id: String,                    // Hash or segment UUID
    val type: String,                              // "segment", "daily_summary", "topic"
    val contentHash: String? = null,
    val textSnippet: String,
    val embedding: ByteArray? = null,              // 384-dimensional dense vector BLOB
    val date: String? = null,                      // "2026-08-22"
    val speakerIds: List<String> = emptyList(),
    val recordingId: String? = null,
    val segmentId: String? = null,
    val lastIndexed: Long = System.currentTimeMillis()
)
