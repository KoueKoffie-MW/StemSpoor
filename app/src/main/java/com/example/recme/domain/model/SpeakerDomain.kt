package com.example.recme.domain.model

/**
 * Domain model representing a speaker profile with Voice Gate consent parameters.
 */
data class SpeakerDomain(
    val id: String,
    val name: String,
    val relationship: String = "Contact",
    val colorHex: String = "#3B82F6",
    val allowedToRecord: Boolean = false,
    val consentTimestamp: Long? = null,
    val consentNote: String? = null,
    val gateConfidenceOverride: Float? = null,
    val expiresAt: Long? = null,
    val estimatedMinutes: Double = 0.0,
    val sampleCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
