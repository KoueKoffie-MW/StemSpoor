package com.example.recme.ai.whisper

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * High-performance audio preprocessor for OpenAI Whisper (16kHz, 400 FFT, 160 hop, 128 Slaney Mel filterbanks).
 */
class WhisperAudioPreprocessor(
    val sampleRate: Int = 16000,
    val nFft: Int = 400,
    val hopLength: Int = 160,
    val nMels: Int = 128
) {
    // Precomputed Hann window (periodic, size 400)
    private val window: FloatArray = FloatArray(nFft) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / nFft))).toFloat()
    }

    // Precomputed Cos/Sin tables for 400-point DFT to run at blazing speed
    private val halfN = nFft / 2 // 200
    private val cosTable = Array(halfN + 1) { k ->
        FloatArray(nFft) { t -> cos(2.0 * Math.PI * k * t / nFft).toFloat() }
    }
    private val sinTable = Array(halfN + 1) { k ->
        FloatArray(nFft) { t -> sin(2.0 * Math.PI * k * t / nFft).toFloat() }
    }

    private val melFilters: Array<FloatArray> = createSlaneyMelFilterbank(sampleRate, nFft, nMels)

    /**
     * Converts raw 16kHz PCM float samples into a 2D Log Mel-Spectrogram of shape [nMels, 3000].
     */
    fun computeMelSpectrogram(samples: FloatArray, targetFrames: Int = 3000): Array<FloatArray> {
        val pad = nFft / 2 // 200
        val paddedLen = samples.size + 2 * pad
        val paddedSamples = FloatArray(paddedLen)
        System.arraycopy(samples, 0, paddedSamples, pad, samples.size)

        val totalFrames = max(1, (paddedLen - nFft) / hopLength + 1)
        val validFrames = minOf(totalFrames, targetFrames)
        val rawLogMel = Array(nMels) { FloatArray(targetFrames) }

        val frameBuffer = FloatArray(nFft)
        val powerSpectrum = FloatArray(halfN + 1)

        var maxLogEnergy = -100.0f

        for (frameIdx in 0 until validFrames) {
            val startSample = frameIdx * hopLength

            // Apply Hann window
            for (i in 0 until nFft) {
                val s = if (startSample + i < paddedLen) paddedSamples[startSample + i] else 0f
                frameBuffer[i] = s * window[i]
            }

            // Fast DFT using precomputed trig tables (STFT power spectrum: |X[k]|^2 without division by N)
            for (k in 0..halfN) {
                val cRow = cosTable[k]
                val sRow = sinTable[k]
                var real = 0f
                var imag = 0f
                for (t in 0 until nFft) {
                    val s = frameBuffer[t]
                    real += s * cRow[t]
                    imag += s * sRow[t]
                }
                powerSpectrum[k] = real * real + imag * imag
            }

            // Slaney Mel Filterbank matrix multiplication
            for (melIdx in 0 until nMels) {
                var melEnergy = 0f
                val filter = melFilters[melIdx]
                for (k in 0..halfN) {
                    val w = filter[k]
                    if (w > 0f) {
                        melEnergy += w * powerSpectrum[k]
                    }
                }
                val logMel = log10(max(melEnergy, 1e-10f))
                rawLogMel[melIdx][frameIdx] = logMel
                if (logMel > maxLogEnergy) {
                    maxLogEnergy = logMel
                }
            }
        }

        // Whisper Dynamic Range Normalization: clamp to [max - 8.0, max], then (x + 4.0) / 4.0
        val clampedMin = maxLogEnergy - 8.0f
        val melSpectrogram = Array(nMels) { FloatArray(targetFrames) }

        for (melIdx in 0 until nMels) {
            for (frameIdx in 0 until targetFrames) {
                if (frameIdx < validFrames) {
                    val clamped = max(rawLogMel[melIdx][frameIdx], clampedMin)
                    melSpectrogram[melIdx][frameIdx] = (clamped + 4.0f) / 4.0f
                } else {
                    melSpectrogram[melIdx][frameIdx] = (clampedMin + 4.0f) / 4.0f
                }
            }
        }

        return melSpectrogram
    }

    /**
     * Constructs Slaney-normalized triangular Mel filterbanks matching Librosa / PyTorch Whisper.
     */
    private fun createSlaneyMelFilterbank(sampleRate: Int, nFft: Int, nMels: Int): Array<FloatArray> {
        val minFreq = 0.0
        val maxFreq = sampleRate / 2.0 // 8000.0 Hz

        val minMel = hzToSlaneyMel(minFreq)
        val maxMel = hzToSlaneyMel(maxFreq)

        val melPoints = DoubleArray(nMels + 2) { i ->
            minMel + i * (maxMel - minMel) / (nMels + 1)
        }

        val hzPoints = DoubleArray(nMels + 2) { i ->
            slaneyMelToHz(melPoints[i])
        }

        // FFT bin frequencies: k * sampleRate / nFft
        val fftFreqs = DoubleArray(nFft / 2 + 1) { k ->
            k.toDouble() * sampleRate.toDouble() / nFft.toDouble()
        }

        val filters = Array(nMels) { FloatArray(nFft / 2 + 1) }

        for (m in 0 until nMels) {
            val fLower = hzPoints[m]
            val fCenter = hzPoints[m + 1]
            val fUpper = hzPoints[m + 2]

            for (k in 0..(nFft / 2)) {
                val f = fftFreqs[k]
                if (f >= fLower && f <= fCenter && fCenter > fLower) {
                    filters[m][k] = ((f - fLower) / (fCenter - fLower)).toFloat()
                } else if (f >= fCenter && f <= fUpper && fUpper > fCenter) {
                    filters[m][k] = ((fUpper - f) / (fUpper - fCenter)).toFloat()
                }
            }

            // Slaney Area Normalization: 2.0 / (fUpper - fLower)
            val enorm = (2.0 / (fUpper - fLower)).toFloat()
            for (k in 0..(nFft / 2)) {
                filters[m][k] *= enorm
            }
        }

        return filters
    }

    private fun hzToSlaneyMel(hz: Double): Double {
        val minLogHz = 1000.0
        val minLogMel = 15.0
        val logStep = 27.0 / ln(6.4)

        return if (hz < minLogHz) {
            3.0 * hz / 200.0
        } else {
            minLogMel + ln(hz / minLogHz) * logStep
        }
    }

    private fun slaneyMelToHz(mel: Double): Double {
        val minLogHz = 1000.0
        val minLogMel = 15.0
        val logStep = ln(6.4) / 27.0

        return if (mel < minLogMel) {
            200.0 * mel / 3.0
        } else {
            minLogHz * exp((mel - minLogMel) * logStep)
        }
    }
}
