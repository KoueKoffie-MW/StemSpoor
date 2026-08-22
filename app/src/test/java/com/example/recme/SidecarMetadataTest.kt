package com.example.recme

import com.example.recme.storage.SidecarData
import com.example.recme.storage.SidecarMetadataWriter
import com.example.recme.storage.SpeechSegmentData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SidecarMetadataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSidecarJsonSerializationAndDeserialization() {
        val targetJson = tempFolder.newFile("test_session.json")
        val writer = SidecarMetadataWriter(
            targetJsonFile = targetJson,
            wavFileName = "test_session.wav",
            sessionId = "session-1234",
            startedAtEpochMs = 1787181300000L
        )

        val segment0 = SpeechSegmentData(
            segmentIndex = 0,
            audioStartMs = 0L,
            audioEndMs = 4200L,
            speechStartEpochMs = 1787181300600L,
            speechEndEpochMs = 1787181304200L,
            preRollMs = 600L,
            postRollMs = 600L
        )

        val segment1 = SpeechSegmentData(
            segmentIndex = 1,
            audioStartMs = 4200L,
            audioEndMs = 9000L,
            speechStartEpochMs = 1787181310000L,
            speechEndEpochMs = 1787181314800L,
            preRollMs = 600L,
            postRollMs = 600L
        )

        writer.addSegment(segment0)
        writer.addSegment(segment1)

        val fileContent = targetJson.readText()
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString<SidecarData>(fileContent)

        assertEquals("test_session.wav", parsed.fileName)
        assertEquals("session-1234", parsed.recordingSessionId)
        assertEquals(1787181300000L, parsed.startedAtEpochMs)
        assertEquals(2, parsed.segments.size)

        assertEquals(0, parsed.segments[0].segmentIndex)
        assertEquals(0L, parsed.segments[0].audioStartMs)
        assertEquals(4200L, parsed.segments[0].audioEndMs)

        assertEquals(1, parsed.segments[1].segmentIndex)
        assertEquals(4200L, parsed.segments[1].audioStartMs)
        assertEquals(9000L, parsed.segments[1].audioEndMs)
    }
}
