package com.example.recme.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SQL projection of an enrolled speaker profile with Voice Gate consent flags.
 * Detailed acoustic centroids remain in the JSON profiles directory.
 */
@Entity(tableName = "speaker_profiles")
data class SpeakerProfileEntity(
    @PrimaryKey val id: String,                    // e.g. "jan_jvr"
    val name: String,
    val relationship: String,
    val colorHex: String,

    // Voice Gate / Consent (MOD-02)
    val allowedToRecord: Boolean = false,
    val consentTimestamp: Long? = null,
    val consentNote: String? = null,
    val gateConfidenceOverride: Float? = null,
    val expiresAt: Long? = null,                   // Epoch timestamp for temporary guest expiration

    // Volume & Priority Stats
    val estimatedMinutes: Double = 0.0,
    val sampleCount: Int = 0,

    val lastUpdated: Long = System.currentTimeMillis()
)
