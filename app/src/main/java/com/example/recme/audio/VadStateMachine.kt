package com.example.recme.audio

/**
 * Lifecycle states of the Voice Activity Detection audio loop.
 */
enum class VadState {
    LISTENING,
    RECORDING,
    POST_ROLL
}

/**
 * Callbacks emitted by the VAD State Machine to drive the audio writer and metadata sidecars.
 */
interface VadStateListener {
    fun onSegmentStarted(
        preRollFrames: List<ShortArray>,
        speechStartEpochMs: Long,
        preRollMs: Long
    )
    fun onFrameToRecord(frame: ShortArray)
    fun onSegmentEnded(
        speechEndEpochMs: Long,
        postRollMs: Long
    )
    fun onSilenceTimeout()
}

/**
 * Deterministic state machine managing pre-roll ring buffer flushes, speech tracking,
 * and post-roll hangover timers.
 */
class VadStateMachine(
    private val preRollBuffer: CircularAudioBuffer = CircularAudioBuffer(),
    private var threshold: Float = AudioConstants.DEFAULT_VAD_THRESHOLD,
    private val listener: VadStateListener
) {
    var currentState: VadState = VadState.LISTENING
        private set

    private var postRollFrameCount: Int = 0
    private var silenceDurationMs: Long = 0L
    private var speechStartEpochMs: Long = 0L

    fun setThreshold(newThreshold: Float) {
        threshold = newThreshold.coerceIn(0.1f, 0.95f)
    }

    fun getThreshold(): Float = threshold

    /**
     * Process an incoming audio frame with its calculated speech probability and wall-clock timestamp.
     */
    @Synchronized
    fun processFrame(frame: ShortArray, prob: Float, currentEpochMs: Long) {
        val isSpeech = prob >= threshold

        when (currentState) {
            VadState.LISTENING -> {
                if (isSpeech) {
                    // Transition to RECORDING
                    currentState = VadState.RECORDING
                    silenceDurationMs = 0L
                    speechStartEpochMs = currentEpochMs
                    val preRollFrames = preRollBuffer.drain()
                    val actualPreRollMs = (preRollFrames.size * AudioConstants.FRAME_DURATION_MS).toLong()

                    listener.onSegmentStarted(
                        preRollFrames = preRollFrames,
                        speechStartEpochMs = speechStartEpochMs,
                        preRollMs = actualPreRollMs
                    )
                    listener.onFrameToRecord(frame)
                } else {
                    // Keep caching in ring buffer
                    preRollBuffer.push(frame)
                    silenceDurationMs += AudioConstants.FRAME_DURATION_MS
                    if (silenceDurationMs >= AudioConstants.MAX_SILENCE_RESET_MS) {
                        listener.onSilenceTimeout()
                        silenceDurationMs = 0L
                    }
                }
            }

            VadState.RECORDING -> {
                if (isSpeech) {
                    listener.onFrameToRecord(frame)
                } else {
                    // Transition to POST_ROLL hangover
                    currentState = VadState.POST_ROLL
                    postRollFrameCount = 1
                    listener.onFrameToRecord(frame)
                }
            }

            VadState.POST_ROLL -> {
                if (isSpeech) {
                    // Speech resumed during post-roll -> return to RECORDING
                    currentState = VadState.RECORDING
                    postRollFrameCount = 0
                    listener.onFrameToRecord(frame)
                } else {
                    postRollFrameCount++
                    listener.onFrameToRecord(frame)

                    if (postRollFrameCount >= AudioConstants.BUFFER_FRAME_COUNT) {
                        // Post-roll ended -> finalize segment
                        val postRollMs = (postRollFrameCount * AudioConstants.FRAME_DURATION_MS).toLong()
                        val speechEndEpochMs = currentEpochMs - postRollMs

                        currentState = VadState.LISTENING
                        preRollBuffer.clear()
                        silenceDurationMs = 0L

                        listener.onSegmentEnded(
                            speechEndEpochMs = speechEndEpochMs,
                            postRollMs = postRollMs
                        )
                    }
                }
            }
        }
    }

    /**
     * Forcefully flushes and ends any active recording or post-roll segment upon shutdown.
     */
    @Synchronized
    fun flushAndReset(currentEpochMs: Long) {
        if (currentState != VadState.LISTENING) {
            val postRollMs = (postRollFrameCount * AudioConstants.FRAME_DURATION_MS).toLong()
            listener.onSegmentEnded(
                speechEndEpochMs = currentEpochMs,
                postRollMs = postRollMs
            )
        }
        currentState = VadState.LISTENING
        preRollBuffer.clear()
        postRollFrameCount = 0
        silenceDurationMs = 0L
    }
}
