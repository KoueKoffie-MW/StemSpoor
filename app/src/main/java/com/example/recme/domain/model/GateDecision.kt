package com.example.recme.domain.model

/**
 * Result of a Voice Gate evaluation on a speech segment.
 */
data class GateDecision(
    val allowed: Boolean,
    val matchedProfileId: String? = null,
    val confidence: Float? = null,
    val reason: String = "Normal",
    val durationMs: Long = 0L,
    val decisionType: String = if (allowed) "ALLOWED" else "DENIED_UNKNOWN"
)

/**
 * Summary statistics of Voice Gate filtering over a time window.
 */
data class FilterStats(
    val totalDecisions: Int,
    val allowedCount: Int,
    val deniedCount: Int,
    val keptPercentage: Float = if (totalDecisions > 0) (allowedCount.toFloat() / totalDecisions) * 100f else 100f
)
