package com.example.recme.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SQL projection of an individual speech segment detected by Silero VAD.
 * Includes Voice Gate fields (MOD-02) for legal audit and selective recording.
 */
@Entity(
    tableName = "speech_segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("recordingId"),
        Index("startTimeWallMs"),
        Index("speakerId"),
        Index("gateDecision")
    ]
)
data class SpeechSegmentEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val startTimeWallMs: Long,
    val endTimeWallMs: Long,
    val durationMs: Long,

    // Speaker / Voice Gate (MOD-02 & MOD-03)
    val speakerId: String? = null,
    val gateDecision: String = "ALLOWED",          // "ALLOWED", "DENIED_UNKNOWN", "DENIED_LOW_CONF", "TEMP_ALLOWED"
    val gateProfileId: String? = null,
    val gateConfidence: Float? = null,
    val gateReason: String? = null,

    // Content & Transcript
    val language: String? = null,
    val hasTranscript: Boolean = false,
    val transcriptText: String? = null,
    val transcriptPath: String? = null,

    // Vault Linkage
    val dailyNoteDate: String? = null,             // e.g. "2026-08-22"
    val topicLinks: List<String> = emptyList(),

    val createdAt: Long = System.currentTimeMillis()
)
