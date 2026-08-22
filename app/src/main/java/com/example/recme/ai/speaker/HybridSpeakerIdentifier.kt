package com.example.recme.ai.speaker

import android.content.Context
import android.util.Log
import com.example.recme.ai.config.PromptConfigManager

/**
 * Combines acoustic voiceprint embeddings with semantic & vault context clues to positively identify speakers,
 * and triggers continuous online voiceprint adaptation.
 */
class HybridSpeakerIdentifier(private val context: Context) {

    private val profileManager = SpeakerProfileManager(context)
    private val promptConfig = PromptConfigManager(context)
    private val embeddingEngine = SpeakerEmbeddingEngine(context)

    /**
     * Identifies the speaker of a speech segment using hybrid acoustic + contextual fusion.
     *
     * @param pcmSamples 16kHz mono audio float samples [-1.0, 1.0]
     * @param segmentText Spoken transcript text (for semantic context reasoning)
     * @param allSegments Contextual neighboring segments in the recording
     */
    /**
     * Identifies the speaker of a speech segment using hybrid acoustic + contextual fusion.
     *
     * @param pcmSamples 16kHz mono audio float samples [-1.0, 1.0]
     * @param segmentText Spoken transcript text (for semantic context reasoning)
     * @param spokenLanguage Detected or candidate language for language-aware acoustic matching (e.g. "af", "de", "en")
     * @param allSegments Contextual neighboring segments in the recording
     */
    suspend fun identifySpeaker(
        pcmSamples: FloatArray,
        segmentText: String?,
        spokenLanguage: String? = null,
        allSegments: List<String> = emptyList()
    ): SpeakerIdentificationResult {
        if (!profileManager.isSpeakerRecognitionEnabled) {
            return SpeakerIdentificationResult(null, 0.0f, 0.0f, 0.0f)
        }

        val profiles = profileManager.getProfiles()
        if (profiles.isEmpty()) {
            return SpeakerIdentificationResult(null, 0.0f, 0.0f, 0.0f)
        }

        // 1. Acoustic Vector Extraction & Language-Aware Matching
        val embedding = embeddingEngine.extractEmbedding(pcmSamples)
        val acousticMatches = profileManager.matchEmbedding(embedding, profiles, spokenLanguage)
        val bestAcoustic = acousticMatches.firstOrNull()

        val rawAcousticScore = bestAcoustic?.second ?: 0.0f

        // 2. Semantic Context Reasoning
        val text = segmentText.orEmpty()
        var bestContextProfile: SpeakerProfile? = null
        var contextScore = 0.0f

        for (profile in profiles) {
            val score = evaluateContextClues(text, profile, promptConfig.userName)
            if (score > contextScore) {
                contextScore = score
                bestContextProfile = profile
            }
        }

        val primaryUser = profiles.firstOrNull {
            it.name.equals(promptConfig.userName, ignoreCase = true) ||
            it.relationship.equals("Self", ignoreCase = true)
        } ?: profiles.firstOrNull()

        // 3. Dynamic Candidate Selection & Confidence Estimation
        // If acoustic score is solid (>= 0.60), prioritize acoustic candidate.
        // Otherwise, if context is clear (>= 0.30), trust context candidate.
        // Default to primary user on own device when no counter-evidence exists.
        val candidate = when {
            rawAcousticScore >= 0.60f && bestAcoustic != null -> bestAcoustic.first
            bestContextProfile != null && contextScore >= 0.30f -> bestContextProfile
            bestAcoustic != null && rawAcousticScore >= 0.45f -> bestAcoustic.first
            else -> primaryUser
        }

        if (candidate == null) {
            return SpeakerIdentificationResult(null, 0.0f, 0.0f, 0.0f)
        }

        val isCandidatePrimary = candidate.id == primaryUser?.id
        val candidateAcoustic = if (bestAcoustic?.first?.id == candidate.id) rawAcousticScore else 0.3f
        val candidateContext = if (bestContextProfile?.id == candidate.id) contextScore else if (isCandidatePrimary) 0.5f else 0.1f

        // If candidate profile is uncalibrated (few samples), weight towards context and primary user bootstrap
        val isCalibrated = candidate.sampleCount >= 3
        val (wAcoustic, wContext) = if (isCalibrated) Pair(0.60f, 0.40f) else Pair(0.25f, 0.75f)

        val jointConfidence = (wAcoustic * candidateAcoustic + wContext * candidateContext).coerceIn(0.0f, 1.0f)
        val threshold = profileManager.recognitionThreshold.coerceAtMost(0.50f)

        val isIdentified = (jointConfidence >= threshold) || (rawAcousticScore >= 0.60f) || (isCandidatePrimary && candidateContext >= 0.35f)
        val finalSpeaker = if (isIdentified) candidate.name else null

        // 4. Continuous Online Learning / Adaptive Multi-Centroid Update
        var autoAdapted = false
        if (isIdentified && jointConfidence >= 0.55f && pcmSamples.size >= 16000) { // at least 1s of speech
            val adaptAlpha = if (isCalibrated) 0.12f else 0.30f
            profileManager.adaptProfileCentroid(candidate.name, embedding, spokenLanguage, alpha = adaptAlpha)
            autoAdapted = true
            Log.i(TAG, "Continuous learning adapted voiceprint for '${candidate.name}' (Lang: $spokenLanguage, Total samples: ${candidate.sampleCount + 1}, Confidence: ${(jointConfidence * 100).toInt()}%)")
        }

        return SpeakerIdentificationResult(
            speaker = finalSpeaker,
            confidence = jointConfidence,
            acousticScore = rawAcousticScore,
            contextScore = contextScore,
            isAutoAdapted = autoAdapted
        )
    }

    /**
     * Evaluates semantic, linguistic, and relationship clues in the spoken text.
     */
    private fun evaluateContextClues(text: String, profile: SpeakerProfile, primaryUserName: String): Float {
        if (text.isBlank()) return 0.0f
        val lower = text.lowercase()
        val name = profile.name.lowercase()
        val rel = profile.relationship.lowercase()

        var score = 0.0f

        // Is this the primary user (e.g. Jan)?
        val isPrimaryUser = profile.name.equals(primaryUserName, ignoreCase = true) || rel.equals("self", ignoreCase = true)

        if (isPrimaryUser) {
            // First-person conversational markers across AF / EN / DE
            if (lower.contains("ek ") || lower.contains("ek en") || lower.contains("ons ") || lower.contains("ons gaan") ||
                lower.contains("shall we") || lower.contains("i am") || lower.contains("i'm ") || lower.contains("my ") ||
                lower.contains("wir ") || lower.contains("ich ")) {
                score += 0.40f
            }
            if (lower.contains("simscape") || lower.contains("multibody") || lower.contains("matlab") || lower.contains("testing")) {
                score += 0.30f
            }
        }

        // Direct vocatives / Address to others: "Angelique", "Boetie", "Ansunet", or aliases
        val hasNameMention = lower.contains(name) ||
                             profile.aliases.any { lower.contains(it.lowercase()) } ||
                             (name == "johan-henry" && lower.contains("boetie"))

        if (hasNameMention) {
            // Vocative mention indicates the speaker is likely addressing the target
            if (isPrimaryUser) {
                // Primary user addressing wife/kids
                score += 0.35f
            } else {
                // Not the speaker, but addressing them -> reduce confidence for target
                return 0.05f
            }
        }

        // Specific family nicknames / speech patterns from children
        if (name == "johan-henry" || name == "ansunet" || rel.contains("son") || rel.contains("daughter") || rel.contains("kind")) {
            if (lower.contains("pappa") || lower.contains("mamma") || lower.contains("speel")) {
                score += 0.45f
            }
        }

        return score.coerceIn(0.0f, 1.0f)
    }

    fun close() {
        embeddingEngine.close()
    }

    companion object {
        private const val TAG = "HybridSpeakerIdentifier"
    }
}
