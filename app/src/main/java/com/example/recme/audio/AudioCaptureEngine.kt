package com.example.recme.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.recme.storage.OpusAudioCompressor
import com.example.recme.storage.RecordingSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Real-time audio engine metrics for UI visualizers.
 */
data class AudioEngineState(
    val isRunning: Boolean = false,
    val isSpeechDetected: Boolean = false,
    val currentVadProbability: Float = 0.0f,
    val currentRmsDb: Float = -100f,
    val activeFileName: String = "",
    val recordedDurationMs: Long = 0L,
    val totalFileSizeBytes: Long = 0L
)

/**
 * Manages AudioRecord hardware capture and connects the Silero VAD detector to the state machine.
 */
class AudioCaptureEngine(
    private val context: Context,
    private val storageDir: File
) : AutoCloseable {

    private val vadDetector = SileroVadDetector(context)
    private var sessionManager: RecordingSessionManager? = null

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _engineState = MutableStateFlow(AudioEngineState())
    val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    private var vadStateMachine: VadStateMachine? = null

    /**
     * Updates the VAD sensitivity threshold (0.1 to 0.95).
     */
    fun setSensitivity(threshold: Float) {
        vadStateMachine?.setThreshold(threshold)
    }

    /**
     * Starts continuous audio capture and real-time VAD processing.
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun startCapture(
        threshold: Float = AudioConstants.DEFAULT_VAD_THRESHOLD,
        maxFileSizeBytes: Long = AudioConstants.DEFAULT_MAX_FILE_SIZE_BYTES,
        isOpusCompressionEnabled: Boolean = true
    ) {
        if (captureJob?.isActive == true) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, AudioConstants.FRAME_SIZE_SAMPLES * 4)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioConstants.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        record.startRecording()
        audioRecord = record

        val sessionMgr = RecordingSessionManager(
            storageDir = storageDir,
            maxFileSizeBytes = maxFileSizeBytes,
            onSessionRoll = { newFile ->
                _engineState.value = _engineState.value.copy(activeFileName = newFile.name)
            },
            onPartFinalized = { completedWav, completedJson ->
                scope.launch(Dispatchers.IO) {
                    if (isOpusCompressionEnabled) {
                        OpusAudioCompressor.compressWavToOpus(completedWav, completedJson)
                    }
                    com.example.recme.sync.SyncScheduler.scheduleImmediateSync(context)
                }
            }
        )
        sessionManager = sessionMgr

        val stateListener = object : VadStateListener {
            override fun onSegmentStarted(
                preRollFrames: List<ShortArray>,
                speechStartEpochMs: Long,
                preRollMs: Long
            ) {
                sessionMgr.onSegmentStarted(preRollFrames, speechStartEpochMs, preRollMs)
                _engineState.value = _engineState.value.copy(isSpeechDetected = true)
            }

            override fun onFrameToRecord(frame: ShortArray) {
                sessionMgr.onFrameToRecord(frame)
            }

            override fun onSegmentEnded(speechEndEpochMs: Long, postRollMs: Long) {
                sessionMgr.onSegmentEnded(speechEndEpochMs, postRollMs)
                _engineState.value = _engineState.value.copy(isSpeechDetected = false)
            }

            override fun onSilenceTimeout() {
                vadDetector.resetState()
            }
        }

        val stateMachine = VadStateMachine(
            threshold = threshold,
            listener = stateListener
        )
        vadStateMachine = stateMachine

        _engineState.value = AudioEngineState(isRunning = true)

        captureJob = scope.launch {
            val frame = ShortArray(AudioConstants.FRAME_SIZE_SAMPLES)
            var readOffset = 0
            var frameCount = 0

            while (isActive) {
                val samplesNeeded = AudioConstants.FRAME_SIZE_SAMPLES - readOffset
                val samplesRead = record.read(frame, readOffset, samplesNeeded)

                if (samplesRead > 0) {
                    readOffset += samplesRead
                    if (readOffset == AudioConstants.FRAME_SIZE_SAMPLES) {
                        // Full 512-sample frame ready
                        val prob = vadDetector.processFrame(frame)
                        val now = System.currentTimeMillis()
                        stateMachine.processFrame(frame, prob, now)

                        // Calculate RMS amplitude in dB and max absolute sample
                        var sumSq = 0.0
                        var maxSample = 0
                        for (sample in frame) {
                            val abs = kotlin.math.abs(sample.toInt())
                            if (abs > maxSample) maxSample = abs
                            sumSq += sample * sample
                        }
                        val rms = sqrt(sumSq / frame.size)
                        val rmsDb = if (rms > 0) (20 * log10(rms / 32768.0)).toFloat() else -100f

                        if (frameCount % 30 == 0) { // Log once per second
                            android.util.Log.d("RecMeAudio", "Mic status: maxSample=$maxSample, rmsDb=${String.format("%.1f", rmsDb)}, prob=${String.format("%.3f", prob)}, state=${stateMachine.currentState}")
                        }
                        frameCount++

                        val bytesPerMs = (AudioConstants.SAMPLE_RATE_HZ * AudioConstants.BYTES_PER_SAMPLE) / 1000L
                        val totalBytes = sessionMgr.getTotalPcmBytes()
                        val durationMs = if (bytesPerMs > 0) totalBytes / bytesPerMs else 0L

                        _engineState.value = _engineState.value.copy(
                            currentVadProbability = prob,
                            currentRmsDb = rmsDb,
                            isSpeechDetected = stateMachine.currentState != VadState.LISTENING,
                            recordedDurationMs = durationMs,
                            totalFileSizeBytes = totalBytes + 44
                        )

                        readOffset = 0
                    }
                } else if (samplesRead < 0) {
                    // AudioRecord read error
                    break
                }
            }
        }
    }

    /**
     * Stops continuous capture and flushes pending writes.
     */
    @Synchronized
    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null

        vadStateMachine?.flushAndReset(System.currentTimeMillis())

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null

        sessionManager?.close()
        sessionManager = null

        _engineState.value = AudioEngineState(isRunning = false)
    }

    override fun close() {
        stopCapture()
        vadDetector.close()
    }
}
