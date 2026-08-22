package com.example.recme

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.recme.ai.worker.TranscriptionQueue
import com.example.recme.ai.worker.TranscriptionStateTracker
import com.example.recme.ai.worker.TranscriptionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptionQueueTest {

    @Test
    fun testQueueSequentialPositionsAndCancellation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Verify status tracker and queue size initial state
        assertEquals(0, TranscriptionQueue.queueSizeFlow.value)

        // Test state tracker queue position updates
        TranscriptionStateTracker.updateStatus("file_1.wav", TranscriptionStatus.Queued(1))
        TranscriptionStateTracker.updateStatus("file_2.wav", TranscriptionStatus.Queued(2))

        val statusMap = TranscriptionStateTracker.statusFlow.first()
        assertEquals(TranscriptionStatus.Queued(1), statusMap["file_1.wav"])
        assertEquals(TranscriptionStatus.Queued(2), statusMap["file_2.wav"])

        // Test cancel
        TranscriptionQueue.cancel("file_2.wav")
        delay(100)
        assertEquals(TranscriptionStatus.Idle, TranscriptionStateTracker.statusFlow.value["file_2.wav"])
    }
}
