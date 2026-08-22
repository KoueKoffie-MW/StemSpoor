package com.example.recme.ai.speaker

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Extracts normalized 192-dimensional acoustic speaker embeddings from 16kHz PCM audio.
 * Supports on-device ONNX neural embedding models (CAM++ / ResNet34) with an acoustic filterbank fallback.
 */
class SpeakerEmbeddingEngine(private val context: Context) : AutoCloseable {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val embeddingDim = 192

    init {
        tryLoadOnnxModel()
    }

    private fun tryLoadOnnxModel() {
        val modelFile = File(context.filesDir, "models/speaker_embedding.onnx")
        if (modelFile.exists() && modelFile.length() > 100_000) {
            try {
                val env = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                }
                ortEnv = env
                ortSession = env.createSession(modelFile.absolutePath, sessionOptions)
                Log.i(TAG, "Loaded ONNX speaker embedding model: ${modelFile.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load ONNX speaker model, using acoustic extractor fallback", e)
            }
        }
    }

    /**
     * Extracts a 192-d acoustic speaker embedding vector from 16kHz PCM float samples [-1.0, 1.0].
     */
    suspend fun extractEmbedding(samples: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        if (samples.isEmpty()) return@withContext FloatArray(embeddingDim)

        val session = ortSession
        val env = ortEnv

        if (session != null && env != null && samples.size >= 1600) {
            try {
                // Run neural ONNX embedding extraction
                val shape = longArrayOf(1, samples.size.toLong())
                val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), shape)
                val inputName = session.inputNames.firstOrNull() ?: "speech"
                val outputs = session.run(mapOf(inputName to tensor))
                val outputTensor = outputs.get(0) as? OnnxTensor
                if (outputTensor != null) {
                    val rawBuffer = outputTensor.floatBuffer
                    val vec = FloatArray(min(embeddingDim, rawBuffer.remaining()))
                    rawBuffer.get(vec)
                    outputs.close()
                    tensor.close()
                    return@withContext l2Normalize(vec)
                }
                outputs.close()
                tensor.close()
            } catch (e: Exception) {
                Log.w(TAG, "ONNX embedding extraction failed, fallback to acoustic filterbank", e)
            }
        }

        // High-precision acoustic filterbank & spectral timbre extractor
        return@withContext extractAcousticFilterbankEmbedding(samples)
    }

    /**
     * Computes a 192-dimensional acoustic timbre fingerprint from multi-band spectral dynamics,
     * formant harmonic distribution, and energy envelopes over 16kHz audio.
     */
    private fun extractAcousticFilterbankEmbedding(samples: FloatArray): FloatArray {
        val windowSize = 512
        val hopSize = 256
        val numFrames = (samples.size - windowSize) / hopSize
        if (numFrames <= 0) return FloatArray(embeddingDim)

        val numBands = 48
        val bandEnergies = Array(numBands) { FloatArray(numFrames) }
        val window = FloatArray(windowSize) { i ->
            0.54f - 0.46f * cos(2.0 * Math.PI * i / (windowSize - 1)).toFloat()
        }

        val frameBuffer = FloatArray(windowSize)
        val fftBuffer = FloatArray(windowSize)

        for (f in 0 until numFrames) {
            val offset = f * hopSize
            for (i in 0 until windowSize) {
                frameBuffer[i] = samples[offset + i] * window[i]
            }

            // Power spectrum calculation
            val spec = computeMagnitudeSpectrum(frameBuffer)
            val specSize = spec.size // 256

            // Aggregate into 48 non-linear mel/bark sub-bands
            for (b in 0 until numBands) {
                val startIdx = ((b.toDouble() / numBands).pow(1.8) * (specSize - 2)).toInt()
                val endIdx = (((b + 1).toDouble() / numBands).pow(1.8) * (specSize - 1)).toInt().coerceAtLeast(startIdx + 1)
                var bandSum = 0.0f
                for (k in startIdx until min(endIdx, specSize)) {
                    bandSum += spec[k]
                }
                bandEnergies[b][f] = ln(max(1e-6f, bandSum))
            }
        }

        // Aggregate 192-dimensional vector:
        // [0..47]: Mean band energy (fundamental spectral shape)
        // [48..95]: Standard deviation across time (vocal modulation)
        // [96..143]: Temporal delta dynamics (speaking rhythm)
        // [144..191]: Discrete Cosine Transform (DCT cepstral coefficients)
        val embedding = FloatArray(embeddingDim)

        for (b in 0 until numBands) {
            val energies = bandEnergies[b]
            var sum = 0.0f
            for (v in energies) sum += v
            val mean = sum / numFrames
            embedding[b] = mean

            var varSum = 0.0f
            for (v in energies) varSum += (v - mean) * (v - mean)
            val std = sqrt(varSum / numFrames)
            embedding[numBands + b] = std

            // Delta dynamic
            var deltaSum = 0.0f
            for (t in 1 until numFrames) {
                deltaSum += abs(energies[t] - energies[t - 1])
            }
            embedding[numBands * 2 + b] = deltaSum / max(1, numFrames - 1)
        }

        // Cepstral DCT coefficients across subbands
        for (k in 0 until numBands) {
            var dctVal = 0.0f
            for (n in 0 until numBands) {
                dctVal += embedding[n] * cos(Math.PI * k * (n + 0.5) / numBands).toFloat()
            }
            embedding[numBands * 3 + k] = dctVal
        }

        return l2Normalize(embedding)
    }

    private fun computeMagnitudeSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val half = n / 2
        val mag = FloatArray(half)
        // Standard DFT magnitude estimation for window
        for (k in 0 until half) {
            var real = 0.0f
            var imag = 0.0f
            val angle = 2.0 * Math.PI * k / n
            for (t in 0 until n) {
                val a = angle * t
                real += frame[t] * cos(a).toFloat()
                imag -= frame[t] * sin(a).toFloat()
            }
            mag[k] = sqrt(real * real + imag * imag)
        }
        return mag
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sumSq = 0.0f
        for (x in vec) sumSq += x * x
        val norm = sqrt(sumSq)
        if (norm < 1e-8f) return vec
        return FloatArray(vec.size) { i -> vec[i] / norm }
    }

    override fun close() {
        ortSession?.close()
        ortEnv?.close()
    }

    companion object {
        private const val TAG = "SpeakerEmbeddingEngine"
    }
}
