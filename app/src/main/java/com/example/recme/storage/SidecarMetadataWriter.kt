package com.example.recme.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Handles thread-safe atomic serialization of companion JSON sidecar metadata.
 */
class SidecarMetadataWriter(
    private val targetJsonFile: File,
    private val wavFileName: String,
    private val sessionId: String,
    private val startedAtEpochMs: Long
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val segments = mutableListOf<SpeechSegmentData>()

    /**
     * Adds a finalized speech segment and flushes the JSON atomically to disk.
     */
    @Synchronized
    fun addSegment(segment: SpeechSegmentData) {
        segments.add(segment)
        flushToDisk()
    }

    /**
     * Returns a snapshot of the current sidecar document.
     */
    @Synchronized
    fun getSidecarData(): SidecarData {
        return SidecarData(
            version = 1,
            fileName = wavFileName,
            recordingSessionId = sessionId,
            startedAtEpochMs = startedAtEpochMs,
            segments = ArrayList(segments)
        )
    }

    /**
     * Finalizes the sidecar by merging segments within gapThresholdMs (< 3s) and writing clean JSON to disk.
     */
    @Synchronized
    fun finalizeAndMergeSegments(gapThresholdMs: Long = 3000L) {
        if (segments.size > 1) {
            val merged = mutableListOf<SpeechSegmentData>()
            var current = segments[0]

            for (i in 1 until segments.size) {
                val next = segments[i]
                val gap = next.audioStartMs - current.audioEndMs
                val duration = next.audioEndMs - current.audioStartMs

                if (gap <= gapThresholdMs && duration <= 30000L) {
                    current = current.copy(
                        audioEndMs = next.audioEndMs,
                        rawText = listOfNotNull(current.rawText?.takeIf { it.isNotBlank() }, next.rawText?.takeIf { it.isNotBlank() }).joinToString(" "),
                        polishedText = listOfNotNull(current.polishedText?.takeIf { it.isNotBlank() }, next.polishedText?.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { null }
                    )
                } else {
                    merged.add(current.copy(segmentIndex = merged.size))
                    current = next
                }
            }
            merged.add(current.copy(segmentIndex = merged.size))
            segments.clear()
            segments.addAll(merged)
        }
        flushToDisk()
    }

    /**
     * Atomically serializes the sidecar document to disk via a temporary file rename.
     */
    @Synchronized
    fun flushToDisk() {
        val data = getSidecarData()
        val jsonString = json.encodeToString(data)
        val tempFile = File(targetJsonFile.parentFile, "${targetJsonFile.name}.tmp")

        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.fd.sync()
            }
            if (tempFile.exists()) {
                if (targetJsonFile.exists()) {
                    targetJsonFile.delete()
                }
                tempFile.renameTo(targetJsonFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
