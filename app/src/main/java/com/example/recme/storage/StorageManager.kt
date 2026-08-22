package com.example.recme.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import com.example.recme.audio.AudioConstants
import com.example.recme.audio.WavAudioWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Model representing a recorded audio file (WAV or Opus) and its companion JSON sidecar.
 */
data class RecordingItem(
    val baseName: String,
    val audioFile: File,
    val jsonFile: File?,
    val fileSizeBytes: Long,
    val totalAudioDurationMs: Long,
    val lastModifiedEpochMs: Long,
    val sidecarData: SidecarData?,
    val isCompressed: Boolean = audioFile.name.endsWith(".opus", ignoreCase = true) || audioFile.name.endsWith(".ogg", ignoreCase = true)
) {
    // Backwards-compatible alias for existing screen callers
    val wavFile: File get() = audioFile
    val isCloudSynced: Boolean get() = sidecarData?.driveFileId != null
}

/**
 * Coordinates directory paths, file discovery, repair scans, and file deletions.
 */
class StorageManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Resolves the primary recording directory:
     * - Uses public Documents/RecMe if All Files Access is granted
     * - Falls back to app external documents directory (always writable without special permission)
     */
    fun getRecordingsDirectory(): File {
        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        if (hasAllFilesAccess) {
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val recMeDir = File(publicDocs, "RecMe")
            if (!recMeDir.exists()) {
                recMeDir.mkdirs()
            }
            if (recMeDir.exists() && recMeDir.canWrite()) {
                return recMeDir
            }
        }

        // App-specific external storage (Always writable on all Android versions)
        val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val fallbackDir = File(appExternal, "RecMe")
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return fallbackDir
    }

    /**
     * Lists all recorded Opus and WAV files from both public and internal fallback directories.
     */
    fun listRecordings(): List<RecordingItem> {
        val dirsToScan = mutableListOf<File>()
        dirsToScan.add(getRecordingsDirectory())

        // Also check fallback if currently using public
        val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (appExternal != null) {
            val fallbackDir = File(appExternal, "RecMe")
            if (fallbackDir.exists() && fallbackDir.absolutePath != getRecordingsDirectory().absolutePath) {
                dirsToScan.add(fallbackDir)
            }
        }

        val results = mutableListOf<RecordingItem>()
        val seenBaseNames = mutableSetOf<String>()

        for (dir in dirsToScan) {
            val files = dir.listFiles { _, name ->
                name.endsWith(".opus", ignoreCase = true) ||
                name.endsWith(".ogg", ignoreCase = true) ||
                name.endsWith(".wav", ignoreCase = true)
            } ?: continue

            // Sort so .opus/.ogg comes before .wav if both exist
            val sortedFiles = files.sortedBy { if (it.name.endsWith(".wav", ignoreCase = true)) 1 else 0 }

            for (audioFile in sortedFiles) {
                val baseName = audioFile.nameWithoutExtension
                if (seenBaseNames.contains(baseName)) {
                    // Already have the compressed version for this baseName
                    if (audioFile.name.endsWith(".wav", ignoreCase = true)) {
                        audioFile.delete() // Clean up duplicate WAV
                    }
                    continue
                }
                seenBaseNames.add(baseName)

                val jsonFile = File(dir, "$baseName.json").takeIf { it.exists() }
                val sidecar = jsonFile?.let { parseSidecar(it) }

                // Calculate duration from sidecar segments or PCM byte estimation
                val durationMs = if (sidecar != null && sidecar.segments.isNotEmpty()) {
                    sidecar.segments.last().audioEndMs
                } else if (audioFile.name.endsWith(".wav", ignoreCase = true)) {
                    val pcmBytes = (audioFile.length() - 44).coerceAtLeast(0)
                    (pcmBytes / (AudioConstants.SAMPLE_RATE_HZ * AudioConstants.BYTES_PER_SAMPLE)) * 1000L
                } else {
                    // Approximate for Opus @ 32kbps = 4000 bytes/sec
                    (audioFile.length() / (AudioConstants.OPUS_BITRATE_BPS / 8)) * 1000L
                }

                results.add(
                    RecordingItem(
                        baseName = baseName,
                        audioFile = audioFile,
                        jsonFile = jsonFile,
                        fileSizeBytes = audioFile.length(),
                        totalAudioDurationMs = durationMs,
                        lastModifiedEpochMs = audioFile.lastModified(),
                        sidecarData = sidecar
                    )
                )
            }
        }

        return results.sortedByDescending { it.lastModifiedEpochMs }
    }

    /**
     * Safely reads and parses a companion sidecar JSON file.
     */
    fun parseSidecar(jsonFile: File): SidecarData? {
        return try {
            val content = jsonFile.readText(Charsets.UTF_8)
            json.decodeFromString<SidecarData>(content)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes the audio file and its companion JSON sidecar.
     */
    fun deleteRecording(item: RecordingItem): Boolean {
        var success = true
        if (item.audioFile.exists()) {
            success = success && item.audioFile.delete()
        }
        item.jsonFile?.let {
            if (it.exists()) {
                success = success && it.delete()
            }
        }
        // Also delete exported markdown notes if present
        val mdFile = File(item.audioFile.parentFile, "${item.baseName}.md")
        if (mdFile.exists()) mdFile.delete()
        val vaultRecordingMd = File(File(item.audioFile.parentFile, "vault/recordings"), "${item.baseName}.md")
        if (vaultRecordingMd.exists()) vaultRecordingMd.delete()
        return success
    }

    /**
     * Deletes the transcript only (resets sidecar isTranscribed = false, clears text/speaker, removes .md notes),
     * keeping the audio file intact.
     */
    fun deleteTranscript(item: RecordingItem): Boolean {
        // 1. Delete standalone markdown notes
        val mdFile = File(item.audioFile.parentFile, "${item.baseName}.md")
        if (mdFile.exists()) mdFile.delete()

        val vaultRecordingMd = File(File(item.audioFile.parentFile, "vault/recordings"), "${item.baseName}.md")
        if (vaultRecordingMd.exists()) vaultRecordingMd.delete()

        // 2. Clear transcript data in sidecar JSON
        val jsonFile = item.jsonFile ?: File(item.audioFile.parentFile, "${item.baseName}.json")
        if (jsonFile.exists() && item.sidecarData != null) {
            val clearedSegments = item.sidecarData.segments.map { seg ->
                seg.copy(
                    rawText = null,
                    polishedText = null,
                    speaker = null,
                    speakerConfidence = null,
                    detectedLanguage = null
                )
            }
            val resetSidecar = item.sidecarData.copy(
                isTranscribed = false,
                segments = clearedSegments
            )
            val jsonStr = json.encodeToString(SidecarData.serializer(), resetSidecar)
            jsonFile.writeText(jsonStr, Charsets.UTF_8)
            return true
        }
        return false
    }

    /**
     * Retrieves an existing sidecar or creates a synthesized sidecar spanning the audio duration
     * for standalone WAV or Opus files without sidecar metadata.
     */
    fun getOrCreateSidecar(audioFile: File): Pair<File, SidecarData> {
        val dir = audioFile.parentFile ?: getRecordingsDirectory()
        val baseName = audioFile.nameWithoutExtension
        val jsonFile = File(dir, "$baseName.json")

        if (jsonFile.exists()) {
            val sidecar = parseSidecar(jsonFile)
            if (sidecar != null) {
                return Pair(jsonFile, sidecar)
            }
        }

        // Synthesize single segment covering entire file
        val durationMs = if (audioFile.name.endsWith(".wav", ignoreCase = true)) {
            val pcmBytes = (audioFile.length() - 44).coerceAtLeast(0)
            ((pcmBytes / (AudioConstants.SAMPLE_RATE_HZ * AudioConstants.BYTES_PER_SAMPLE)) * 1000L).coerceAtLeast(1000L)
        } else {
            ((audioFile.length() / (AudioConstants.OPUS_BITRATE_BPS / 8)) * 1000L).coerceAtLeast(1000L)
        }

        val now = audioFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
        val defaultSegment = SpeechSegmentData(
            segmentIndex = 0,
            audioStartMs = 0L,
            audioEndMs = durationMs,
            speechStartEpochMs = now,
            speechEndEpochMs = now + durationMs,
            preRollMs = 0L,
            postRollMs = 0L
        )

        val newSidecar = SidecarData(
            fileName = audioFile.name,
            recordingSessionId = java.util.UUID.randomUUID().toString(),
            startedAtEpochMs = now,
            isTranscribed = false,
            segments = listOf(defaultSegment)
        )

        val jsonStr = json.encodeToString(SidecarData.serializer(), newSidecar)
        jsonFile.writeText(jsonStr, Charsets.UTF_8)
        return Pair(jsonFile, newSidecar)
    }

    /**
     * Scans directory on startup and repairs any unfinalized WAV headers resulting from abrupt kills.
     */
    fun repairCorruptRecordings(): Int {
        val dir = getRecordingsDirectory()
        val wavFiles = dir.listFiles { _, name -> name.endsWith(".wav", ignoreCase = true) } ?: return 0
        var repairedCount = 0

        for (file in wavFiles) {
            if (file.length() > 44) {
                if (WavAudioWriter.repairHeaderIfCorrupt(file)) {
                    repairedCount++
                }
            }
        }
        return repairedCount
    }

    /**
     * Compresses any completed WAV files found in storage on startup if Opus compression is enabled.
     */
    fun compressPendingWavFiles(scope: CoroutineScope) {
        val prefs = context.getSharedPreferences(com.example.recme.service.VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val isOpusEnabled = prefs.getBoolean(com.example.recme.service.VadRecordingService.KEY_OPUS_COMPRESSION, true)

        scope.launch(Dispatchers.IO) {
            val dir = getRecordingsDirectory()
            val wavFiles = dir.listFiles { _, name -> name.endsWith(".wav", ignoreCase = true) } ?: return@launch
            for (wavFile in wavFiles) {
                if (wavFile.length() > 44) {
                    val jsonFile = File(dir, "${wavFile.nameWithoutExtension}.json").takeIf { it.exists() }
                    if (isOpusEnabled) {
                        OpusAudioCompressor.compressWavToOpus(wavFile, jsonFile)
                    }
                }
            }
            com.example.recme.sync.SyncScheduler.scheduleImmediateSync(context)
        }
    }

    /**
     * Retrieves the user-configured segment merge gap threshold in milliseconds.
     * Defaults to 1000ms (1.0s). 0ms disables merging completely.
     */
    fun getSegmentMergeGapMs(): Long {
        val prefs = context.getSharedPreferences(com.example.recme.service.VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(
            com.example.recme.service.VadRecordingService.KEY_SEGMENT_MERGE_GAP_MS,
            com.example.recme.service.VadRecordingService.DEFAULT_SEGMENT_MERGE_GAP_MS
        )
    }

    /**
     * Updates the user-configured segment merge gap threshold in milliseconds.
     */
    fun setSegmentMergeGapMs(gapMs: Long) {
        val prefs = context.getSharedPreferences(com.example.recme.service.VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(com.example.recme.service.VadRecordingService.KEY_SEGMENT_MERGE_GAP_MS, gapMs).apply()
    }

    /**
     * Merges adjacent speech segments where the gap between consecutive segments <= gapThresholdMs.
     * If gapThresholdMs <= 0, no merging is performed.
     */
    fun mergeAdjacentSegments(
        segments: List<SpeechSegmentData>,
        gapThresholdMs: Long = getSegmentMergeGapMs(),
        maxSegmentDurationMs: Long = 30000L
    ): List<SpeechSegmentData> {
        if (segments.size <= 1 || gapThresholdMs <= 0L) return segments

        val merged = mutableListOf<SpeechSegmentData>()
        var current = segments[0]

        for (i in 1 until segments.size) {
            val next = segments[i]
            val gap = next.audioStartMs - current.audioEndMs
            val combinedDuration = next.audioEndMs - current.audioStartMs

            if (gap <= gapThresholdMs && combinedDuration <= maxSegmentDurationMs) {
                // Combine into single continuous segment
                val combinedRaw = listOfNotNull(current.rawText?.takeIf { it.isNotBlank() }, next.rawText?.takeIf { it.isNotBlank() }).joinToString(" ")
                val combinedPolished = listOfNotNull(current.polishedText?.takeIf { it.isNotBlank() }, next.polishedText?.takeIf { it.isNotBlank() }).joinToString(" ")
                val detectedLang = current.detectedLanguage ?: next.detectedLanguage

                current = current.copy(
                    audioEndMs = next.audioEndMs,
                    rawText = combinedRaw,
                    polishedText = combinedPolished.ifBlank { null },
                    detectedLanguage = detectedLang
                )
            } else {
                merged.add(current.copy(segmentIndex = merged.size))
                current = next
            }
        }
        merged.add(current.copy(segmentIndex = merged.size))
        return merged
    }

    /**
     * Re-merges speech segments on an existing recording and writes updated sidecar JSON.
     */
    fun remergeRecordingSegments(
        item: RecordingItem,
        gapThresholdMs: Long = getSegmentMergeGapMs()
    ): RecordingItem {
        val sidecar = item.sidecarData ?: return item
        val jsonFile = item.jsonFile ?: return item

        val mergedSegments = mergeAdjacentSegments(sidecar.segments, gapThresholdMs)
        val updatedSidecar = sidecar.copy(
            segments = mergedSegments,
            driveFileId = null // Reset driveFileId to force sync
        )

        return try {
            val serialized = json.encodeToString(SidecarData.serializer(), updatedSidecar)
            jsonFile.writeText(serialized, Charsets.UTF_8)
            item.copy(sidecarData = updatedSidecar)
        } catch (e: Exception) {
            e.printStackTrace()
            item
        }
    }

    /**
     * Re-merges all existing recordings in storage using the specified gap threshold.
     */
    fun remergeAllRecordings(gapThresholdMs: Long = getSegmentMergeGapMs()): Int {
        val list = listRecordings()
        var count = 0
        for (item in list) {
            if (item.sidecarData != null && item.jsonFile != null) {
                remergeRecordingSegments(item, gapThresholdMs)
                count++
            }
        }
        return count
    }
}
