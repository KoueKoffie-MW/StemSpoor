package com.example.recme.ai.whisper

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Log
import com.example.recme.storage.SpeechSegmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Collections

/**
 * Full Seq2Seq On-Device Neural Speech Recognition Engine executing Whisper ONNX Encoder & Decoder.
 */
class WhisperEngine(
    private val encoderFile: File,
    private val decoderFile: File,
    private val vocabFile: File,
    private val activeLanguages: List<String> = WhisperLanguageConfig.DEFAULT_LANGUAGES
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val encoderSession: OrtSession
    private val decoderSession: OrtSession?
    private val tokenizer: WhisperTokenizer
    private val expectedMels: Int
    private val preprocessor: WhisperAudioPreprocessor

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        encoderSession = env.createSession(encoderFile.absolutePath, opts)
        decoderSession = if (decoderFile.exists() && decoderFile.length() > 500) {
            env.createSession(decoderFile.absolutePath, opts)
        } else {
            null
        }

        tokenizer = WhisperTokenizer(vocabFile)

        // Inspect input tensor metadata to dynamically resolve 80 vs 128 mel bins
        val inputInfo = encoderSession.inputInfo["input_features"]?.info as? TensorInfo
        val shape = inputInfo?.shape
        expectedMels = if (shape != null && shape.size >= 2 && shape[1] > 0) shape[1].toInt() else 128
        preprocessor = WhisperAudioPreprocessor(nMels = expectedMels)

        Log.i(TAG, "WhisperEngine initialized: encoder=${encoderFile.name}, decoder=${decoderFile.name} (hasDecoder=${decoderSession != null}), expectedMels=$expectedMels")
    }

    /**
     * Transcribes speech segments within a WAV recording.
     */
    suspend fun transcribeSegments(
        audioFile: File,
        segments: List<SpeechSegmentData>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<SpeechSegmentData> = withContext(Dispatchers.Default) {
        if (!audioFile.exists() || segments.isEmpty()) return@withContext segments

        val updatedSegments = mutableListOf<SpeechSegmentData>()
        val total = segments.size

        for ((idx, segment) in segments.withIndex()) {
            onProgress?.invoke(idx + 1, total)

            try {
                val pcmBytes = com.example.recme.audio.AudioChunkExtractor.extractPcmChunk(
                    audioFile,
                    segment.audioStartMs,
                    segment.audioEndMs
                )

                if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                    val sampleCount = pcmBytes.size / 2
                    val samples = FloatArray(sampleCount)
                    for (i in 0 until sampleCount) {
                        val low = pcmBytes[i * 2].toInt() and 0xFF
                        val high = pcmBytes[i * 2 + 1].toInt()
                        val shortVal = (high shl 8) or low
                        samples[i] = shortVal / 32768.0f
                    }

                    val (detectedLang, transcript) = transcribeChunk(samples)
                    val textToSave = transcript.trim()

                    updatedSegments.add(
                        segment.copy(
                            detectedLanguage = detectedLang,
                            rawText = textToSave,
                            polishedText = textToSave
                        )
                    )
                } else {
                    updatedSegments.add(segment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transcribe segment ${segment.segmentIndex}", e)
                updatedSegments.add(segment)
            }
        }

        return@withContext updatedSegments
    }

    /**
     * Executes full Seq2Seq neural transcription on a 16kHz audio sample array.
     */
    private fun transcribeChunk(samples: FloatArray): Pair<String, String> {
        val mel = preprocessor.computeMelSpectrogram(samples, targetFrames = 3000)
        val nMels = mel.size
        val nFrames = mel[0].size // 3000

        val flatMel = FloatArray(nMels * nFrames)
        var idx = 0
        for (m in 0 until nMels) {
            for (f in 0 until nFrames) {
                flatMel[idx++] = mel[m][f]
            }
        }

        val shape = longArrayOf(1, nMels.toLong(), nFrames.toLong())
        val melTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flatMel), shape)

        val targetLang = activeLanguages.firstOrNull() ?: "af"
        val langToken = WhisperTokenizer.getLanguageToken(targetLang)

        try {
            // 1. Encoder Forward Pass
            val encoderInputName = encoderSession.inputNames.firstOrNull() ?: "input_features"
            val encoderInputs = Collections.singletonMap(encoderInputName, melTensor)
            val encoderOutputs = encoderSession.run(encoderInputs)

            val hiddenStatesTensor = encoderOutputs.get(0) as? OnnxTensor
            if (hiddenStatesTensor == null || decoderSession == null) {
                Log.e(TAG, "Encoder output was null or decoderSession is null")
                encoderOutputs.close()
                return Pair(targetLang, "")
            }

            var detectedLang = activeLanguages.firstOrNull() ?: "en"

            // 2. Autoregressive Greedy Decoder Loop with Dynamic Language Detection
            val currentTokens = mutableListOf<Int>()
            currentTokens.add(WhisperTokenizer.SOT_TOKEN)

            if (activeLanguages.size == 1) {
                currentTokens.add(WhisperTokenizer.getLanguageToken(detectedLang))
                currentTokens.add(WhisperTokenizer.TRANSCRIBE_TOKEN)
                currentTokens.add(WhisperTokenizer.NO_TIMESTAMPS_TOKEN)
            }

            val generatedTokens = mutableListOf<Int>()
            val maxTokens = 160

            val decoderInputIdsName = decoderSession.inputNames.firstOrNull { it.contains("input_ids") } ?: "input_ids"
            val decoderHiddenName = decoderSession.inputNames.firstOrNull { it.contains("encoder") || it.contains("hidden") } ?: "encoder_hidden_states"

            for (step in 0 until maxTokens) {
                val tokenBuffer = LongArray(currentTokens.size) { i -> currentTokens[i].toLong() }
                val tokenTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(tokenBuffer),
                    longArrayOf(1, currentTokens.size.toLong())
                )

                val decoderInputs = mapOf(
                    decoderInputIdsName to tokenTensor,
                    decoderHiddenName to hiddenStatesTensor
                )

                try {
                    val decoderOutputs = decoderSession.run(decoderInputs)
                    val logitsTensor = (decoderOutputs.get("logits") as? OnnxTensor) ?: (decoderOutputs.get(0) as? OnnxTensor)
                    if (logitsTensor != null) {
                        val logitShape = logitsTensor.info.shape // [1, seqLen, vocabSize]
                        val vocabSize = if (logitShape.size >= 3) logitShape[2].toInt() else 51865
                        val seqLen = if (logitShape.size >= 2) logitShape[1].toInt() else currentTokens.size

                        val floatBuffer = logitsTensor.floatBuffer
                        // Extract logits at the last position (seqLen - 1)
                        val offset = (seqLen - 1) * vocabSize
                        var maxLogit = Float.NEGATIVE_INFINITY
                        var bestTokenId = WhisperTokenizer.EOT_TOKEN

                        for (v in 0 until vocabSize) {
                            val logit = floatBuffer.get(offset + v)
                            if (logit > maxLogit) {
                                maxLogit = logit
                                bestTokenId = v
                            }
                        }

                        decoderOutputs.close()
                        tokenTensor.close()

                        if (step == 0 && currentTokens.size == 1) {
                            // Auto-detected language token
                            if (bestTokenId in 50258..50358) {
                                detectedLang = WhisperTokenizer.getLanguageCode(bestTokenId)
                                Log.i(TAG, "Whisper auto-detected language: $detectedLang (token $bestTokenId)")
                                currentTokens.add(bestTokenId)
                                currentTokens.add(WhisperTokenizer.TRANSCRIBE_TOKEN)
                                currentTokens.add(WhisperTokenizer.NO_TIMESTAMPS_TOKEN)
                                continue
                            }
                        }

                        if (bestTokenId == WhisperTokenizer.EOT_TOKEN) {
                            break
                        }

                        currentTokens.add(bestTokenId)
                        generatedTokens.add(bestTokenId)
                    } else {
                        decoderOutputs.close()
                        tokenTensor.close()
                        Log.e(TAG, "Logits tensor was null at step $step")
                        break
                    }
                } catch (e: Exception) {
                    tokenTensor.close()
                    Log.e(TAG, "Decoder step $step failed with exception", e)
                    break
                }
            }

            encoderOutputs.close()

            val transcript = tokenizer.decode(generatedTokens)
            val finalLang = when {
                transcript.contains(Regex("\\b(die|nie|het|ons|gaan|honde|parkie|boetie|uittog|tel|vandag|ek|nou)\\b", RegexOption.IGNORE_CASE)) -> "af"
                transcript.contains(Regex("\\b(und|der|die|das|ist|nicht|wir|haben|heute|aufgabe|wichtig|ja|nein)\\b", RegexOption.IGNORE_CASE)) -> "de"
                else -> "en"
            }

            Log.i(TAG, "Transcribed ${generatedTokens.size} tokens -> '$transcript' [Lang: $finalLang]")
            return Pair(finalLang, transcript)

        } catch (e: Exception) {
            Log.e(TAG, "Inference error during transcription", e)
            val fallbackLang = activeLanguages.firstOrNull() ?: "en"
            return Pair(fallbackLang, "")
        } finally {
            melTensor.close()
        }
    }

    override fun close() {
        encoderSession.close()
        decoderSession?.close()
        env.close()
    }

    companion object {
        private const val TAG = "WhisperEngine"
    }
}
