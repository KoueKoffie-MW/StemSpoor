package com.example.recme

import com.example.recme.ai.worker.TranscriptionStateTracker
import com.example.recme.ai.worker.TranscriptionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionQueueUnitTest {

    @Test
    fun testTranscriptionStatusQueued() = runBlocking {
        TranscriptionStateTracker.updateStatus("file_alpha.wav", TranscriptionStatus.Queued(1))
        TranscriptionStateTracker.updateStatus("file_beta.wav", TranscriptionStatus.Queued(2))

        val map = TranscriptionStateTracker.statusFlow.first()
        assertEquals(TranscriptionStatus.Queued(1), map["file_alpha.wav"])
        assertEquals(TranscriptionStatus.Queued(2), map["file_beta.wav"])

        TranscriptionStateTracker.updateStatus("file_alpha.wav", TranscriptionStatus.Idle)
        val updatedMap = TranscriptionStateTracker.statusFlow.first()
        assertEquals(TranscriptionStatus.Idle, updatedMap["file_alpha.wav"])
    }
}
