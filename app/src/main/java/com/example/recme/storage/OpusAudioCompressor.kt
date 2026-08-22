package com.example.recme.storage

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import com.example.recme.audio.AudioConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import kotlin.math.min

/**
 * Background post-processor that compresses completed raw WAV recordings into 32 kbps Opus audio.
 * Delivers ~90% disk space reduction while preserving exact millisecond timestamp alignment.
 */
object OpusAudioCompressor {
    private const val TAG = "OpusAudioCompressor"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Compresses a raw WAV file to an Opus/OGG container file using Android MediaCodec + MediaMuxer.
     * On success, updates the sidecar JSON to point to the compressed file and deletes the original WAV.
     *
     * @param wavFile Source WAV file.
     * @param jsonFile Companion sidecar JSON file (optional).
     * @return The compressed File on success, or the original wavFile on failure.
     */
    suspend fun compressWavToOpus(wavFile: File, jsonFile: File?): File = withContext(Dispatchers.IO) {
        if (!wavFile.exists() || wavFile.length() <= 44) {
            Log.w(TAG, "File too small or does not exist: ${wavFile.name}")
            return@withContext wavFile
        }

        val baseName = wavFile.nameWithoutExtension
        val outputOpusFile = File(wavFile.parentFile, "$baseName.opus")
        val tempOutputFile = File(wavFile.parentFile, "$baseName.opus.tmp")

        try {
            Log.i(TAG, "Starting Opus compression for ${wavFile.name} (${wavFile.length() / 1024} KB)...")
            val startTime = System.currentTimeMillis()

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                AudioConstants.SAMPLE_RATE_HZ,
                AudioConstants.CHANNEL_COUNT
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, AudioConstants.OPUS_BITRATE_BPS)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // MediaMuxer OGG format natively supports Opus on Android 10+ (API 29+)
            val muxerFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            }

            val muxer = MediaMuxer(tempOutputFile.absolutePath, muxerFormat)
            var audioTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val inputStream = FileInputStream(wavFile)
            // Skip 44-byte WAV header to feed pure PCM samples
            inputStream.skip(44)

            val pcmChunk = ByteArray(8192)
            var isInputEos = false
            var isOutputEos = false
            var totalBytesRead = 0L

            while (!isOutputEos) {
                // 1. Feed input PCM data to MediaCodec safely respecting inputBuffer capacity
                if (!isInputEos) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(1000L)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val maxToRead = min(inputBuffer.remaining(), pcmChunk.size)
                            val bytesRead = inputStream.read(pcmChunk, 0, maxToRead)
                            if (bytesRead > 0) {
                                inputBuffer.put(pcmChunk, 0, bytesRead)
                                val presentationTimeUs = (totalBytesRead * 1_000_000L) / (AudioConstants.SAMPLE_RATE_HZ * AudioConstants.BYTES_PER_SAMPLE)
                                encoder.queueInputBuffer(inputBufferIndex, 0, bytesRead, presentationTimeUs, 0)
                                totalBytesRead += bytesRead
                            } else {
                                isInputEos = true
                                encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        }
                    }
                }

                // 2. Dequeue encoded Opus packets
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 1000L)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) {
                            throw RuntimeException("Format changed after muxer started")
                        }
                        val newFormat = encoder.outputFormat
                        audioTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputBufferIndex >= 0 -> {
                        val encodedBuffer = encoder.getOutputBuffer(outputBufferIndex)
                        if (encodedBuffer != null) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0 && muxerStarted) {
                                encodedBuffer.position(bufferInfo.offset)
                                encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(audioTrackIndex, encodedBuffer, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outputBufferIndex, false)
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputEos = true
                        }
                    }
                }
            }

            inputStream.close()
            encoder.stop()
            encoder.release()

            if (muxerStarted) {
                muxer.stop()
                muxer.release()
            }

            // Rename temp output to final .opus
            if (tempOutputFile.exists() && tempOutputFile.length() > 0) {
                if (outputOpusFile.exists()) outputOpusFile.delete()
                tempOutputFile.renameTo(outputOpusFile)

                val elapsed = System.currentTimeMillis() - startTime
                val originalKb = wavFile.length() / 1024
                val compressedKb = outputOpusFile.length() / 1024
                val ratio = if (originalKb > 0) (100 - (compressedKb * 100 / originalKb)) else 0
                Log.i(TAG, "Opus compression complete in ${elapsed}ms: ${originalKb}KB -> ${compressedKb}KB ($ratio% space reclaimed)")

                // Update sidecar JSON file_name reference
                if (jsonFile != null && jsonFile.exists()) {
                    try {
                        val content = jsonFile.readText(Charsets.UTF_8)
                        val sidecar = json.decodeFromString<SidecarData>(content)
                        val updatedSidecar = sidecar.copy(fileName = outputOpusFile.name)
                        jsonFile.writeText(json.encodeToString(SidecarData.serializer(), updatedSidecar))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update sidecar JSON fileName", e)
                    }
                }

                // Delete original uncompressed WAV file
                wavFile.delete()
                return@withContext outputOpusFile
            } else {
                Log.e(TAG, "Opus output file was empty or failed to write")
                tempOutputFile.delete()
                return@withContext wavFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing ${wavFile.name} to Opus", e)
            tempOutputFile.delete()
            return@withContext wavFile
        }
    }
}
