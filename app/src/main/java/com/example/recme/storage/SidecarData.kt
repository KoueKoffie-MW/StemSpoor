package com.example.recme.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Speech segment timing metadata conforming to ADR-0001.
 */
@Serializable
data class SpeechSegmentData(
    @SerialName("segment_index")
    val segmentIndex: Int,

    @SerialName("audio_start_ms")
    val audioStartMs: Long,

    @SerialName("audio_end_ms")
    val audioEndMs: Long,

    @SerialName("speech_start_epoch_ms")
    val speechStartEpochMs: Long,

    @SerialName("speech_end_epoch_ms")
    val speechEndEpochMs: Long,

    @SerialName("pre_roll_ms")
    val preRollMs: Long,

    @SerialName("post_roll_ms")
    val postRollMs: Long,

    @SerialName("detected_language")
    val detectedLanguage: String? = null,

    @SerialName("raw_text")
    val rawText: String? = null,

    @SerialName("polished_text")
    val polishedText: String? = null,

    @SerialName("speaker")
    val speaker: String? = null,

    @SerialName("speaker_confidence")
    val speakerConfidence: Float? = null
)

/**
 * Top-level sidecar JSON document mapping condensed WAV playhead to real-world timestamps.
 */
@Serializable
data class SidecarData(
    @SerialName("version")
    val version: Int = 1,

    @SerialName("file_name")
    val fileName: String,

    @SerialName("sample_rate_hz")
    val sampleRateHz: Int = 16000,

    @SerialName("channels")
    val channels: Int = 1,

    @SerialName("bit_depth")
    val bitDepth: Int = 16,

    @SerialName("recording_session_id")
    val recordingSessionId: String,

    @SerialName("started_at_epoch_ms")
    val startedAtEpochMs: Long,

    @SerialName("drive_file_id")
    val driveFileId: String? = null,

    @SerialName("drive_json_file_id")
    val driveJsonFileId: String? = null,

    @SerialName("drive_sync_epoch_ms")
    val driveSyncEpochMs: Long? = null,

    @SerialName("is_transcribed")
    val isTranscribed: Boolean = false,

    @SerialName("languages_detected")
    val languagesDetected: List<String> = emptyList(),

    @SerialName("segments")
    val segments: List<SpeechSegmentData> = emptyList()
)
