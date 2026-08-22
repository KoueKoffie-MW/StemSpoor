package com.example.recme.ai.gemini

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.recme.service.VadRecordingService
import com.example.recme.storage.SpeechSegmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Production-ready Multimodal Audio Transcriber powered by Google Gemini 2.0 Flash.
 * Transcribes mixed-language speech (Afrikaans, German, English) with high verbatim accuracy.
 */
class GeminiAudioTranscriber(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getApiKey(): String? {
        val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }
    }

    fun getModelId(): String {
        val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val savedModel = prefs.getString(KEY_GEMINI_MODEL_ID, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        // If saved model is known to be unavailable or rate-limited on free tier, default to gemini-3.6-flash
        return if (SUPPORTED_MODELS.any { it.first == savedModel } && savedModel != "gemini-3.7-flash") {
            savedModel
        } else {
            DEFAULT_MODEL_ID
        }
    }

    fun isConfigured(): Boolean {
        return getApiKey() != null
    }

    fun isEnabled(): Boolean {
        val prefs = context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)
        val engine = prefs.getString(KEY_TRANSCRIPTION_ENGINE, ENGINE_ON_DEVICE)
        return engine == ENGINE_GEMINI_CLOUD && isConfigured()
    }

    /**
     * Validates a candidate Gemini API key by making a lightweight ping to the API.
     */
    suspend fun testApiKey(apiKey: String, modelId: String = getModelId()): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank"))
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=${apiKey.trim()}"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val requestBody = """
                {
                  "contents": [{
                    "parts": [{ "text": "Ping test" }]
                  }],
                  "generationConfig": { "maxOutputTokens": 5 }
                }
            """.trimIndent()

            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Result.success("Connection successful! Gemini 2.0 Flash is ready.")
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                val errorMsg = try {
                    val root = json.parseToJsonElement(err).jsonObject
                    root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: err
                } catch (e: Exception) {
                    err
                }
                Result.failure(IllegalStateException("API Error ($responseCode): $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Transcribes speech segments using Gemini Multimodal Audio understanding.
     */
    suspend fun transcribeSegments(
        audioFile: File,
        segments: List<SpeechSegmentData>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<SpeechSegmentData> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey == null || !audioFile.exists() || segments.isEmpty()) return@withContext segments

        val updatedSegments = mutableListOf<SpeechSegmentData>()
        val total = segments.size

        for ((idx, segment) in segments.withIndex()) {
            onProgress?.invoke(idx + 1, total)

            // Enforce minimum delay between calls to respect free-tier rate limits (20 req/min)
            if (idx > 0) {
                delay(MIN_MS_BETWEEN_CALLS)
            }

            try {
                val pcmBytes = com.example.recme.audio.AudioChunkExtractor.extractPcmChunk(
                    audioFile,
                    segment.audioStartMs,
                    segment.audioEndMs
                )

                if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                    Log.i(TAG, "Transcribing segment ${segment.segmentIndex + 1}/$total (${pcmBytes.size} bytes PCM)...")
                    val chunkWavBytes = com.example.recme.audio.AudioChunkExtractor.createWavContainer(pcmBytes)
                    val base64Audio = Base64.encodeToString(chunkWavBytes, Base64.NO_WRAP)

                    val result = executeWithRetry {
                        callGeminiAudioApi(apiKey, base64Audio, "audio/wav")
                    }

                    // Sanitize: strip known Gemini noise/silence placeholders
                    val transcriptText = sanitizeTranscript(result.text)
                    val detectedLang = if (transcriptText.isNotBlank()) detectLanguage(transcriptText) else "en"
                    val detectedSpeaker = result.speaker
                    Log.i(TAG, "Segment ${segment.segmentIndex + 1}/$total transcript: '$transcriptText' (Lang: $detectedLang, Speaker: ${detectedSpeaker ?: "none"})")

                    updatedSegments.add(
                        segment.copy(
                            detectedLanguage = detectedLang,
                            speaker = detectedSpeaker ?: segment.speaker,
                            rawText = transcriptText.ifBlank { null },
                            polishedText = transcriptText.ifBlank { null }
                        )
                    )
                } else {
                    Log.w(TAG, "No PCM bytes extracted for segment ${segment.segmentIndex} " +
                        "[${segment.audioStartMs}–${segment.audioEndMs}ms] in ${audioFile.name}")
                    updatedSegments.add(segment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini transcription failed for segment ${segment.segmentIndex}", e)
                updatedSegments.add(segment)
            }
        }

        return@withContext updatedSegments

    }

    /**
     * Generates an executive daily summary and action items checklist via Gemini API without local LLM overhead.
     */
    suspend fun generateSummaryAndActions(texts: List<String>): Pair<String, List<String>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: return@withContext Pair("", emptyList())
        if (texts.isEmpty()) return@withContext Pair("", emptyList())

        val promptConfig = com.example.recme.ai.config.PromptConfigManager(context)
        val promptText = promptConfig.formatSummaryPrompt(texts)

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${getModelId()}:generateContent?key=${apiKey.trim()}"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val requestBody = """
                {
                  "contents": [{
                    "parts": [{ "text": ${org.json.JSONObject.quote(promptText)} }]
                  }],
                  "generationConfig": { "maxOutputTokens": 600, "temperature": 0.2 }
                }
            """.trimIndent()

            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().readText()
                val root = json.parseToJsonElement(responseText).jsonObject
                val fullText = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""

                val summary = fullText.substringAfter("===SUMMARY===").substringBefore("===ACTIONS===").trim()
                val actionsRaw = fullText.substringAfter("===ACTIONS===").trim()
                val actionList = actionsRaw.lines()
                    .map { it.replace(Regex("^-\\s*\\[[ xX]?\\]\\s*"), "").replace(Regex("^[\\-*•]\\s*"), "").trim() }
                    .filter { it.isNotBlank() }

                return@withContext Pair(summary, actionList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate summary via Gemini", e)
        }

        return@withContext Pair("", emptyList())
    }

    private suspend fun executeWithRetry(
        maxAttempts: Int = 3,
        block: suspend () -> GeminiTranscriptResult
    ): GeminiTranscriptResult {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Gemini attempt $attempt/$maxAttempts failed: ${e.message}")
                if (attempt < maxAttempts) {
                    val backoffMs = attempt * 2000L
                    delay(backoffMs)
                }
            }
        }
        throw lastException ?: IllegalStateException("Gemini execution failed after $maxAttempts attempts")
    }

    private fun callGeminiAudioApi(
        apiKey: String,
        base64Audio: String,
        mimeType: String,
        modelId: String = getModelId()
    ): GeminiTranscriptResult {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=${apiKey.trim()}"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        val promptConfig = com.example.recme.ai.config.PromptConfigManager(context)
        val systemInstruction = promptConfig.formatGeminiTranscriptionInstruction()
        val speakerHint = if (promptConfig.frequentSpeakers.isNotBlank()) {
            "Identify the speaker name (${promptConfig.userName}, ${promptConfig.frequentSpeakers}) if identifiable from voice or context."
        } else {
            "Identify the speaker name (${promptConfig.userName}) if identifiable."
        }

        val promptText = "Transcribe the spoken audio verbatim word-for-word. Do not translate. Preserve code-switching between Afrikaans, English, and German. $speakerHint"

        val requestBody = """
            {
              "system_instruction": {
                "parts": [{
                  "text": ${org.json.JSONObject.quote(systemInstruction)}
                }]
              },
              "contents": [{
                "parts": [
                  {
                    "inline_data": {
                      "mime_type": "$mimeType",
                      "data": "$base64Audio"
                    }
                  },
                  {
                    "text": ${org.json.JSONObject.quote(promptText)}
                  }
                ]
              }],
              "generationConfig": {
                "temperature": 0.0,
                "maxOutputTokens": 2048,
                "response_mime_type": "application/json",
                "response_schema": {
                  "type": "OBJECT",
                  "properties": {
                    "transcription": { "type": "STRING" },
                    "speaker": { "type": "STRING" }
                  },
                  "required": ["transcription"]
                }
              }
            }
        """.trimIndent()

        conn.outputStream.use { os ->
            os.write(requestBody.toByteArray(Charsets.UTF_8))
        }

        val responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
            // If selected model hits rate limits (429), is overloaded (503), or deprecated (404), auto-fallback
            if ((responseCode == 429 || responseCode == 503 || responseCode == 404) && modelId != DEFAULT_MODEL_ID) {
                Log.w(TAG, "Model $modelId failed with $responseCode. Falling back to $DEFAULT_MODEL_ID")
                return callGeminiAudioApi(apiKey, base64Audio, mimeType, DEFAULT_MODEL_ID)
            }

            throw IllegalStateException("Gemini API Error $responseCode: $err")
        }

        val responseText = conn.inputStream.bufferedReader().readText()
        val root = json.parseToJsonElement(responseText).jsonObject
        val candidates = root["candidates"]?.jsonArray
        val firstCandidate = candidates?.firstOrNull()?.jsonObject
        val content = firstCandidate?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray
        val textPart = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
        return extractTranscriptResult(textPart)
    }

    /**
     * Extracts the transcription text and speaker from either structured JSON or plain text.
     */
    private fun extractTranscriptResult(rawResponse: String): GeminiTranscriptResult {
        val trimmed = rawResponse.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (trimmed.startsWith("{") && trimmed.contains("transcription")) {
            try {
                val jsonObject = org.json.JSONObject(trimmed)
                val text = jsonObject.optString("transcription", "").trim()
                val spkRaw = jsonObject.optString("speaker", "").trim()
                val spk = spkRaw.takeIf {
                    it.isNotBlank() &&
                    !it.equals("null", ignoreCase = true) &&
                    !it.equals("unknown", ignoreCase = true) &&
                    !it.equals("speaker", ignoreCase = true)
                }
                return GeminiTranscriptResult(text, spk)
            } catch (e: Exception) {
                val match = Regex("\"transcription\"\\s*:\\s*\"([\\s\\S]*?)\"").find(trimmed)
                if (match != null) {
                    val text = match.groupValues[1]
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\r", "")
                        .trim()
                    val spkMatch = Regex("\"speaker\"\\s*:\\s*\"([\\s\\S]*?)\"").find(trimmed)
                    val spk = spkMatch?.groupValues?.get(1)?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.equals("unknown", ignoreCase = true)
                    }
                    return GeminiTranscriptResult(text, spk)
                }
            }
        }
        return GeminiTranscriptResult(trimmed, null)
    }

    data class GeminiTranscriptResult(
        val text: String,
        val speaker: String? = null
    )

    /**
     * Light safety net for the fallback path when structured JSON output isn't returned.
     * The primary defence against special tokens is the response_mime_type + response_schema
     * in callGeminiAudioApi(). This only catches clear-cut garbage in the fallback case.
     */
    private fun sanitizeTranscript(raw: String): String {
        if (raw.isBlank()) return ""
        // Only strip the most unambiguous model control token patterns —
        // nothing that could accidentally eat valid speech characters
        return raw
            .replace(Regex("<(?:unused\\d+|bos|eos|pad|unk|mask)>", RegexOption.IGNORE_CASE), "")
            .replace("[multimodal]", "")
            .trim()
    }

    private fun detectLanguage(cleanText: String): String {
        return when {
            cleanText.contains(Regex("\\b(die|nie|het|ons|gaan|honde|parkie|boetie|baie|asseblief)\\b", RegexOption.IGNORE_CASE)) -> "af"
            cleanText.contains(Regex("\\b(und|der|die|das|ist|nicht|wir|haben|kannst|zählen)\\b", RegexOption.IGNORE_CASE)) -> "de"
            else -> "en"
        }
    }

    private fun createWavHeaderAndData(pcmData: ByteArray): ByteArray {
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
        header.putInt(16) // Subchunk1Size (16 for PCM)
        header.putShort(1.toShort()) // AudioFormat (1 for PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort()) // BlockAlign
        header.putShort(16.toShort()) // BitsPerSample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "GeminiAudioTranscriber"
        const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        const val KEY_GEMINI_MODEL_ID = "key_gemini_model_id"
        const val DEFAULT_MODEL_ID = "gemini-3.5-flash-lite"   // Fast, open free-tier quota, no thinking token truncation
        const val KEY_TRANSCRIPTION_ENGINE = "key_transcription_engine"
        const val ENGINE_ON_DEVICE = "on_device"
        const val ENGINE_GEMINI_CLOUD = "gemini_cloud"
        const val ENGINE_SMART_HYBRID = "smart_hybrid"

        // Free-tier API keys are limited to 20 requests per minute (~3s per call).
        // We enforce a minimum delay between segment API calls to avoid HTTP 429.
        private const val MIN_MS_BETWEEN_CALLS = 3500L

        val SUPPORTED_MODELS = listOf(
            "gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite (Recommended — High Speed)",
            "gemini-flash-latest"   to "Gemini Flash Latest",
            "gemini-3.6-flash"      to "Gemini 3.6 Flash",
            "gemini-3.7-flash"      to "Gemini 3.7 Flash (Paid Tier Required)"
        )
    }
}
