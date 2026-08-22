package com.example.recme.ai.voicegate

import com.example.recme.audio.AudioConstants

/**
 * 10-second circular rolling verification buffer for Voice Gate.
 * Holds active speech frames in memory until speaker verification succeeds or segment ends.
 *
 * @param maxSeconds Buffer capacity in seconds (default 10 seconds).
 * @param frameSize Number of samples per frame (default 512).
 */
class VerificationAudioBuffer(
    val maxSeconds: Int = 10,
    val frameSize: Int = AudioConstants.FRAME_SIZE_SAMPLES
) {
    // 16000 samples/sec / 512 samples/frame = 31.25 frames/sec -> 320 frames for 10.24 seconds
    val capacity: Int = (maxSeconds * AudioConstants.SAMPLE_RATE_HZ + frameSize - 1) / frameSize

    private val buffer: Array<ShortArray> = Array(capacity) { ShortArray(frameSize) }
    private var head: Int = 0
    private var count: Int = 0

    @Synchronized
    fun push(frame: ShortArray) {
        System.arraycopy(frame, 0, buffer[head], 0, frameSize)
        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    @Synchronized
    fun pushAll(frames: List<ShortArray>) {
        for (f in frames) {
            push(f)
        }
    }

    /**
     * Converts all stored PCM frames in chronological order to a normalized float array [-1.0f, 1.0f].
     */
    @Synchronized
    fun toFloatArray(): FloatArray {
        val totalSamples = count * frameSize
        val result = FloatArray(totalSamples)
        val start = if (count < capacity) 0 else head
        var destOffset = 0

        for (i in 0 until count) {
            val idx = (start + i) % capacity
            val frame = buffer[idx]
            for (s in frame) {
                result[destOffset++] = s / 32768.0f
            }
        }
        return result
    }

    /**
     * Drains all frames chronologically to commit them to the recording session on disk.
     */
    @Synchronized
    fun drain(): List<ShortArray> {
        val result = ArrayList<ShortArray>(count)
        val start = if (count < capacity) 0 else head
        for (i in 0 until count) {
            val idx = (start + i) % capacity
            result.add(buffer[idx].clone())
        }
        clear()
        return result
    }

    @Synchronized
    fun getDurationMs(): Long {
        return (count * frameSize * 1000L) / AudioConstants.SAMPLE_RATE_HZ
    }

    @Synchronized
    fun getFrameCount(): Int = count

    @Synchronized
    fun clear() {
        head = 0
        count = 0
    }
}
