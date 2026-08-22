package com.example.recme.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Audit log entity recording every Voice Gate decision for compliance statistics.
 */
@Entity(
    tableName = "gate_audit",
    indices = [
        Index("timestamp"),
        Index("decision"),
        Index("profileId")
    ]
)
data class GateAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val decision: String,                          // "ALLOWED", "DENIED_UNKNOWN", "DENIED_LOW_CONF", "TEMP_ALLOWED"
    val profileId: String? = null,
    val confidence: Float? = null,
    val reason: String? = null,
    val durationMs: Long = 0L
)
