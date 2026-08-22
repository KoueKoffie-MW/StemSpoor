package com.example.recme.ai.models

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Individual file artifact within an AI model package.
 */
data class ModelArtifact(
    val url: String,
    val localFileName: String,
    val expectedSizeBytes: Long
)

/**
 * Defines downloadable AI model artifacts for on-device ASR and LLM reasoning.
 */
enum class AIModelType(
    val id: String,
    val displayName: String,
    val description: String,
    val artifacts: List<ModelArtifact>
) {
    WHISPER_LARGE_V3_TURBO(
        id = "whisper_large_v3_turbo",
        displayName = "Whisper Large-v3 Turbo (INT8)",
        description = "SOTA multilingual ASR (~800MB). 8x faster 4-layer decoder, best accuracy for Afrikaans, English & German.",
        artifacts = listOf(
            ModelArtifact(
                url = "https://huggingface.co/onnx-community/whisper-large-v3-turbo/resolve/main/onnx/encoder_model_quantized.onnx",
                localFileName = "whisper_large_v3_turbo_encoder.onnx",
                expectedSizeBytes = 512_000_000L
            ),
            ModelArtifact(
                url = "https://huggingface.co/onnx-community/whisper-large-v3-turbo/resolve/main/onnx/decoder_model_quantized.onnx",
                localFileName = "whisper_large_v3_turbo_decoder.onnx",
                expectedSizeBytes = 280_000_000L
            ),
            ModelArtifact(
                url = "https://huggingface.co/onnx-community/whisper-large-v3-turbo/resolve/main/vocab.json",
                localFileName = "whisper_large_v3_turbo_vocab.json",
                expectedSizeBytes = 1_040_000L
            )
        )
    ),
    WHISPER_SMALL_INT8(
        id = "whisper_small_int8",
        displayName = "Whisper Small (INT8)",
        description = "Balanced multilingual ASR (~500MB). Complete Seq2Seq Encoder + Decoder + Vocab.",
        artifacts = listOf(
            ModelArtifact(
                url = "https://huggingface.co/onnx-community/whisper-small/resolve/main/onnx/encoder_model_quantized.onnx",
                localFileName = "whisper_small_encoder.onnx",
                expectedSizeBytes = 148_000_000L
            ),
            ModelArtifact(
                url = "https://huggingface.co/onnx-community/whisper-small/resolve/main/onnx/decoder_model_quantized.onnx",
                localFileName = "whisper_small_decoder.onnx",
                expectedSizeBytes = 360_000_000L
            ),
            ModelArtifact(
                url = "https://huggingface.co/onnx-community/whisper-small/resolve/main/vocab.json",
                localFileName = "whisper_small_vocab.json",
                expectedSizeBytes = 1_040_000L
            )
        )
    ),
    GEMMA_4_E2B_INT4(
        id = "gemma_4_e2b_int4",
        displayName = "Gemma 4 E2B (INT4)",
        description = "Google DeepMind edge LLM (~1.5GB). Multimodal reasoning, thinking mode & native audio.",
        artifacts = listOf(
            ModelArtifact(
                url = "https://huggingface.co/lmstudio-community/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
                localFileName = "gemma_4_e2b_int4.gguf",
                expectedSizeBytes = 1_530_000_000L
            )
        )
    ),
    GEMMA_4_E4B_INT4(
        id = "gemma_4_e4b_int4",
        displayName = "Gemma 4 E4B (INT4)",
        description = "Higher capacity Gemma 4 (~2.8GB). Deep multi-step reasoning & long-context synthesis.",
        artifacts = listOf(
            ModelArtifact(
                url = "https://huggingface.co/lmstudio-community/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
                localFileName = "gemma_4_e4b_int4.gguf",
                expectedSizeBytes = 2_850_000_000L
            )
        )
    );

    val sizeBytes: Long get() = artifacts.sumOf { it.expectedSizeBytes }
    val localFileName: String get() = artifacts.first().localFileName
}

/**
 * Download state for a model.
 */
sealed class ModelDownloadState {
    data object NotDownloaded : ModelDownloadState()
    data class Downloading(
        val progressPercent: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedMbPerSec: Float = 0f,
        val currentFileName: String = ""
    ) : ModelDownloadState()
    data class Ready(val localFiles: List<File>) : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

/**
 * Manages downloading, verification, and local caching of on-device AI models with resumable Range support.
 */
class ModelDownloadManager(private val context: Context) {

    private val modelsDir = File(context.filesDir, "ai_models").apply { mkdirs() }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    init {
        refreshStates()
    }

    fun getLocalModelFiles(modelType: AIModelType): List<File> {
        return modelType.artifacts.map { File(modelsDir, it.localFileName) }
    }

    fun getEncoderFile(modelType: AIModelType): File {
        return File(modelsDir, modelType.artifacts.first { it.localFileName.contains("encoder") }.localFileName)
    }

    fun getDecoderFile(modelType: AIModelType): File {
        return File(modelsDir, modelType.artifacts.first { it.localFileName.contains("decoder") }.localFileName)
    }

    fun getVocabFile(modelType: AIModelType): File {
        return File(modelsDir, modelType.artifacts.first { it.localFileName.contains("vocab") }.localFileName)
    }

    fun isModelReady(modelType: AIModelType): Boolean {
        val files = getLocalModelFiles(modelType)
        return files.all { it.exists() && it.length() > 500 }
    }

    fun getModelFile(modelType: AIModelType): File {
        return File(modelsDir, modelType.localFileName)
    }

    fun refreshStates() {
        val states = mutableMapOf<String, ModelDownloadState>()
        for (model in AIModelType.entries) {
            val files = getLocalModelFiles(model)
            val allReady = files.all { it.exists() && it.length() > 500 }

            if (allReady) {
                states[model.id] = ModelDownloadState.Ready(files)
            } else if (activeJobs[model.id]?.isActive == true) {
                // Keep downloading state
                val existing = _downloadStates.value[model.id]
                states[model.id] = existing ?: ModelDownloadState.Downloading(0f, 0L, model.sizeBytes)
            } else {
                states[model.id] = ModelDownloadState.NotDownloaded
            }
        }
        _downloadStates.value = states
    }

    fun startDownload(modelType: AIModelType) {
        if (activeJobs[modelType.id]?.isActive == true) {
            Log.i(TAG, "Download already active for: ${modelType.id}")
            return
        }

        val totalExpectedBytes = modelType.sizeBytes

        val job = appScope.launch {
            try {
                var totalBytesDownloadedSoFar = 0L

                for (artifact in modelType.artifacts) {
                    val destFile = File(modelsDir, artifact.localFileName)
                    val partFile = File(modelsDir, "${artifact.localFileName}.part")

                    if (destFile.exists() && destFile.length() > 500) {
                        totalBytesDownloadedSoFar += destFile.length()
                        continue
                    }

                    downloadArtifactResumable(
                        artifact = artifact,
                        partFile = partFile,
                        destFile = destFile,
                        totalExpectedBytes = totalExpectedBytes,
                        modelType = modelType
                    ) { fileDownloadedBytes ->
                        val currentTotal = totalBytesDownloadedSoFar + fileDownloadedBytes
                        val progress = if (totalExpectedBytes > 0) currentTotal.toFloat() / totalExpectedBytes.toFloat() else 0.5f
                        updateState(
                            modelType.id,
                            ModelDownloadState.Downloading(
                                progressPercent = progress.coerceIn(0f, 1f),
                                bytesDownloaded = currentTotal,
                                totalBytes = totalExpectedBytes,
                                currentFileName = artifact.localFileName
                            )
                        )
                    }

                    totalBytesDownloadedSoFar += destFile.length()
                }

                val allFiles = getLocalModelFiles(modelType)
                updateState(modelType.id, ModelDownloadState.Ready(allFiles))
                Log.i(TAG, "All artifacts ready for: ${modelType.id}")

            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${modelType.id}", e)
                updateState(modelType.id, ModelDownloadState.Error(e.message ?: "Network download error"))
            } finally {
                activeJobs.remove(modelType.id)
            }
        }

        activeJobs[modelType.id] = job
    }

    private suspend fun downloadArtifactResumable(
        artifact: ModelArtifact,
        partFile: File,
        destFile: File,
        totalExpectedBytes: Long,
        modelType: AIModelType,
        onFileProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val url = URL(artifact.url)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true

        if (existingBytes > 0) {
            conn.setRequestProperty("Range", "bytes=$existingBytes-")
        }

        val responseCode = conn.responseCode
        val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
        val isOk = responseCode == HttpURLConnection.HTTP_OK

        if (!isPartial && !isOk) {
            throw IllegalStateException("Server returned HTTP $responseCode for ${artifact.url}")
        }

        val append = isPartial && existingBytes > 0
        var currentFileBytes = if (append) existingBytes else 0L

        var lastLogTime = System.currentTimeMillis()
        var lastBytes = currentFileBytes

        BufferedInputStream(conn.inputStream).use { input ->
            BufferedOutputStream(FileOutputStream(partFile, append)).use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    currentFileBytes += read

                    val now = System.currentTimeMillis()
                    if (now - lastLogTime >= 500) {
                        val timeDiffSec = (now - lastLogTime) / 1000f
                        val bytesDiff = currentFileBytes - lastBytes
                        val speedMbPerSec = if (timeDiffSec > 0) (bytesDiff / (1024f * 1024f)) / timeDiffSec else 0f

                        onFileProgress(currentFileBytes)
                        lastLogTime = now
                        lastBytes = currentFileBytes
                    }
                }
                output.flush()
            }
        }

        if (partFile.exists()) {
            if (destFile.exists()) destFile.delete()
            partFile.renameTo(destFile)
        }
    }

    fun deleteModel(modelType: AIModelType) {
        activeJobs[modelType.id]?.cancel()
        activeJobs.remove(modelType.id)

        for (file in getLocalModelFiles(modelType)) {
            if (file.exists()) file.delete()
            val part = File(modelsDir, "${file.name}.part")
            if (part.exists()) part.delete()
        }

        updateState(modelType.id, ModelDownloadState.NotDownloaded)
    }

    private fun updateState(modelId: String, state: ModelDownloadState) {
        val current = _downloadStates.value.toMutableMap()
        current[modelId] = state
        _downloadStates.value = current
    }

    companion object {
        private const val TAG = "ModelDownloadManager"
    }
}
