package com.example.recme.ai.gemma

import android.util.Log
import com.example.recme.storage.SpeechSegmentData

/**
 * Handles grammar, punctuation, and code-switching refinement across Afrikaans, English, and German
 * using on-device Gemma LLM with rule-based heuristics fallback.
 */
object GemmaPostProcessor {

    private const val TAG = "GemmaPostProcessor"

    /**
     * Polishes a list of raw transcribed segments using Gemma LLM when available.
     */
    suspend fun polishSegments(
        segments: List<SpeechSegmentData>,
        engine: GemmaLlamaEngine? = null,
        promptConfig: com.example.recme.ai.config.PromptConfigManager? = null
    ): List<SpeechSegmentData> {
        val updated = mutableListOf<SpeechSegmentData>()

        for (seg in segments) {
            val raw = seg.rawText
            if (raw.isNullOrBlank()) {
                updated.add(seg)
                continue
            }

            var polished = ""
            if (engine != null) {
                try {
                    val customPrompt = promptConfig?.formatGemmaPolishingPrompt(raw)
                    val llmOutput = engine.polishTranscript(raw, customPrompt).trim()
                    // Verify that LLM output is valid human-readable text and not empty/garbage tokens
                    val hasValidLetters = llmOutput.count { it.isLetter() } >= 3
                    if (hasValidLetters && llmOutput.length >= raw.length / 3) {
                        polished = llmOutput
                        Log.i(TAG, "Gemma LLM polished: '$raw' -> '$polished'")
                    } else {
                        Log.w(TAG, "Gemma LLM produced invalid/token-only output ('$llmOutput'), retaining Whisper transcript")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Gemma LLM polishing failed for segment, falling back to heuristics", e)
                }
            }

            if (polished.isBlank()) {
                polished = polishText(raw, seg.detectedLanguage)
            }

            updated.add(seg.copy(polishedText = polished))
        }

        return updated
    }

    /**
     * Refines text capitalization, punctuation, and common multilingual phonetic artifacts.
     */
    fun polishText(text: String, languageCode: String?): String {
        var result = text.trim()
        if (result.isEmpty()) return result

        // Capitalize first letter
        result = result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Ensure closing punctuation
        if (!result.endsWith(".") && !result.endsWith("?") && !result.endsWith("!")) {
            result += "."
        }

        // Domain-specific Afrikaans idioms & common ASR contractions
        if (languageCode.equals("af", ignoreCase = true)) {
            result = result
                .replace(Regex("\\bboer maak n plan\\b", RegexOption.IGNORE_CASE), "boer maak 'n plan")
                .replace(Regex("\\bja nee\\b", RegexOption.IGNORE_CASE), "ja-nee")
                .replace(Regex("\\bhoe gaan dit\\b", RegexOption.IGNORE_CASE), "hoe gaan dit")
        }

        return result
    }
}
