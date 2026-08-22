package com.example.recme.ai.speaker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an enrolled speaker profile with an acoustic voiceprint embedding centroid.
 */
@Serializable
data class SpeakerProfile(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("relationship")
    val relationship: String = "Contact",

    @SerialName("color_hex")
    val colorHex: String = "#3B82F6",

    @SerialName("aliases")
    val aliases: List<String> = emptyList(),

    @SerialName("sample_count")
    val sampleCount: Int = 1,

    @SerialName("centroid_embedding")
    val centroidEmbedding: List<Float>,

    @SerialName("language_centroids")
    val languageCentroids: Map<String, List<Float>> = emptyMap(),

    @SerialName("language_sample_counts")
    val languageSampleCounts: Map<String, Int> = emptyMap(),

    @SerialName("last_updated_epoch_ms")
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),

    @SerialName("allowed_to_record")
    val allowedToRecord: Boolean = false,

    @SerialName("consent_timestamp")
    val consentTimestamp: Long? = null,

    @SerialName("consent_note")
    val consentNote: String? = null,

    @SerialName("expires_at_epoch_ms")
    val expiresAtEpochMs: Long? = null,

    @SerialName("total_recorded_seconds")
    val totalRecordedSeconds: Double = 0.0,

    @SerialName("confidence_threshold_override")
    val confidenceThresholdOverride: Float? = null
)

/**
 * Result of a hybrid acoustic-context speaker identification evaluation.
 */
data class SpeakerIdentificationResult(
    val speaker: String?,
    val confidence: Float,
    val acousticScore: Float,
    val contextScore: Float,
    val isAutoAdapted: Boolean = false
)
