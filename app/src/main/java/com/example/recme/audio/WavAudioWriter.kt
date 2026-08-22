package com.example.recme.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance streaming RIFF/WAV writer with atomic header patching.
 * Compliant with standard 16-bit PCM Mono 16 kHz audio specifications.
 */
class WavAudioWriter(
    private val sampleRate: Int = AudioConstants.SAMPLE_RATE_HZ,
    private val channels: Short = AudioConstants.CHANNEL_COUNT.toShort(),
    private val bitsPerSample: Short = AudioConstants.BITS_PER_SAMPLE.toShort()
) : AutoCloseable {

    private var randomAccessFile: RandomAccessFile? = null
    var totalPcmBytesWritten: Long = 0L
        private set

    private val headerBuffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    private val pcmByteBuffer = ByteBuffer.allocateDirect(AudioConstants.FRAME_SIZE_SAMPLES * 2)
        .order(ByteOrder.LITTLE_ENDIAN)

    /**
     * Opens or creates a WAV file and writes the initial 44-byte RIFF header.
     */
    @Synchronized
    fun open(file: File) {
        close()
        file.parentFile?.mkdirs()
        randomAccessFile = RandomAccessFile(file, "rw").apply {
            setLength(0) // Truncate if existing
            write(buildHeader(0))
        }
        totalPcmBytesWritten = 0L
    }

    /**
     * Streams PCM 16-bit audio samples to disk.
     */
    @Synchronized
    fun writeSamples(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        val raf = randomAccessFile ?: return
        var remaining = length
        var currentOffset = offset

        while (remaining > 0) {
            val chunkLength = minOf(remaining, AudioConstants.FRAME_SIZE_SAMPLES)
            pcmByteBuffer.clear()
            for (i in currentOffset until (currentOffset + chunkLength)) {
                pcmByteBuffer.putShort(samples[i])
            }
            pcmByteBuffer.flip()

            val bytesCount = chunkLength * 2
            val tempBytes = ByteArray(bytesCount)
            pcmByteBuffer.get(tempBytes)
            raf.write(tempBytes)

            totalPcmBytesWritten += bytesCount
            currentOffset += chunkLength
            remaining -= chunkLength
        }
    }

    /**
     * Flushes the latest byte counts to the 44-byte RIFF header on disk without closing the stream.
     */
    @Synchronized
    fun flushHeader() {
        val raf = randomAccessFile ?: return
        val currentPosition = raf.filePointer
        raf.seek(0)
        raf.write(buildHeader(totalPcmBytesWritten))
        raf.seek(currentPosition)
    }

    /**
     * Builds standard 44-byte RIFF header byte array for given PCM data length.
     */
    private fun buildHeader(pcmDataLength: Long): ByteArray {
        headerBuffer.clear()
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        // RIFF chunk descriptor
        headerBuffer.put('R'.code.toByte())
        headerBuffer.put('I'.code.toByte())
        headerBuffer.put('F'.code.toByte())
        headerBuffer.put('F'.code.toByte())
        headerBuffer.putInt(totalDataLen.toInt())
        headerBuffer.put('W'.code.toByte())
        headerBuffer.put('A'.code.toByte())
        headerBuffer.put('V'.code.toByte())
        headerBuffer.put('E'.code.toByte())

        // fmt sub-chunk
        headerBuffer.put('f'.code.toByte())
        headerBuffer.put('m'.code.toByte())
        headerBuffer.put('t'.code.toByte())
        headerBuffer.put(' '.code.toByte())
        headerBuffer.putInt(16) // SubChunk1Size for PCM
        headerBuffer.putShort(1) // AudioFormat 1 = PCM
        headerBuffer.putShort(channels)
        headerBuffer.putInt(sampleRate)
        headerBuffer.putInt(byteRate)
        headerBuffer.putShort(blockAlign)
        headerBuffer.putShort(bitsPerSample)

        // data sub-chunk
        headerBuffer.put('d'.code.toByte())
        headerBuffer.put('a'.code.toByte())
        headerBuffer.put('t'.code.toByte())
        headerBuffer.put('a'.code.toByte())
        headerBuffer.putInt(pcmDataLength.toInt())

        return headerBuffer.array()
    }

    /**
     * Flushes final header and cleanly closes the file handle.
     */
    @Synchronized
    override fun close() {
        randomAccessFile?.let { raf ->
            try {
                flushHeader()
                raf.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        randomAccessFile = null
    }

    companion object {
        /**
         * Repairs an unfinalized WAV file on startup by inspecting actual file length and rewriting header.
         */
        fun repairHeaderIfCorrupt(file: File): Boolean {
            if (!file.exists() || file.length() < 44) return false
            try {
                RandomAccessFile(file, "rw").use { raf ->
                    val actualPcmBytes = file.length() - 44
                    val writer = WavAudioWriter()
                    raf.seek(0)
                    raf.write(writer.buildHeader(actualPcmBytes))
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }
}
