package com.example.recme.ai.gemma

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nehuatl.llamacpp.LlamaContext
import java.io.File

/**
 * High-performance on-device Gemma LLM engine backed by native llama.cpp (ARM64 dotprod/i8mm).
 */
class GemmaLlamaEngine(private val context: Context) {

    private var llamaContext: LlamaContext? = null
    private var isModelLoaded = false
    private var activeModelPath: String? = null

    /**
     * Initializes and loads the GGUF model into memory if not already active.
     */
    suspend fun loadModel(modelFile: File, contextSize: Int = 2048): Boolean = withContext(Dispatchers.Default) {
        if (!modelFile.exists() || modelFile.length() < 100_000_000L) {
            Log.w(TAG, "Gemma model file not found or invalid: ${modelFile.absolutePath}")
            return@withContext false
        }

        if (isModelLoaded && activeModelPath == modelFile.absolutePath && llamaContext != null) {
            Log.d(TAG, "Model already loaded: ${modelFile.name}")
            return@withContext true
        }

        release()

        try {
            Log.i(TAG, "Loading Gemma GGUF model via detached FD from ${modelFile.absolutePath} (ctx: $contextSize)...")
            val pfd = ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val fd = pfd.detachFd()

            val params = mapOf(
                "model" to modelFile.name,
                "model_fd" to fd,
                "n_ctx" to contextSize,
                "n_batch" to 512,
                "n_threads" to 4,
                "use_mlock" to true,
                "embedding" to false
            )

            val ctx = LlamaContext(1, params)
            llamaContext = ctx
            isModelLoaded = true
            activeModelPath = modelFile.absolutePath
            Log.i(TAG, "Gemma GGUF model successfully loaded into RAM: ${modelFile.name}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing Gemma model", e)
            release()
            return@withContext false
        }
    }

    /**
     * Generates a completion for the given prompt using Gemma chat formatting.
     */
    suspend fun generate(prompt: String, maxTokens: Int = 256): String = withContext(Dispatchers.Default) {
        val ctx = llamaContext
        if (ctx == null || !isModelLoaded) {
            Log.w(TAG, "Cannot generate: Gemma model is not loaded")
            return@withContext ""
        }

        try {
            Log.i(TAG, "Sending prompt to Gemma (length: ${prompt.length} chars)...")
            val outputBuilder = StringBuilder()
            ctx.setTokenCallback { token ->
                outputBuilder.append(token)
            }

            val params = mapOf(
                "prompt" to prompt,
                "n_predict" to maxTokens,
                "temperature" to 0.1f,
                "top_p" to 0.9f,
                "stop" to listOf("<end_of_turn>", "<eos>", "<bos>", "<start_of_turn>", "<pad>"),
                "emit_partial_completion" to true
            )

            val result = ctx.completion(params)
            val fullTextResult = (result["text"] as? String)?.trim().orEmpty()
            val finalGenerated = if (fullTextResult.isNotEmpty()) fullTextResult else outputBuilder.toString().trim()
            Log.i(TAG, "Gemma generation completed (${finalGenerated.length} chars): '$finalGenerated'")
            return@withContext cleanGemmaOutput(finalGenerated)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemma inference", e)
            return@withContext ""
        }
    }

    /**
     * Refines a raw multilingual transcript segment preserving Afrikaans/English/German code-switching.
     */
    suspend fun polishTranscript(rawText: String, customPrompt: String? = null): String {
        if (rawText.isBlank()) return rawText
        if (!isModelLoaded) return rawText

        val formattedPrompt = customPrompt ?: """
<start_of_turn>user
You are an expert multilingual editor. Polish the following voice recording transcript.
Preserve the exact original meaning and natural code-switching between Afrikaans, English, and German.
Fix phonetic speech-recognition errors, correct grammar and punctuation.
Return ONLY the polished transcript text with no explanations, greetings, or commentary.

Raw Transcript:
"$rawText"<end_of_turn>
<start_of_turn>model
""".trimIndent()

        val polished = generate(formattedPrompt, maxTokens = 128)
        return if (polished.isNotBlank()) polished else rawText
    }

    /**
     * Synthesizes a structured daily executive summary and action items for Obsidian notes.
     */
    suspend fun generateDailySummary(allTranscripts: List<String>, customPrompt: String? = null): Pair<String, List<String>> {
        if (allTranscripts.isEmpty() || !isModelLoaded) {
            return Pair("No speech recordings to summarize.", emptyList())
        }

        val combinedText = allTranscripts.joinToString("\n- ")
        val formattedPrompt = customPrompt ?: """
<start_of_turn>user
You are an executive personal assistant. Analyze today's voice note recordings below.
Provide:
1. An Executive Summary (2-3 concise sentences capturing key discussions, thoughts, or events across Afrikaans, English, and German notes).
2. Action Items (a bulleted list of any tasks, promises, or things to remember).

Recordings:
- $combinedText<end_of_turn>
<start_of_turn>model
""".trimIndent()

        val output = generate(formattedPrompt, maxTokens = 256)
        if (output.isBlank()) {
            return Pair("Summary generation unavailable.", emptyList())
        }

        return parseSummaryAndActions(output)
    }

    private fun cleanGemmaOutput(raw: String): String {
        return raw
            .replace(Regex("<(?:unused\\d+|start_of_turn|end_of_turn|eos|bos|pad|unk|mask\\d*)>", RegexOption.IGNORE_CASE), "")
            .replace("_response>", "")
            .trim()
            .replace(Regex("^[:\\-_>\\s]+"), "")
            .removeSurrounding("\"")
            .trim()
    }

    private fun parseSummaryAndActions(text: String): Pair<String, List<String>> {
        val cleaned = cleanGemmaOutput(text)
        val lines = cleaned.lines()
        val summaryBuilder = StringBuilder()
        val actions = mutableListOf<String>()
        var inActions = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Action Item", ignoreCase = true) || 
                trimmed.startsWith("## Action", ignoreCase = true) ||
                trimmed.startsWith("2. Action", ignoreCase = true) ||
                trimmed.startsWith("Action:", ignoreCase = true)) {
                inActions = true
                continue
            }
            if (inActions) {
                if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.matches(Regex("^\\d+\\..*"))) {
                    val cleanItem = trimmed.replace(Regex("^[-*\\d.]+\\s*"), "").trim()
                    if (cleanItem.isNotEmpty()) {
                        actions.add(cleanItem)
                    }
                }
            } else {
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("1. Executive", ignoreCase = true) && !trimmed.startsWith("Executive Summary", ignoreCase = true)) {
                    summaryBuilder.append(trimmed).append(" ")
                }
            }
        }

        val summary = summaryBuilder.toString().trim().ifEmpty { cleaned }
        return Pair(summary, actions)
    }

    /**
     * Unloads the model from RAM and releases all native C++ resources.
     */
    fun release() {
        try {
            llamaContext?.release()
            llamaContext = null
            isModelLoaded = false
            activeModelPath = null
            Log.d(TAG, "Gemma native resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Gemma resources", e)
        }
    }

    companion object {
        private const val TAG = "GemmaLlamaEngine"
    }
}
