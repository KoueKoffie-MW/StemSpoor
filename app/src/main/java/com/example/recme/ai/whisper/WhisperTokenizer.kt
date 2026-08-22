package com.example.recme.ai.whisper

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * High-performance BPE tokenizer for Whisper Seq2Seq models.
 * Parses standard vocab.json into a direct token ID lookup table (51,865 tokens).
 */
class WhisperTokenizer(vocabFile: File?) {

    private val vocabArray = Array(TOTAL_VOCAB_SIZE) { "" }
    private var isLoaded = false

    init {
        loadVocab(vocabFile)
    }

    private fun loadVocab(vocabFile: File?) {
        if (vocabFile == null || !vocabFile.exists()) {
            Log.w(TAG, "vocab.json file not found, tokenizer operating with basic ASCII fallback")
            return
        }

        try {
            val jsonString = vocabFile.readText(Charsets.UTF_8)
            val jsonElement = Json.parseToJsonElement(jsonString).jsonObject

            for ((tokenStr, idElement) in jsonElement) {
                val id = idElement.jsonPrimitive.content.toIntOrNull() ?: continue
                if (id in 0 until TOTAL_VOCAB_SIZE) {
                    vocabArray[id] = cleanTokenString(tokenStr)
                }
            }
            isLoaded = true
            Log.i(TAG, "Successfully loaded ${vocabArray.count { it.isNotEmpty() }} tokens from vocab.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse vocab.json", e)
        }
    }

    /**
     * Converts raw BPE byte representations into readable UTF-8 text.
     * Whisper uses `Ġ` (\u0120) to represent leading spaces.
     */
    private fun cleanTokenString(rawToken: String): String {
        return rawToken
            .replace("Ġ", " ")
            .replace("Ċ", "\n")
            .replace("<|startoftranscript|>", "")
            .replace("<|transcribe|>", "")
            .replace("<|notimestamps|>", "")
            .replace("<|endoftranscript|>", "")
    }

    /**
     * Decodes a list of generated token IDs into a single coherent text transcript.
     */
    fun decode(tokenIds: List<Int>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            if (id == SOT_TOKEN || id == TRANSCRIBE_TOKEN || id == NO_TIMESTAMPS_TOKEN || id == EOT_TOKEN) {
                continue
            }
            if (id in 50258..50358) {
                // Language token (e.g. <|en|>, <|de|>, <|af|>)
                continue
            }
            if (id in 0 until TOTAL_VOCAB_SIZE) {
                val token = vocabArray[id]
                if (token.isNotEmpty()) {
                    sb.append(token)
                }
            }
        }
        return sb.toString().trim()
    }

    companion object {
        private const val TAG = "WhisperTokenizer"
        const val TOTAL_VOCAB_SIZE = 51866

        const val EOT_TOKEN = 50257
        const val SOT_TOKEN = 50258
        const val TRANSCRIBE_TOKEN = 50359
        const val NO_TIMESTAMPS_TOKEN = 50363

        // Common language prompt tokens
        const val LANG_EN = 50259
        const val LANG_DE = 50261
        const val LANG_AF = 50327
        const val LANG_NL = 50286
        const val LANG_FR = 50265
        const val LANG_ES = 50262

        fun getLanguageToken(langCode: String): Int {
            return when (langCode.lowercase()) {
                "af" -> LANG_AF
                "de" -> LANG_DE
                "nl" -> LANG_NL
                "fr" -> LANG_FR
                "es" -> LANG_ES
                else -> LANG_EN
            }
        }

        fun getLanguageCode(tokenId: Int): String {
            return when (tokenId) {
                LANG_AF -> "af"
                LANG_DE -> "de"
                LANG_NL -> "nl"
                LANG_FR -> "fr"
                LANG_ES -> "es"
                else -> "en"
            }
        }
    }
}
