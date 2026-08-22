package com.example.recme

import com.example.recme.storage.SpeechSegmentData
import com.example.recme.storage.StorageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentMergerTest {

    @Test
    fun testMergeAdjacentSegmentsWithin3Seconds() {
        // Test pure segment merging logic
        val rawSegments = listOf(
            SpeechSegmentData(
                segmentIndex = 0,
                audioStartMs = 0L,
                audioEndMs = 1200L,
                speechStartEpochMs = 1700000000000L,
                speechEndEpochMs = 1700000001200L,
                preRollMs = 608L,
                postRollMs = 608L,
                detectedLanguage = "de",
                rawText = "Guten",
                polishedText = "Guten"
            ),
            SpeechSegmentData(
                segmentIndex = 1,
                audioStartMs = 1800L, // 600ms gap (< 3000ms)
                audioEndMs = 2600L,
                speechStartEpochMs = 1700000001800L,
                speechEndEpochMs = 1700000002600L,
                preRollMs = 608L,
                postRollMs = 608L,
                detectedLanguage = "de",
                rawText = "Morgen",
                polishedText = "Morgen"
            ),
            SpeechSegmentData(
                segmentIndex = 2,
                audioStartMs = 8000L, // 5400ms gap (> 3000ms)
                audioEndMs = 10500L,
                speechStartEpochMs = 1700000008000L,
                speechEndEpochMs = 1700000010500L,
                preRollMs = 608L,
                postRollMs = 608L,
                detectedLanguage = "de",
                rawText = "Wie geht es Ihnen",
                polishedText = "Wie geht es Ihnen"
            )
        )

        val merged = mergeTestSegments(rawSegments, gapThresholdMs = 3000L)

        assertEquals(2, merged.size)

        // First merged segment: 0L to 2600L
        assertEquals(0, merged[0].segmentIndex)
        assertEquals(0L, merged[0].audioStartMs)
        assertEquals(2600L, merged[0].audioEndMs)
        assertEquals("Guten Morgen", merged[0].rawText)

        // Second segment remained separate
        assertEquals(1, merged[1].segmentIndex)
        assertEquals(8000L, merged[1].audioStartMs)
        assertEquals(10500L, merged[1].audioEndMs)
        assertEquals("Wie geht es Ihnen", merged[1].rawText)
    }

    private fun mergeTestSegments(
        segments: List<SpeechSegmentData>,
        gapThresholdMs: Long = 3000L,
        maxSegmentDurationMs: Long = 30000L
    ): List<SpeechSegmentData> {
        if (segments.size <= 1) return segments

        val merged = mutableListOf<SpeechSegmentData>()
        var current = segments[0]

        for (i in 1 until segments.size) {
            val next = segments[i]
            val gap = next.audioStartMs - current.audioEndMs
            val combinedDuration = next.audioEndMs - current.audioStartMs

            if (gap <= gapThresholdMs && combinedDuration <= maxSegmentDurationMs) {
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
}
