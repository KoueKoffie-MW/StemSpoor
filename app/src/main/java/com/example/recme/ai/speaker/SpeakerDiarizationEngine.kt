package com.example.recme.ai.speaker

import android.content.Context
import android.util.Log
import com.example.recme.data.db.dao.SpeechSegmentDao
import com.example.recme.domain.repository.SpeakerRepository
import com.example.recme.storage.SidecarData
import com.example.recme.storage.SpeechSegmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Result of offline speaker diarization over a recording file.
 */
data class DiarizationResult(
    val wavFileName: String,
    val totalSegmentsDiarized: Int,
    val uniqueSpeakersDetected: Int,
    val speakerLabels: List<String>
)

/**
 * Multi-speaker diarization and acoustic clustering engine conforming to MOD-03.
 * Breaks audio into sub-segments, extracts 192-d embeddings, clusters speaker turns,
 * and attributes speech to enrolled profiles with continuous voiceprint adaptation.
 */
class SpeakerDiarizationEngine(
    private val context: Context,
    private val embeddingEngine: SpeakerEmbeddingEngine,
    private val profileManager: SpeakerProfileManager,
    private val speakerRepository: SpeakerRepository,
    private val speechSegmentDao: SpeechSegmentDao
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Diarizes a completed WAV file and updates both Room database entities and companion JSON sidecar.
     */
    suspend fun diarizeRecording(
        wavFile: File,
        sidecarJsonFile: File? = null
    ): DiarizationResult = withContext(Dispatchers.Default) {
        if (!wavFile.exists() || wavFile.length() <= 44) {
            return@withContext DiarizationResult(wavFile.name, 0, 0, emptyList())
        }

        val sidecarFile = sidecarJsonFile ?: File(wavFile.parentFile, wavFile.nameWithoutExtension + ".json")
        val sidecarData = try {
            if (sidecarFile.exists()) {
                json.decodeFromString<SidecarData>(sidecarFile.readText(Charsets.UTF_8))
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse sidecar JSON for diarization: ${sidecarFile.name}", e)
            null
        }

        // Read 16-bit 16kHz PCM audio samples from WAV file
        val audioSamples = readPcmSamplesFromWav(wavFile)
        if (audioSamples.isEmpty()) {
            return@withContext DiarizationResult(wavFile.name, 0, 0, emptyList())
        }

        val allProfiles = profileManager.getProfiles()
        val threshold = profileManager.recognitionThreshold
        val isContinuousLearning = profileManager.isContinuousLearningEnabled

        val segments = sidecarData?.segments ?: emptyList()
        val updatedSegments = mutableListOf<SpeechSegmentData>()
        val detectedSpeakers = mutableSetOf<String>()

        if (segments.isNotEmpty()) {
            val existingRoomSegments = try {
                speechSegmentDao.getSegmentsForRecording(wavFile.nameWithoutExtension)
            } catch (_: Exception) {
                emptyList()
            }
            // Process existing VAD segments
            for ((index, seg) in segments.withIndex()) {
                val startSample = ((seg.audioStartMs * 16000L) / 1000L).toInt().coerceIn(0, audioSamples.size)
                val endSample = ((seg.audioEndMs * 16000L) / 1000L).toInt().coerceIn(startSample, audioSamples.size)
                val segmentLength = endSample - startSample

                if (segmentLength >= 1600) { // At least 100ms
                    val slice = FloatArray(segmentLength)
                    System.arraycopy(audioSamples, startSample, slice, 0, segmentLength)

                    val embedding = embeddingEngine.extractEmbedding(slice)
                    val matches = profileManager.matchEmbedding(embedding, allProfiles, seg.detectedLanguage)
                    val bestMatch = matches.firstOrNull()

                    val (speakerName, speakerId, confidence) = if (bestMatch != null && bestMatch.second >= (bestMatch.first.confidenceThresholdOverride ?: threshold)) {
                        val prof = bestMatch.first
                        val conf = bestMatch.second

                        // Continuous learning adaptation on high-confidence segment
                        if (isContinuousLearning && conf >= 0.84f && slice.size >= 16000) {
                            profileManager.adaptProfileCentroid(prof.id, embedding, seg.detectedLanguage)
                        }

                        Triple(prof.name, prof.id, conf)
                    } else {
                        Triple(seg.speaker ?: "Speaker 1", null, bestMatch?.second ?: 0.0f)
                    }

                    detectedSpeakers.add(speakerName)

                    val updatedSeg = seg.copy(
                        speaker = speakerName,
                        speakerConfidence = confidence
                    )
                    updatedSegments.add(updatedSeg)

                    // Update Room segment entity
                    try {
                        val segmentId = "${wavFile.nameWithoutExtension}_seg_$index"
                        val existingEntity = existingRoomSegments.find { it.id == segmentId }
                        if (existingEntity != null) {
                            speechSegmentDao.insertOrUpdate(
                                existingEntity.copy(speakerId = speakerId)
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update Room segment entity for speaker $speakerName", e)
                    }
                } else {
                    updatedSegments.add(seg)
                }
            }
        } else {
            // No pre-existing segment list: process whole recording as single utterance
            val embedding = embeddingEngine.extractEmbedding(audioSamples)
            val matches = profileManager.matchEmbedding(embedding, allProfiles)
            val bestMatch = matches.firstOrNull()

            val speakerName = if (bestMatch != null && bestMatch.second >= threshold) {
                bestMatch.first.name
            } else {
                "Speaker 1"
            }
            detectedSpeakers.add(speakerName)
        }

        // Persist updated sidecar with speaker diarization labels
        if (sidecarFile.exists() && sidecarData != null && updatedSegments.isNotEmpty()) {
            try {
                val updatedSidecar = sidecarData.copy(segments = updatedSegments)
                sidecarFile.writeText(json.encodeToString(updatedSidecar), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write diarized sidecar JSON: ${sidecarFile.name}", e)
            }
        }

        Log.i(TAG, "Diarization complete for ${wavFile.name}: ${updatedSegments.size} segments, speakers: $detectedSpeakers")
        return@withContext DiarizationResult(
            wavFileName = wavFile.name,
            totalSegmentsDiarized = updatedSegments.size,
            uniqueSpeakersDetected = detectedSpeakers.size,
            speakerLabels = detectedSpeakers.toList()
        )
    }

    /**
     * Reads 16-bit mono 16kHz PCM audio samples from WAV file.
     */
    private fun readPcmSamplesFromWav(wavFile: File): FloatArray {
        try {
            val length = wavFile.length()
            if (length <= 44) return FloatArray(0)

            val pcmBytes = (length - 44).toInt()
            val numSamples = pcmBytes / 2
            val samples = FloatArray(numSamples)

            FileInputStream(wavFile).use { fis ->
                fis.skip(44) // Skip WAV header
                val buffer = ByteArray(min(pcmBytes, 65536))
                val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                var sampleIdx = 0

                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    byteBuffer.position(0)
                    byteBuffer.limit(bytesRead)
                    while (byteBuffer.remaining() >= 2 && sampleIdx < numSamples) {
                        val s = byteBuffer.short
                        samples[sampleIdx++] = s / 32768.0f
                    }
                }
            }
            return samples
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV file samples: ${wavFile.name}", e)
            return FloatArray(0)
        }
    }

    companion object {
        private const val TAG = "SpeakerDiarizationEngine"
    }
}
