package com.example.recme.ai.voicegate

import android.util.Log
import com.example.recme.ai.speaker.SpeakerEmbeddingEngine
import com.example.recme.ai.speaker.SpeakerProfile
import com.example.recme.ai.speaker.SpeakerProfileManager
import com.example.recme.domain.model.GateDecision
import com.example.recme.domain.repository.GateAuditRepository
import com.example.recme.domain.repository.SpeakerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Post-VAD speaker verification engine ensuring compliance with § 201 StGB and privacy laws.
 * Evaluates speech PCM windows against explicitly authorized speaker profiles.
 */
class VoiceGateEvaluator(
    private val profileManager: SpeakerProfileManager,
    private val speakerRepository: SpeakerRepository,
    private val embeddingEngine: SpeakerEmbeddingEngine,
    private val auditRepository: GateAuditRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Evaluates a PCM sample window (16kHz float array in range [-1.0, 1.0]) against allowed speakers.
     */
    suspend fun evaluateSpeechWindow(
        samples: FloatArray,
        spokenLanguage: String? = null
    ): GateDecision = withContext(Dispatchers.Default) {
        if (!profileManager.isVoiceGateEnabled) {
            return@withContext GateDecision(
                allowed = true,
                matchedProfileId = null,
                confidence = 1.0f,
                reason = "Voice Gate Disabled",
                decisionType = "BYPASS"
            )
        }

        if (samples.isEmpty() || samples.size < 1600) { // Less than 100ms
            return@withContext GateDecision(
                allowed = false,
                matchedProfileId = null,
                confidence = 0.0f,
                reason = "Insufficient audio window for verification",
                decisionType = "DENIED_TOO_SHORT"
            )
        }

        // Get authorized profiles ordered by priority (most frequent speaker first)
        val allowedProfiles = try {
            val fromRepo = speakerRepository.getAllowedProfilesSortedByPriority()
            if (fromRepo.isNotEmpty()) {
                val allProfiles = profileManager.getProfiles()
                fromRepo.mapNotNull { domain -> allProfiles.find { it.id == domain.id } }
            } else {
                profileManager.getAllowedProfiles()
            }
        } catch (_: Exception) {
            profileManager.getAllowedProfiles()
        }

        if (allowedProfiles.isEmpty()) {
            Log.w(TAG, "Voice Gate active but no speaker profiles have 'allowedToRecord = true'")
            return@withContext GateDecision(
                allowed = false,
                matchedProfileId = null,
                confidence = 0.0f,
                reason = "No authorized speaker profiles enrolled",
                decisionType = "DENIED_NO_ALLOWED_PROFILES"
            )
        }

        // Extract 192-d speaker embedding
        val embedding = embeddingEngine.extractEmbedding(samples)
        val globalThreshold = profileManager.voiceGateConfidenceThreshold

        // Score against allowed profiles
        var bestProfile: SpeakerProfile? = null
        var bestScore = -1.0f
        var effectiveThreshold = globalThreshold
        val langKey = spokenLanguage?.lowercase()?.trim()?.takeIf { it.isNotBlank() }

        for (profile in allowedProfiles) {
            val globalCentroid = profile.centroidEmbedding.toFloatArray()
            val simGlobal = profileManager.computeCosineSimilarity(embedding, globalCentroid)

            val langCentroid = langKey?.let { profile.languageCentroids[it]?.toFloatArray() }
            val simLang = if (langCentroid != null) {
                profileManager.computeCosineSimilarity(embedding, langCentroid)
            } else null

            val profileScore = when {
                simLang != null -> maxOf(simGlobal, simLang * 1.05f)
                else -> simGlobal
            }

            if (profileScore > bestScore) {
                bestScore = profileScore
                bestProfile = profile
                effectiveThreshold = profile.confidenceThresholdOverride ?: globalThreshold
            }
        }

        val isAllowed = bestScore >= effectiveThreshold && bestProfile != null
        val decision = if (isAllowed) {
            GateDecision(
                allowed = true,
                matchedProfileId = bestProfile?.id,
                confidence = bestScore,
                reason = "Matched authorized speaker '${bestProfile?.name}' (${String.format("%.2f", bestScore)} >= ${String.format("%.2f", effectiveThreshold)})",
                decisionType = "ALLOWED"
            )
        } else {
            val matchName = bestProfile?.name ?: "Unknown"
            GateDecision(
                allowed = false,
                matchedProfileId = bestProfile?.id,
                confidence = bestScore,
                reason = "Unverified speaker / confidence ${String.format("%.2f", bestScore)} < ${String.format("%.2f", effectiveThreshold)} (best candidate: $matchName)",
                decisionType = "DENIED_LOW_CONFIDENCE"
            )
        }

        Log.d(TAG, "VoiceGate evaluation: ${decision.decisionType} | ${decision.reason}")
        return@withContext decision
    }

    /**
     * Asynchronously records an audit event for legal verification and statistics.
     */
    fun recordAuditAsync(
        recordingId: String?,
        segmentIndex: Int,
        durationMs: Long,
        decision: GateDecision
    ) {
        scope.launch {
            try {
                auditRepository.logDecision(decision.copy(durationMs = durationMs))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist gate audit record", e)
            }
        }
    }

    companion object {
        private const val TAG = "VoiceGateEvaluator"
    }
}
