package com.example.recme

import com.example.recme.audio.CircularAudioBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CircularAudioBufferTest {

    @Test
    fun testBufferCapacityAndDrainOrder() {
        val buffer = CircularAudioBuffer(capacity = 3, frameSize = 4)

        val frame1 = shortArrayOf(1, 1, 1, 1)
        val frame2 = shortArrayOf(2, 2, 2, 2)
        val frame3 = shortArrayOf(3, 3, 3, 3)
        val frame4 = shortArrayOf(4, 4, 4, 4)

        buffer.push(frame1)
        buffer.push(frame2)
        assertEquals(2, buffer.size())

        buffer.push(frame3)
        assertEquals(3, buffer.size())

        // Pushing frame4 should evict frame1
        buffer.push(frame4)
        assertEquals(3, buffer.size())

        val drained = buffer.drain()
        assertEquals(3, drained.size)
        assertArrayEquals(frame2, drained[0])
        assertArrayEquals(frame3, drained[1])
        assertArrayEquals(frame4, drained[2])
    }

    @Test
    fun testClear() {
        val buffer = CircularAudioBuffer(capacity = 5, frameSize = 2)
        buffer.push(shortArrayOf(1, 2))
        buffer.push(shortArrayOf(3, 4))
        assertEquals(2, buffer.size())

        buffer.clear()
        assertEquals(0, buffer.size())
        assertEquals(0, buffer.drain().size)
    }
}
