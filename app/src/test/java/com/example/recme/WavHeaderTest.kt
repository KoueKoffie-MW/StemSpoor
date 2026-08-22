package com.example.recme

import com.example.recme.audio.WavAudioWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWavHeaderByteAccuracy() {
        val testFile = tempFolder.newFile("test.wav")
        val writer = WavAudioWriter()
        writer.open(testFile)

        // Write 16000 samples (1 second = 32000 bytes)
        val samples = ShortArray(16000) { (it % 1000).toShort() }
        writer.writeSamples(samples)
        writer.close()

        assertEquals(32044L, testFile.length())

        val bytes = testFile.readBytes()
        assertEquals(32044, bytes.size)

        // Check "RIFF"
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])

        // Check ChunkSize (total size - 8 = 32036)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkSize = buffer.getInt(4)
        assertEquals(32036, chunkSize)

        // Check "WAVE"
        assertEquals('W'.code.toByte(), bytes[8])
        assertEquals('A'.code.toByte(), bytes[9])
        assertEquals('V'.code.toByte(), bytes[10])
        assertEquals('E'.code.toByte(), bytes[11])

        // Check "fmt "
        assertEquals('f'.code.toByte(), bytes[12])
        assertEquals('m'.code.toByte(), bytes[13])
        assertEquals('t'.code.toByte(), bytes[14])
        assertEquals(' '.code.toByte(), bytes[15])

        // AudioFormat = 1 (PCM), Channels = 1, SampleRate = 16000, ByteRate = 32000
        assertEquals(1.toShort(), buffer.getShort(20))
        assertEquals(1.toShort(), buffer.getShort(22))
        assertEquals(16000, buffer.getInt(24))
        assertEquals(32000, buffer.getInt(28))
        assertEquals(2.toShort(), buffer.getShort(32)) // BlockAlign
        assertEquals(16.toShort(), buffer.getShort(34)) // BitsPerSample

        // "data" chunk
        assertEquals('d'.code.toByte(), bytes[36])
        assertEquals('a'.code.toByte(), bytes[37])
        assertEquals('t'.code.toByte(), bytes[38])
        assertEquals('a'.code.toByte(), bytes[39])

        // Subchunk2Size = 32000
        val subchunk2Size = buffer.getInt(40)
        assertEquals(32000, subchunk2Size)
    }

    @Test
    fun testRepairCorruptHeader() {
        val testFile = tempFolder.newFile("corrupt.wav")
        RandomAccessFile(testFile, "rw").use { raf ->
            raf.setLength(64044L) // 64000 PCM bytes + 44 bytes header initialized to 0
        }

        val success = WavAudioWriter.repairHeaderIfCorrupt(testFile)
        assertTrue(success)

        val bytes = testFile.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64036, buffer.getInt(4)) // ChunkSize
        assertEquals(64000, buffer.getInt(40)) // Subchunk2Size
    }
}
