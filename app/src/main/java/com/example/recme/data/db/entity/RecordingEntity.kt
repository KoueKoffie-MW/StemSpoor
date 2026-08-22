package com.example.recme.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQL projection of an audio recording session.
 * Single source of truth remains the companion JSON sidecar and WAV/Opus file.
 */
@Entity(
    tableName = "recordings",
    indices = [
        Index("startTimeWallMs"),
        Index("isProcessed")
    ]
)
data class RecordingEntity(
    @PrimaryKey val id: String,                    // e.g. "2026-08-22_143022"
    val startTimeWallMs: Long,
    val endTimeWallMs: Long,
    val durationMs: Long,
    val sidecarPath: String,                       // Path to sidecar JSON
    val audioPath: String,                         // Path to condensed audio
    val isProcessed: Boolean = false,              // Transcription complete?
    val createdAt: Long = System.currentTimeMillis()
)
