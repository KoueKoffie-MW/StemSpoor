package com.example.recme.data.bootstrap

import android.util.Log
import com.example.recme.ai.speaker.SpeakerProfileManager
import com.example.recme.domain.model.Recording
import com.example.recme.domain.model.SpeakerDomain
import com.example.recme.domain.model.SpeechSegment
import com.example.recme.domain.repository.ConfigRepository
import com.example.recme.domain.repository.RecordingRepository
import com.example.recme.domain.repository.SpeakerRepository
import com.example.recme.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages one-time bootstrap and synchronization between authoritative disk sidecars (.json/WAV)
 * and the Room SQLite database query projection.
 */
class DatabaseBootstrapManager(
    private val recordingRepository: RecordingRepository,
    private val speakerRepository: SpeakerRepository,
    private val configRepository: ConfigRepository,
    private val storageManager: StorageManager,
    private val speakerProfileManager: SpeakerProfileManager
) {

    companion object {
        private const val TAG = "StemSpoorBootstrap"
        const val KEY_MERGE_GAP_MS = "segment_merge_gap_ms"
    }

    /**
     * Scans storage and populates Room database if empty or missing recent items.
     * Guaranteed to be idempotent and safe to run on app start.
     */
    suspend fun bootstrapFromDisk(): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Room projection bootstrap from disk...")

            // 1. Seed Config (Segment merge gap)
            val currentMergeGap = storageManager.getSegmentMergeGapMs()
            configRepository.setLong(KEY_MERGE_GAP_MS, currentMergeGap)

            // 2. Seed Speaker Profiles
            val enrolledProfiles = speakerProfileManager.getProfiles()
            for (profile in enrolledProfiles) {
                val existing = speakerRepository.getProfileById(profile.id)
                if (existing == null) {
                    speakerRepository.saveProfile(
                        SpeakerDomain(
                            id = profile.id,
                            name = profile.name,
                            relationship = profile.relationship,
                            colorHex = profile.colorHex,
                            allowedToRecord = true, // Default enrolled profiles to allowed
                            consentTimestamp = profile.lastUpdatedEpochMs,
                            consentNote = "Enrolled profile",
                            sampleCount = profile.sampleCount,
                            lastUpdated = profile.lastUpdatedEpochMs
                        )
                    )
                }
            }

            // 3. Scan & Project Recordings & Segments
            val items = storageManager.listRecordings()
            var indexedCount = 0

            for (item in items) {
                val sidecar = item.sidecarData ?: continue
                val existingRecording = recordingRepository.getRecordingById(item.baseName)

                val recording = Recording(
                    id = item.baseName,
                    startTimeWallMs = sidecar.startedAtEpochMs,
                    endTimeWallMs = sidecar.startedAtEpochMs + item.totalAudioDurationMs,
                    durationMs = item.totalAudioDurationMs,
                    sidecarPath = item.jsonFile?.absolutePath ?: "",
                    audioPath = item.audioFile.absolutePath,
                    isProcessed = sidecar.isTranscribed,
                    createdAt = item.lastModifiedEpochMs
                )
                recordingRepository.saveRecording(recording)

                if (sidecar.segments.isNotEmpty()) {
                    val segments = sidecar.segments.mapIndexed { idx, seg ->
                        SpeechSegment(
                            id = "${item.baseName}_$idx",
                            recordingId = item.baseName,
                            startTimeWallMs = seg.speechStartEpochMs,
                            endTimeWallMs = seg.speechEndEpochMs,
                            durationMs = (seg.audioEndMs - seg.audioStartMs).coerceAtLeast(0L),
                            speakerId = seg.speaker,
                            gateDecision = seg.gateDecision ?: "ALLOWED",
                            gateProfileId = seg.gateProfileId,
                            gateConfidence = seg.gateConfidence ?: seg.speakerConfidence,
                            gateReason = seg.gateReason,
                            language = seg.detectedLanguage,
                            hasTranscript = !seg.rawText.isNullOrBlank(),
                            transcriptText = seg.polishedText ?: seg.rawText,
                            createdAt = seg.speechStartEpochMs
                        )
                    }
                    recordingRepository.saveSegments(segments)
                }
                indexedCount++
            }

            Log.d(TAG, "Bootstrap completed successfully. Indexed $indexedCount recordings.")
            return@withContext indexedCount
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap failed with exception", e)
            return@withContext 0
        }
    }
}
