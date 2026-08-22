package com.example.recme.ai.speaker

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import com.example.recme.service.VadRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.sqrt

/**
 * Manages storage, cosine matching, and continuous online adaptation of speaker voiceprints.
 */
class SpeakerProfileManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)

    private val mutex = Mutex()

    private val voiceprintsDir: File by lazy {
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(documents, "RecMe/voiceprints")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val profilesFile: File
        get() = File(voiceprintsDir, "speaker_profiles.json")

    var recognitionThreshold: Float
        get() = prefs.getFloat(KEY_SPEAKER_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) = prefs.edit().putFloat(KEY_SPEAKER_THRESHOLD, value).apply()

    var isSpeakerRecognitionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPEAKER_RECOGNITION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEAKER_RECOGNITION_ENABLED, value).apply()

    var isContinuousLearningEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS_LEARNING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS_LEARNING_ENABLED, value).apply()

    var isVoiceGateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_GATE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_GATE_ENABLED, value).apply()

    var voiceGateConfidenceThreshold: Float
        get() = prefs.getFloat(KEY_VOICE_GATE_THRESHOLD, DEFAULT_VOICE_GATE_THRESHOLD)
        set(value) = prefs.edit().putFloat(KEY_VOICE_GATE_THRESHOLD, value).apply()

    /**
     * Loads all enrolled speaker profiles.
     */
    suspend fun getProfiles(): List<SpeakerProfile> = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadProfilesInternal()
        }
    }

    /**
     * Synchronously loads speaker profiles without requiring a coroutine context.
     */
    fun getProfilesBlocking(): List<SpeakerProfile> {
        return try {
            loadProfilesInternal()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Enrolls or updates a speaker profile with a new embedding and optional language tag.
     */
    suspend fun enrollOrUpdateProfile(
        name: String,
        relationship: String = "Contact",
        colorHex: String = "#3B82F6",
        newEmbedding: FloatArray,
        spokenLanguage: String? = null,
        aliases: List<String> = emptyList()
    ): SpeakerProfile = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalized = normalize(newEmbedding)
            val currentProfiles = loadProfilesInternal().toMutableList()
            val existingIndex = currentProfiles.indexOfFirst { it.name.equals(name.trim(), ignoreCase = true) }
            val langKey = spokenLanguage?.lowercase()?.trim()?.takeIf { it.isNotBlank() }

            val updated = if (existingIndex >= 0) {
                val existing = currentProfiles[existingIndex]
                val updatedCentroid = updateCentroidEma(existing.centroidEmbedding.toFloatArray(), normalized, alpha = 0.25f)

                val updatedLangCentroids = existing.languageCentroids.toMutableMap()
                val updatedLangCounts = existing.languageSampleCounts.toMutableMap()

                if (langKey != null) {
                    val existingLangCentroid = updatedLangCentroids[langKey]?.toFloatArray()
                    val newLangCentroid = if (existingLangCentroid != null) {
                        updateCentroidEma(existingLangCentroid, normalized, alpha = 0.30f)
                    } else {
                        normalized
                    }
                    updatedLangCentroids[langKey] = newLangCentroid.toList()
                    updatedLangCounts[langKey] = (updatedLangCounts[langKey] ?: 0) + 1
                }

                val mergedAliases = (existing.aliases + aliases).distinct()

                existing.copy(
                    relationship = relationship,
                    colorHex = colorHex,
                    aliases = mergedAliases,
                    sampleCount = existing.sampleCount + 1,
                    centroidEmbedding = updatedCentroid.toList(),
                    languageCentroids = updatedLangCentroids,
                    languageSampleCounts = updatedLangCounts,
                    lastUpdatedEpochMs = System.currentTimeMillis()
                )
            } else {
                val langCentroids = if (langKey != null) mapOf(langKey to normalized.toList()) else emptyMap()
                val langCounts = if (langKey != null) mapOf(langKey to 1) else emptyMap()

                SpeakerProfile(
                    id = name.lowercase().replace(Regex("[^a-z0-9]"), "_"),
                    name = name.trim(),
                    relationship = relationship,
                    colorHex = colorHex,
                    aliases = aliases.distinct(),
                    sampleCount = 1,
                    centroidEmbedding = normalized.toList(),
                    languageCentroids = langCentroids,
                    languageSampleCounts = langCounts,
                    lastUpdatedEpochMs = System.currentTimeMillis()
                )
            }

            if (existingIndex >= 0) {
                currentProfiles[existingIndex] = updated
            } else {
                currentProfiles.add(updated)
            }

            saveProfilesInternal(currentProfiles)
            Log.i(TAG, "Enrolled/Updated voiceprint for '${updated.name}' (Samples: ${updated.sampleCount}, Langs: ${updated.languageCentroids.keys.joinToString()})")
            updated
        }
    }

    /**
     * Deletes a speaker profile.
     */
    suspend fun deleteProfile(speakerId: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val profiles = loadProfilesInternal().toMutableList()
            val removed = profiles.removeAll { it.id == speakerId }
            if (removed) {
                saveProfilesInternal(profiles)
                Log.i(TAG, "Deleted speaker profile ID: $speakerId")
            }
            removed
        }
    }

    /**
     * Matches a sample embedding against all enrolled profiles using Cosine Similarity.
     * Incorporates language-aware multi-centroid scoring when segment language is known.
     * Returns a list of (Profile, SimilarityScore) sorted descending.
     */
    fun matchEmbedding(
        embedding: FloatArray,
        profiles: List<SpeakerProfile>,
        spokenLanguage: String? = null
    ): List<Pair<SpeakerProfile, Float>> {
        if (profiles.isEmpty()) return emptyList()
        val normalized = normalize(embedding)
        val langKey = spokenLanguage?.lowercase()?.trim()?.takeIf { it.isNotBlank() }

        return profiles.map { profile ->
            // 1. Global centroid similarity
            val simGlobal = computeCosineSimilarity(normalized, profile.centroidEmbedding.toFloatArray())

            // 2. Language-specific centroid similarity
            val simLang = if (langKey != null && profile.languageCentroids.containsKey(langKey)) {
                val langVec = profile.languageCentroids[langKey]!!.toFloatArray()
                computeCosineSimilarity(normalized, langVec)
            } else null

            // 3. Best of other language centroids (slight penalty if matching across languages)
            val bestOtherLangSim = profile.languageCentroids
                .filterKeys { it != langKey }
                .values
                .maxOfOrNull { computeCosineSimilarity(normalized, it.toFloatArray()) }

            val effectiveScore = when {
                // Exact language match available: take best of lang-specific and global
                simLang != null -> maxOf(simLang, simGlobal)
                // Other language centroids exist: take max of global and best exemplar
                bestOtherLangSim != null -> maxOf(simGlobal, bestOtherLangSim * 0.96f)
                else -> simGlobal
            }

            Pair(profile, effectiveScore.coerceIn(-1.0f, 1.0f))
        }.sortedByDescending { it.second }
    }

    /**
     * Performs continuous online adaptation by updating both the speaker's global centroid
     * and their language-specific centroid.
     */
    suspend fun adaptProfileCentroid(
        speakerName: String,
        newEmbedding: FloatArray,
        spokenLanguage: String? = null,
        alpha: Float = 0.12f
    ) = withContext(Dispatchers.IO) {
        if (!isContinuousLearningEnabled) return@withContext
        mutex.withLock {
            val profiles = loadProfilesInternal().toMutableList()
            val idx = profiles.indexOfFirst { it.name.equals(speakerName, ignoreCase = true) }
            if (idx >= 0) {
                val profile = profiles[idx]
                val normalized = normalize(newEmbedding)
                val newCentroid = updateCentroidEma(profile.centroidEmbedding.toFloatArray(), normalized, alpha)

                val updatedLangCentroids = profile.languageCentroids.toMutableMap()
                val updatedLangCounts = profile.languageSampleCounts.toMutableMap()
                val langKey = spokenLanguage?.lowercase()?.trim()?.takeIf { it.isNotBlank() }

                if (langKey != null) {
                    val existingLang = updatedLangCentroids[langKey]?.toFloatArray()
                    val newLang = if (existingLang != null) {
                        updateCentroidEma(existingLang, normalized, alpha = (alpha * 1.5f).coerceAtMost(0.35f))
                    } else {
                        normalized
                    }
                    updatedLangCentroids[langKey] = newLang.toList()
                    updatedLangCounts[langKey] = (updatedLangCounts[langKey] ?: 0) + 1
                }

                profiles[idx] = profile.copy(
                    sampleCount = profile.sampleCount + 1,
                    centroidEmbedding = newCentroid.toList(),
                    languageCentroids = updatedLangCentroids,
                    languageSampleCounts = updatedLangCounts,
                    lastUpdatedEpochMs = System.currentTimeMillis()
                )
                saveProfilesInternal(profiles)
                Log.i(TAG, "Adapted voiceprint for '${profile.name}' (Lang: $langKey, Global samples: ${profiles[idx].sampleCount})")
            }
        }
    }

    // ==========================================
    // Vector & Centroid Math
    // ==========================================

    fun computeCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0.0f
        var dot = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        return if (denom > 1e-8f) (dot / denom).coerceIn(-1.0f, 1.0f) else 0.0f
    }

    fun normalize(v: FloatArray): FloatArray {
        var sumSq = 0.0f
        for (x in v) sumSq += x * x
        val norm = sqrt(sumSq)
        if (norm < 1e-8f) return v.clone()
        return FloatArray(v.size) { i -> v[i] / norm }
    }

    fun updateCentroidEma(centroid: FloatArray, newSample: FloatArray, alpha: Float): FloatArray {
        val updated = FloatArray(centroid.size) { i ->
            (1.0f - alpha) * centroid[i] + alpha * newSample[i]
        }
        return normalize(updated)
    }

    /**
     * Updates legal consent and recording permissions for a specific speaker profile.
     */
    suspend fun updateConsent(
        profileId: String,
        allowedToRecord: Boolean,
        consentNote: String? = null,
        expiresAtEpochMs: Long? = null,
        confidenceThresholdOverride: Float? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val profiles = loadProfilesInternal().toMutableList()
            val idx = profiles.indexOfFirst { it.id == profileId }
            if (idx >= 0) {
                profiles[idx] = profiles[idx].copy(
                    allowedToRecord = allowedToRecord,
                    consentTimestamp = if (allowedToRecord) System.currentTimeMillis() else null,
                    consentNote = consentNote,
                    expiresAtEpochMs = expiresAtEpochMs,
                    confidenceThresholdOverride = confidenceThresholdOverride,
                    lastUpdatedEpochMs = System.currentTimeMillis()
                )
                saveProfilesInternal(profiles)
                Log.i(TAG, "Updated consent for '${profiles[idx].name}': allowed=$allowedToRecord")
            }
        }
    }

    /**
     * Retrieves all profiles explicitly authorized for audio recording, sorted by priority.
     */
    suspend fun getAllowedProfiles(): List<SpeakerProfile> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        getProfiles().filter { profile ->
            profile.allowedToRecord && (profile.expiresAtEpochMs == null || profile.expiresAtEpochMs > now)
        }.sortedByDescending { it.totalRecordedSeconds }
    }

    private fun loadProfilesInternal(): List<SpeakerProfile> {
        return try {
            if (profilesFile.exists()) {
                val content = profilesFile.readText(Charsets.UTF_8)
                json.decodeFromString<List<SpeakerProfile>>(content)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read speaker profiles", e)
            emptyList()
        }
    }

    private fun saveProfilesInternal(profiles: List<SpeakerProfile>) {
        try {
            val content = json.encodeToString(profiles)
            profilesFile.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save speaker profiles", e)
        }
    }

    companion object {
        private const val TAG = "SpeakerProfileManager"
        const val KEY_SPEAKER_RECOGNITION_ENABLED = "key_speaker_recognition_enabled"
        const val KEY_CONTINUOUS_LEARNING_ENABLED = "key_continuous_learning_enabled"
        const val KEY_SPEAKER_THRESHOLD = "key_speaker_threshold"
        const val DEFAULT_THRESHOLD = 0.65f
        const val KEY_VOICE_GATE_ENABLED = "key_voice_gate_enabled"
        const val KEY_VOICE_GATE_THRESHOLD = "key_voice_gate_threshold"
        const val DEFAULT_VOICE_GATE_THRESHOLD = 0.72f
    }
}

