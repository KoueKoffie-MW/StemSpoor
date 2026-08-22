package com.example.recme.ai.transcription

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.recme.ai.gemini.GeminiAudioTranscriber
import com.example.recme.ai.gemma.GemmaPostProcessor
import com.example.recme.ai.models.AIModelType
import com.example.recme.ai.models.ModelDownloadManager
import com.example.recme.ai.whisper.WhisperEngine
import com.example.recme.ai.whisper.WhisperLanguageConfig
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.SpeechSegmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Operating mode for the Dual Transcription Pipeline (MOD-04).
 */
enum class TranscriptionEngineMode(val key: String, val displayName: String) {
    LOCAL_ONLY("local_only", "Local Only (Private Offline)"),
    CLOUD_ONLY("cloud_only", "Google AI Studio (Gemini Flash)"),
    SMART_HYBRID("smart_hybrid", "Smart Hybrid (Cloud with Local Fallback)");

    companion object {
        fun fromKey(key: String?): TranscriptionEngineMode {
            return entries.find { it.key == key } ?: SMART_HYBRID
        }
    }
}

/**
 * Coordinates dual-engine speech transcription across Local On-Device (SenseVoice/Whisper)
 * and Cloud Multimodal (Google AI Studio Gemini Flash).
 */
class TranscriptionManager(
    private val context: Context,
    private val geminiTranscriber: GeminiAudioTranscriber,
    private val downloadManager: ModelDownloadManager
) {
    private val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)

    var engineMode: TranscriptionEngineMode
        get() = TranscriptionEngineMode.fromKey(prefs.getString(KEY_ENGINE_MODE, TranscriptionEngineMode.SMART_HYBRID.key))
        set(value) = prefs.edit().putString(KEY_ENGINE_MODE, value.key).apply()

    /**
     * Transcribes an audio file and its detected VAD speech segments according to configured engine mode.
     */
    suspend fun transcribeRecording(
        audioFile: File,
        segments: List<SpeechSegmentData>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<SpeechSegmentData> = withContext(Dispatchers.Default) {
        if (!audioFile.exists() || segments.isEmpty()) return@withContext segments

        val mode = engineMode
        val isNetworkAvailable = isOnline()
        val isGeminiReady = geminiTranscriber.isConfigured()

        val shouldUseCloud = when (mode) {
            TranscriptionEngineMode.CLOUD_ONLY -> isGeminiReady && isNetworkAvailable
            TranscriptionEngineMode.SMART_HYBRID -> isGeminiReady && isNetworkAvailable
            TranscriptionEngineMode.LOCAL_ONLY -> false
        }

        if (shouldUseCloud) {
            try {
                Log.i(TAG, "Executing Cloud Transcription via Google AI Studio (${geminiTranscriber.getModelId()})...")
                val cloudSegments = geminiTranscriber.transcribeSegments(audioFile, segments, onProgress)
                if (cloudSegments.any { !it.rawText.isNullOrBlank() }) {
                    return@withContext cloudSegments
                }
                Log.w(TAG, "Cloud transcription returned empty text, falling back to local engine...")
            } catch (e: Exception) {
                Log.w(TAG, "Cloud transcription failed, falling back to on-device engine", e)
                if (mode == TranscriptionEngineMode.CLOUD_ONLY) {
                    throw e
                }
            }
        }

        // Local On-Device Transcription (SenseVoice / Whisper)
        Log.i(TAG, "Executing On-Device Local Transcription...")
        return@withContext transcribeLocalOnDevice(audioFile, segments, onProgress)
    }

    private suspend fun transcribeLocalOnDevice(
        audioFile: File,
        segments: List<SpeechSegmentData>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<SpeechSegmentData> = withContext(Dispatchers.Default) {
        val whisperModel = when {
            downloadManager.isModelReady(AIModelType.WHISPER_LARGE_V3_TURBO) -> AIModelType.WHISPER_LARGE_V3_TURBO
            downloadManager.isModelReady(AIModelType.WHISPER_SMALL_INT8) -> AIModelType.WHISPER_SMALL_INT8
            else -> null
        }

        if (whisperModel == null) {
            Log.w(TAG, "No local ASR model is downloaded on device.")
            return@withContext segments
        }

        val encoderFile = downloadManager.getEncoderFile(whisperModel)
        val decoderFile = downloadManager.getDecoderFile(whisperModel)
        val vocabFile = downloadManager.getVocabFile(whisperModel)
        val languageSet = prefs.getStringSet(KEY_ACTIVE_LANGUAGES, WhisperLanguageConfig.DEFAULT_LANGUAGES.toSet())?.toList()
            ?: WhisperLanguageConfig.DEFAULT_LANGUAGES

        var whisperEngine: WhisperEngine? = null
        try {
            whisperEngine = WhisperEngine(
                encoderFile = encoderFile,
                decoderFile = decoderFile,
                vocabFile = vocabFile,
                activeLanguages = languageSet
            )
            val transcribed = whisperEngine.transcribeSegments(audioFile, segments) { cur, total ->
                onProgress?.invoke(cur, total)
            }
            return@withContext GemmaPostProcessor.polishSegments(transcribed)
        } finally {
            whisperEngine?.close()
        }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "TranscriptionManager"
        const val KEY_ENGINE_MODE = "key_transcription_engine_mode"
        const val KEY_ACTIVE_LANGUAGES = "key_active_languages"
    }
}
