package com.example.recme.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-device Silero VAD inference engine utilizing ONNX Runtime.
 * Implements Silero VAD v5 streaming architecture with 64-sample context buffer and 576-sample effective window.
 */
class SileroVadDetector(context: Context) : AutoCloseable {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // Silero VAD v5 constants (16 kHz)
    private val contextSize: Int = 64
    private val totalWindowSize: Int = AudioConstants.FRAME_SIZE_SAMPLES + contextSize // 512 + 64 = 576
    private val contextBuffer = FloatArray(contextSize)

    // Pre-allocated direct NIO buffers (zero allocation per frame)
    private val inputBuffer: FloatBuffer = ByteBuffer.allocateDirect(totalWindowSize * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val srBuffer: LongBuffer = ByteBuffer.allocateDirect(8)
        .order(ByteOrder.nativeOrder())
        .asLongBuffer().apply {
            put(AudioConstants.SAMPLE_RATE_HZ.toLong())
            flip()
        }

    // Model version detection flags
    private val isV5: Boolean
    private val stateShape: LongArray
    private var stateBuffer: FloatBuffer

    // For v4 fallback (h, c tensors)
    private var hBuffer: FloatBuffer? = null
    private var cBuffer: FloatBuffer? = null

    init {
        val modelBytes = readAssetBytes(context, "silero_vad.onnx")
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
        }
        session = ortEnv.createSession(modelBytes, sessionOptions)

        val inputNames = session.inputNames
        Log.i(TAG, "ONNX VAD Loaded. Inputs: $inputNames, Outputs: ${session.outputNames}")

        if (inputNames.contains("state")) {
            // Silero VAD v5
            isV5 = true
            stateShape = longArrayOf(2, 1, 128)
            stateBuffer = ByteBuffer.allocateDirect((2 * 1 * 128) * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        } else {
            // Silero VAD v4 fallback
            isV5 = false
            stateShape = longArrayOf(2, 1, 64)
            stateBuffer = ByteBuffer.allocateDirect((2 * 1 * 64) * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            hBuffer = ByteBuffer.allocateDirect((2 * 1 * 64) * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            cBuffer = ByteBuffer.allocateDirect((2 * 1 * 64) * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        }
        resetState()
    }

    /**
     * Evaluates a 512-sample PCM frame and returns the speech probability [0.0, 1.0].
     *
     * @param pcmSamples 16-bit PCM mono samples (512 length).
     * @return Speech probability scalar.
     */
    @Synchronized
    fun processFrame(pcmSamples: ShortArray): Float {
        inputBuffer.clear()

        if (isV5) {
            // 1. Prepend 64 context samples from previous chunk (Silero v5 streaming requirement)
            for (i in 0 until contextSize) {
                inputBuffer.put(contextBuffer[i])
            }
            // 2. Append 512 samples from current chunk normalized to [-1.0, 1.0]
            for (i in 0 until AudioConstants.FRAME_SIZE_SAMPLES) {
                inputBuffer.put(pcmSamples[i] / 32768.0f)
            }
            inputBuffer.flip()

            // 3. Save last 64 samples of current chunk as context for the next frame
            val contextOffset = AudioConstants.FRAME_SIZE_SAMPLES - contextSize
            for (i in 0 until contextSize) {
                contextBuffer[i] = pcmSamples[contextOffset + i] / 32768.0f
            }
        } else {
            // v4: Standard 512 samples without external context prepending
            for (i in 0 until AudioConstants.FRAME_SIZE_SAMPLES) {
                inputBuffer.put(pcmSamples[i] / 32768.0f)
            }
            inputBuffer.flip()
        }

        srBuffer.rewind()

        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            inputBuffer,
            longArrayOf(1, if (isV5) totalWindowSize.toLong() else AudioConstants.FRAME_SIZE_SAMPLES.toLong())
        )
        val srTensor = OnnxTensor.createTensor(
            ortEnv,
            srBuffer,
            longArrayOf(1)
        )

        return try {
            if (isV5) {
                stateBuffer.rewind()
                val stateTensor = OnnxTensor.createTensor(ortEnv, stateBuffer, stateShape)

                val inputs = mapOf(
                    "input" to inputTensor,
                    "sr" to srTensor,
                    "state" to stateTensor
                )

                session.run(inputs).use { results ->
                    // Extract probability output safely by name or index 0
                    val outputValue = if (results.get("output").isPresent) {
                        results.get("output").get()
                    } else {
                        results.get(0).value
                    }
                    val outputTensor = outputValue as OnnxTensor
                    val prob = outputTensor.floatBuffer.get(0)

                    // Extract updated recurrent state safely by name or index 1
                    val nextStateValue = if (results.get("state").isPresent) {
                        results.get("state").get()
                    } else if (results.get("stateN").isPresent) {
                        results.get("stateN").get()
                    } else if (results.size() > 1) {
                        results.get(1).value
                    } else null

                    if (nextStateValue is OnnxTensor) {
                        stateBuffer.clear()
                        stateBuffer.put(nextStateValue.floatBuffer)
                        stateBuffer.flip()
                    }

                    stateTensor.close()
                    prob
                }
            } else {
                val h = hBuffer!!
                val c = cBuffer!!
                h.rewind()
                c.rewind()
                val hTensor = OnnxTensor.createTensor(ortEnv, h, longArrayOf(2, 1, 64))
                val cTensor = OnnxTensor.createTensor(ortEnv, c, longArrayOf(2, 1, 64))

                val inputs = mapOf(
                    "input" to inputTensor,
                    "sr" to srTensor,
                    "h" to hTensor,
                    "c" to cTensor
                )

                session.run(inputs).use { results ->
                    val outputValue = if (results.get("output").isPresent) {
                        results.get("output").get()
                    } else {
                        results.get(0).value
                    }
                    val outputTensor = outputValue as OnnxTensor
                    val prob = outputTensor.floatBuffer.get(0)

                    if (results.get("hn").isPresent) {
                        val hnTensor = results.get("hn").get() as OnnxTensor
                        h.clear()
                        h.put(hnTensor.floatBuffer)
                        h.flip()
                    }
                    if (results.get("cn").isPresent) {
                        val cnTensor = results.get("cn").get() as OnnxTensor
                        c.clear()
                        c.put(cnTensor.floatBuffer)
                        c.flip()
                    }

                    hTensor.close()
                    cTensor.close()
                    prob
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating VAD frame", e)
            0.0f
        } finally {
            inputTensor.close()
            srTensor.close()
        }
    }

    /**
     * Resets the recurrent neural network hidden states and context buffer to zero.
     */
    @Synchronized
    fun resetState() {
        contextBuffer.fill(0.0f)

        stateBuffer.clear()
        while (stateBuffer.hasRemaining()) {
            stateBuffer.put(0.0f)
        }
        stateBuffer.flip()

        hBuffer?.let {
            it.clear()
            while (it.hasRemaining()) it.put(0.0f)
            it.flip()
        }
        cBuffer?.let {
            it.clear()
            while (it.hasRemaining()) it.put(0.0f)
            it.flip()
        }
    }

    override fun close() {
        session.close()
        ortEnv.close()
    }

    companion object {
        private const val TAG = "SileroVadDetector"

        private fun readAssetBytes(context: Context, assetName: String): ByteArray {
            context.assets.open(assetName).use { inputStream: InputStream ->
                val buffer = ByteArrayOutputStream()
                val temp = ByteArray(8192)
                var read: Int
                while (inputStream.read(temp).also { read = it } != -1) {
                    buffer.write(temp, 0, read)
                }
                return buffer.toByteArray()
            }
        }
    }
}
