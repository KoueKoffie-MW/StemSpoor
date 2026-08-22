package com.example.recme.audio

/**
 * Pre-allocated circular ring buffer for caching audio frames for pre-roll without heap churn.
 *
 * @param capacity Number of frames the ring buffer can hold (e.g. 19 frames for ~608 ms).
 * @param frameSize Number of audio samples per frame (e.g. 512 samples).
 */
class CircularAudioBuffer(
    val capacity: Int = AudioConstants.BUFFER_FRAME_COUNT,
    val frameSize: Int = AudioConstants.FRAME_SIZE_SAMPLES
) {
    private val buffer: Array<ShortArray> = Array(capacity) { ShortArray(frameSize) }
    private var head: Int = 0
    private var count: Int = 0

    /**
     * Pushes a new frame into the circular buffer in-place without memory allocation.
     */
    @Synchronized
    fun push(frame: ShortArray, offset: Int = 0) {
        System.arraycopy(frame, offset, buffer[head], 0, frameSize)
        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    /**
     * Drains all currently buffered frames in chronological order (oldest to newest).
     * @return List of cloned ShortArrays representing the buffered pre-roll audio.
     */
    @Synchronized
    fun drain(): List<ShortArray> {
        val result = ArrayList<ShortArray>(count)
        val start = if (count < capacity) 0 else head
        for (i in 0 until count) {
            val idx = (start + i) % capacity
            result.add(buffer[idx].clone())
        }
        return result
    }

    /**
     * Returns the current number of frames stored in the buffer.
     */
    @Synchronized
    fun size(): Int = count

    /**
     * Resets the buffer head and frame count to empty.
     */
    @Synchronized
    fun clear() {
        head = 0
        count = 0
    }
}
