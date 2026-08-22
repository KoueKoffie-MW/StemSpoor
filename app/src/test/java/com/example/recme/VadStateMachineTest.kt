package com.example.recme

import com.example.recme.audio.AudioConstants
import com.example.recme.audio.CircularAudioBuffer
import com.example.recme.audio.VadState
import com.example.recme.audio.VadStateListener
import com.example.recme.audio.VadStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VadStateMachineTest {

    @Test
    fun testTransitionsAndPreRollEmission() {
        var segmentStartedCount = 0
        var recordedFramesCount = 0
        var segmentEndedCount = 0
        var preRollFramesReceived = 0

        val listener = object : VadStateListener {
            override fun onSegmentStarted(preRollFrames: List<ShortArray>, speechStartEpochMs: Long, preRollMs: Long) {
                segmentStartedCount++
                preRollFramesReceived = preRollFrames.size
            }

            override fun onFrameToRecord(frame: ShortArray) {
                recordedFramesCount++
            }

            override fun onSegmentEnded(speechEndEpochMs: Long, postRollMs: Long) {
                segmentEndedCount++
            }

            override fun onSilenceTimeout() {}
        }

        val ringBuffer = CircularAudioBuffer(capacity = 5, frameSize = 4)
        val stateMachine = VadStateMachine(
            preRollBuffer = ringBuffer,
            threshold = 0.5f,
            listener = listener
        )

        val frame = ShortArray(4) { 100 }

        // Send 3 silent frames
        stateMachine.processFrame(frame, prob = 0.1f, currentEpochMs = 1000L)
        stateMachine.processFrame(frame, prob = 0.2f, currentEpochMs = 1032L)
        stateMachine.processFrame(frame, prob = 0.1f, currentEpochMs = 1064L)
        assertEquals(VadState.LISTENING, stateMachine.currentState)
        assertEquals(0, segmentStartedCount)
        assertEquals(0, recordedFramesCount)

        // Send speech frame (triggers RECORDING)
        stateMachine.processFrame(frame, prob = 0.85f, currentEpochMs = 1096L)
        assertEquals(VadState.RECORDING, stateMachine.currentState)
        assertEquals(1, segmentStartedCount)
        assertEquals(3, preRollFramesReceived)
        assertEquals(1, recordedFramesCount)

        // Send continuous speech frame
        stateMachine.processFrame(frame, prob = 0.9f, currentEpochMs = 1128L)
        assertEquals(VadState.RECORDING, stateMachine.currentState)
        assertEquals(2, recordedFramesCount)

        // Send 1 silent frame -> transitions to POST_ROLL
        stateMachine.processFrame(frame, prob = 0.2f, currentEpochMs = 1160L)
        assertEquals(VadState.POST_ROLL, stateMachine.currentState)
        assertEquals(3, recordedFramesCount)

        // Send speech again -> resumes RECORDING
        stateMachine.processFrame(frame, prob = 0.8f, currentEpochMs = 1192L)
        assertEquals(VadState.RECORDING, stateMachine.currentState)
        assertEquals(4, recordedFramesCount)

        // Send 19 silent frames -> ends segment and transitions back to LISTENING
        for (i in 1..AudioConstants.BUFFER_FRAME_COUNT) {
            stateMachine.processFrame(frame, prob = 0.1f, currentEpochMs = 1200L + (i * 32L))
        }
        assertEquals(VadState.LISTENING, stateMachine.currentState)
        assertEquals(1, segmentEndedCount)
    }
}
