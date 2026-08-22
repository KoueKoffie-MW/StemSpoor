package com.example.recme.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Memory-safe audio chunk extractor that retrieves raw 16kHz Mono PCM for any given time slice
 * from either uncompressed WAV files or compressed Opus/OGG containers.
 */
object AudioChunkExtractor {
    private const val TAG = "AudioChunkExtractor"
    private const val BYTES_PER_MS_16KHZ_16BIT = 32 // 16,000 samples/s * 2 bytes/sample / 1000 ms

    /**
     * Extracts raw PCM bytes for a specific segment [startMs, endMs].
     */
    suspend fun extractPcmChunk(
        audioFile: File,
        startMs: Long,
        endMs: Long
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || startMs >= endMs) return@withContext null

        // Check if source or sibling WAV file exists
        val wavFile = if (audioFile.name.endsWith(".wav", ignoreCase = true)) {
            audioFile
        } else {
            val siblingWav = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".wav")
            if (siblingWav.exists()) siblingWav else null
        }

        if (wavFile != null) {
            return@withContext extractFromWav(wavFile, startMs, endMs)
        }

        // Decode from compressed Opus / OGG file
        return@withContext decodeFromMediaFile(audioFile, startMs, endMs)
    }

    /**
     * Slices PCM bytes directly from WAV using zero-copy RandomAccessFile seeking.
     */
    private fun extractFromWav(wavFile: File, startMs: Long, endMs: Long): ByteArray? {
        return try {
            val startByte = 44L + (startMs * BYTES_PER_MS_16KHZ_16BIT)
            val durationMs = endMs - startMs
            val totalBytes = (durationMs * BYTES_PER_MS_16KHZ_16BIT).toInt()

            RandomAccessFile(wavFile, "r").use { raf ->
                if (startByte < raf.length() && totalBytes > 0) {
                    val actualBytesToRead = minOf(totalBytes.toLong(), raf.length() - startByte).toInt()
                    raf.seek(startByte)
                    val pcmBytes = ByteArray(actualBytesToRead)
                    raf.readFully(pcmBytes)
                    pcmBytes
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract WAV chunk from ${wavFile.name}", e)
            null
        }
    }

    /**
     * Decodes a specific time window [startMs, endMs] from an Opus/OGG container to raw 16kHz Mono PCM.
     */
    private fun decodeFromMediaFile(file: File, startMs: Long, endMs: Long): ByteArray? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val outputPcmShorts = ArrayList<Short>()

        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = f
                    break
                }
            }

            if (audioTrackIndex == -1 || trackFormat == null) {
                Log.e(TAG, "No audio track found in ${file.name}")
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(trackFormat, null, null, 0)
            decoder.start()

            var outputSampleRate = 16000
            var outputChannels = 1

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEos = false
            var isOutputEos = false
            val timeoutUs = 5000L
            var totalSamplesDecoded = 0L

            while (!isOutputEos) {
                if (!isInputEos) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEos = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = decoder.outputFormat
                        outputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 16000)
                        outputChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                        Log.d(TAG, "Decoder output format: ${outputSampleRate}Hz, $outputChannels channels")
                    }
                    outIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val numShorts = shortBuf.remaining()

                            val samplesInThisBuffer = numShorts / outputChannels
                            val bufStartMs = (totalSamplesDecoded * 1000L) / outputSampleRate
                            val bufEndMs = ((totalSamplesDecoded + samplesInThisBuffer) * 1000L) / outputSampleRate

                            if (bufEndMs >= startMs && bufStartMs <= endMs) {
                                for (s in 0 until samplesInThisBuffer) {
                                    val currentSampleMs = ((totalSamplesDecoded + s) * 1000L) / outputSampleRate
                                    if (currentSampleMs in startMs..endMs) {
                                        // Downmix to mono if stereo
                                        val sampleVal: Short = if (outputChannels == 2) {
                                            val left = shortBuf.get(s * 2).toInt()
                                            val right = shortBuf.get(s * 2 + 1).toInt()
                                            ((left + right) / 2).toShort()
                                        } else {
                                            shortBuf.get(s)
                                        }
                                        outputPcmShorts.add(sampleVal)
                                    }
                                }
                            }

                            totalSamplesDecoded += samplesInThisBuffer

                            // Early stop once we pass endMs
                            if (bufStartMs > endMs + 500L) {
                                isOutputEos = true
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputEos = true
                        }
                    }
                }
            }

            if (outputPcmShorts.isEmpty()) {
                Log.w(TAG, "No PCM samples extracted for [${startMs}–${endMs}ms] from ${file.name}")
                return null
            }

            // Resample to 16,000 Hz if decoded at 48,000 Hz or other sample rate
            val resampledShorts = if (outputSampleRate != 16000) {
                resampleAudio(outputPcmShorts, outputSampleRate, 16000)
            } else {
                outputPcmShorts.toShortArray()
            }

            val pcmBytes = ByteArray(resampledShorts.size * 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampledShorts)
            Log.d(TAG, "Extracted ${pcmBytes.size} bytes (16kHz mono) for [${startMs}–${endMs}ms] from ${file.name}")
            return pcmBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio from ${file.name}", e)
            return null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * High-quality linear resampling from sourceSampleRate to targetSampleRate (e.g. 48kHz -> 16kHz).
     */
    private fun resampleAudio(input: List<Short>, sourceRate: Int, targetRate: Int): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        if (sourceRate == targetRate) return input.toShortArray()

        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val outputLength = (input.size / ratio).toInt()
        val output = ShortArray(outputLength)

        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex

            val s1 = input.getOrElse(srcIndex) { input.last() }.toInt()
            val s2 = input.getOrElse(srcIndex + 1) { input.last() }.toInt()
            val interpolated = (s1 + frac * (s2 - s1)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[i] = interpolated.toShort()
        }

        return output
    }

    /**
     * Wraps raw 16kHz mono 16-bit PCM bytes in a standard 44-byte WAV header.
     */
    fun createWavContainer(pcmData: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val totalDataLen = pcmData.size + 36
        val sampleRate = 16000
        val channels = 1
        val byteRate = sampleRate * channels * 2

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size
        header.putShort(1.toShort()) // AudioFormat PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }
}
