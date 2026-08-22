package com.example.recme.domain.model

/**
 * Domain model representing an audio recording session.
 */
data class Recording(
    val id: String,
    val startTimeWallMs: Long,
    val endTimeWallMs: Long,
    val durationMs: Long,
    val sidecarPath: String,
    val audioPath: String,
    val isProcessed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Domain model representing a distinct VAD speech segment.
 */
data class SpeechSegment(
    val id: String,
    val recordingId: String,
    val startTimeWallMs: Long,
    val endTimeWallMs: Long,
    val durationMs: Long,
    val speakerId: String? = null,
    val gateDecision: String = "ALLOWED",
    val gateProfileId: String? = null,
    val gateConfidence: Float? = null,
    val gateReason: String? = null,
    val language: String? = null,
    val hasTranscript: Boolean = false,
    val transcriptText: String? = null,
    val transcriptPath: String? = null,
    val dailyNoteDate: String? = null,
    val topicLinks: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
