package com.example.recme.ai.speaker

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Utility for capturing high-fidelity 16kHz PCM audio samples directly from the microphone
 * to extract acoustic speaker embeddings for enrollment.
 */
class SpeakerVoiceRecorder(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun recordSampleAndExtractEmbedding(
        durationMs: Long = 3500L,
        onProgress: ((remainingSec: Int) -> Unit)? = null
    ): Result<FloatArray> = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return@withContext Result.failure(IllegalStateException("AudioRecord initialization failed"))
        }

        val totalSamples = (sampleRate * (durationMs / 1000.0f)).toInt()
        val floatSamples = FloatArray(totalSamples)
        val shortBuffer = ShortArray(1024)
        var samplesReadTotal = 0

        val startTime = System.currentTimeMillis()
        try {
            audioRecord.startRecording()
            while (samplesReadTotal < totalSamples && (System.currentTimeMillis() - startTime) < (durationMs + 1000)) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read > 0) {
                    val toCopy = minOf(read, totalSamples - samplesReadTotal)
                    for (i in 0 until toCopy) {
                        floatSamples[samplesReadTotal + i] = shortBuffer[i] / 32768.0f
                    }
                    samplesReadTotal += toCopy
                }

                val elapsed = System.currentTimeMillis() - startTime
                val remainingSec = maxOf(0, ((durationMs - elapsed) / 1000).toInt())
                onProgress?.invoke(remainingSec)
                delay(20)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            try {
                audioRecord.stop()
                audioRecord.release()
            } catch (_: Exception) {}
        }

        if (samplesReadTotal < 8000) {
            return@withContext Result.failure(IllegalStateException("Recorded sample too short"))
        }

        val embeddingEngine = SpeakerEmbeddingEngine(context)
        return@withContext try {
            val validSlice = floatSamples.copyOfRange(0, samplesReadTotal)
            val embedding = embeddingEngine.extractEmbedding(validSlice)
            Result.success(embedding)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            embeddingEngine.close()
        }
    }
}
