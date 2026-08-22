package com.example.recme.ai.config

import android.content.Context
import android.content.SharedPreferences
import com.example.recme.service.VadRecordingService

/**
 * Manages user background profile, custom vocabulary, frequent speakers,
 * and editable AI prompt templates across Gemini and Gemma workflows.
 */
class PromptConfigManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)

    // ==========================================
    // 1. User & Household Profile
    // ==========================================

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
        set(value) = prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()

    var userBio: String
        get() = prefs.getString(KEY_USER_BIO, DEFAULT_USER_BIO) ?: DEFAULT_USER_BIO
        set(value) = prefs.edit().putString(KEY_USER_BIO, value.trim()).apply()

    var frequentSpeakers: String
        get() = prefs.getString(KEY_FREQUENT_SPEAKERS, DEFAULT_FREQUENT_SPEAKERS) ?: DEFAULT_FREQUENT_SPEAKERS
        set(value) = prefs.edit().putString(KEY_FREQUENT_SPEAKERS, value.trim()).apply()

    var customVocabulary: String
        get() = prefs.getString(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY) ?: DEFAULT_CUSTOM_VOCABULARY
        set(value) = prefs.edit().putString(KEY_CUSTOM_VOCABULARY, value.trim()).apply()

    var dialectRules: String
        get() = prefs.getString(KEY_DIALECT_RULES, DEFAULT_DIALECT_RULES) ?: DEFAULT_DIALECT_RULES
        set(value) = prefs.edit().putString(KEY_DIALECT_RULES, value.trim()).apply()

    // ==========================================
    // 2. Customizable Prompt Templates
    // ==========================================

    var geminiTranscriptionInstruction: String
        get() = prefs.getString(KEY_GEMINI_TRANSCRIPTION_PROMPT, DEFAULT_GEMINI_TRANSCRIPTION_PROMPT) ?: DEFAULT_GEMINI_TRANSCRIPTION_PROMPT
        set(value) = prefs.edit().putString(KEY_GEMINI_TRANSCRIPTION_PROMPT, value.trim()).apply()

    var gemmaPolishingPrompt: String
        get() = prefs.getString(KEY_GEMMA_POLISHING_PROMPT, DEFAULT_GEMMA_POLISHING_PROMPT) ?: DEFAULT_GEMMA_POLISHING_PROMPT
        set(value) = prefs.edit().putString(KEY_GEMMA_POLISHING_PROMPT, value.trim()).apply()

    var summaryAndActionsPrompt: String
        get() = prefs.getString(KEY_SUMMARY_ACTIONS_PROMPT, DEFAULT_SUMMARY_ACTIONS_PROMPT) ?: DEFAULT_SUMMARY_ACTIONS_PROMPT
        set(value) = prefs.edit().putString(KEY_SUMMARY_ACTIONS_PROMPT, value.trim()).apply()

    var askAiSystemPrompt: String
        get() = prefs.getString(KEY_ASK_AI_PROMPT, DEFAULT_ASK_AI_PROMPT) ?: DEFAULT_ASK_AI_PROMPT
        set(value) = prefs.edit().putString(KEY_ASK_AI_PROMPT, value.trim()).apply()

    // ==========================================
    // 3. Reset Helpers
    // ==========================================

    fun resetUserContext() {
        prefs.edit()
            .putString(KEY_USER_NAME, DEFAULT_USER_NAME)
            .putString(KEY_USER_BIO, DEFAULT_USER_BIO)
            .putString(KEY_FREQUENT_SPEAKERS, DEFAULT_FREQUENT_SPEAKERS)
            .putString(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY)
            .putString(KEY_DIALECT_RULES, DEFAULT_DIALECT_RULES)
            .apply()
    }

    fun resetPrompt(key: String) {
        val editor = prefs.edit()
        when (key) {
            KEY_GEMINI_TRANSCRIPTION_PROMPT -> editor.putString(KEY_GEMINI_TRANSCRIPTION_PROMPT, DEFAULT_GEMINI_TRANSCRIPTION_PROMPT)
            KEY_GEMMA_POLISHING_PROMPT -> editor.putString(KEY_GEMMA_POLISHING_PROMPT, DEFAULT_GEMMA_POLISHING_PROMPT)
            KEY_SUMMARY_ACTIONS_PROMPT -> editor.putString(KEY_SUMMARY_ACTIONS_PROMPT, DEFAULT_SUMMARY_ACTIONS_PROMPT)
            KEY_ASK_AI_PROMPT -> editor.putString(KEY_ASK_AI_PROMPT, DEFAULT_ASK_AI_PROMPT)
        }
        editor.apply()
    }

    // ==========================================
    // 4. Dynamic Token Substitution
    // ==========================================

    fun formatGeminiTranscriptionInstruction(): String {
        val template = geminiTranscriptionInstruction
        val contextBlock = buildContextBlock()
        return template
            .replace("{USER_NAME}", userName)
            .replace("{USER_CONTEXT}", contextBlock)
            .replace("{VOCABULARY}", customVocabulary)
            .replace("{SPEAKERS}", frequentSpeakers)
            .replace("{DIALECT_RULES}", dialectRules)
    }

    fun formatGemmaPolishingPrompt(rawTranscript: String): String {
        val template = gemmaPolishingPrompt
        val contextBlock = buildContextBlock()
        return template
            .replace("{RAW_TRANSCRIPT}", rawTranscript)
            .replace("{USER_NAME}", userName)
            .replace("{USER_CONTEXT}", contextBlock)
            .replace("{VOCABULARY}", customVocabulary)
            .replace("{SPEAKERS}", frequentSpeakers)
            .replace("{DIALECT_RULES}", dialectRules)
    }

    fun formatSummaryPrompt(transcripts: List<String>): String {
        val template = summaryAndActionsPrompt
        val formattedTranscripts = transcripts.joinToString("\n- ")
        return template
            .replace("{TRANSCRIPTS}", formattedTranscripts)
            .replace("{USER_NAME}", userName)
            .replace("{USER_CONTEXT}", buildContextBlock())
            .replace("{SPEAKERS}", frequentSpeakers)
    }

    fun formatAskAiSystemPrompt(): String {
        val template = askAiSystemPrompt
        return template
            .replace("{USER_NAME}", userName)
            .replace("{USER_CONTEXT}", buildContextBlock())
            .replace("{SPEAKERS}", frequentSpeakers)
    }

    fun getEffectiveFrequentSpeakers(): String {
        return try {
            val profileManager = com.example.recme.ai.speaker.SpeakerProfileManager(context)
            val enrolled = profileManager.getProfilesBlocking().map { "${it.name} (${it.relationship})" }
            if (enrolled.isEmpty()) {
                frequentSpeakers
            } else {
                val manual = frequentSpeakers.split(",").map { it.trim() }.filter { it.isNotBlank() }
                (manual + enrolled).distinct().joinToString(", ")
            }
        } catch (e: Exception) {
            frequentSpeakers
        }
    }

    private fun buildContextBlock(): String {
        val builder = StringBuilder()
        if (userName.isNotBlank()) builder.append("User Name: $userName\n")
        if (userBio.isNotBlank()) builder.append("Background: $userBio\n")
        val speakers = getEffectiveFrequentSpeakers()
        if (speakers.isNotBlank()) builder.append("Frequent Speakers / Family: $speakers\n")
        if (customVocabulary.isNotBlank()) builder.append("Key Vocabulary / Domain Terms: $customVocabulary\n")
        if (dialectRules.isNotBlank()) builder.append("Language & Dialect Notes: $dialectRules\n")
        return builder.toString().trim()
    }

    companion object {
        const val KEY_USER_NAME = "key_user_name"
        const val KEY_USER_BIO = "key_user_bio"
        const val KEY_FREQUENT_SPEAKERS = "key_frequent_speakers"
        const val KEY_CUSTOM_VOCABULARY = "key_custom_vocabulary"
        const val KEY_DIALECT_RULES = "key_dialect_rules"

        const val KEY_GEMINI_TRANSCRIPTION_PROMPT = "key_gemini_transcription_prompt"
        const val KEY_GEMMA_POLISHING_PROMPT = "key_gemma_polishing_prompt"
        const val KEY_SUMMARY_ACTIONS_PROMPT = "key_summary_actions_prompt"
        const val KEY_ASK_AI_PROMPT = "key_ask_ai_prompt"

        // Default Context
        const val DEFAULT_USER_NAME = "Jan"
        const val DEFAULT_USER_BIO = "Automotive engineering specialist focusing on Simscape Multibody, simulation modeling, and Driver-in-the-Loop (DIL) systems."
        const val DEFAULT_FREQUENT_SPEAKERS = "Angelique (wife), Ansunet (daughter), Johan-Henry / Boetie (son)"
        const val DEFAULT_CUSTOM_VOCABULARY = "Simscape, Multibody, Simulink, MATLAB, DIL, VAD, Silero, Whisper, Gemma, Opus, Obsidian, RecMe, Boer maak 'n plan"
        const val DEFAULT_DIALECT_RULES = "Natural trilingual code-switching between Afrikaans, English, and German. Preserve authentic Afrikaans idioms and German phrases without forcing translation."

        // Default Prompt Templates
        const val DEFAULT_GEMINI_TRANSCRIPTION_PROMPT = """You are a precision verbatim speech-to-text transcriber for {USER_NAME}.
{USER_CONTEXT}

Instructions:
1. Output ONLY the exact spoken words in the language they were spoken (Afrikaans, English, German, or natural code-switching).
2. Recognize domain vocabulary and speaker names correctly ({VOCABULARY}).
3. Never include metadata tags, markdown fences, timestamps, or conversational filler.
4. If no speech is present, return an empty transcription string."""

        const val DEFAULT_GEMMA_POLISHING_PROMPT = """<start_of_turn>user
You are an expert multilingual voice-note editor for {USER_NAME}.
{USER_CONTEXT}

Polish the following speech transcript:
- Fix obvious speech-to-text phonetic misspellings.
- Preserve authentic code-switching between Afrikaans, English, and German.
- Keep technical terms and family names intact ({VOCABULARY}).
- Return ONLY the polished transcript text with NO commentary or markdown fences.

Raw Transcript:
"{RAW_TRANSCRIPT}"<end_of_turn>
<start_of_turn>model
"""

        const val DEFAULT_SUMMARY_ACTIONS_PROMPT = """You are an executive personal assistant for {USER_NAME}.
{USER_CONTEXT}

Analyze today's recorded transcripts below:
- {TRANSCRIPTS}

Please output in this EXACT format:
===SUMMARY===
(A concise 2-4 sentence executive summary of topics, decisions, and discussions across Afrikaans, English, and German notes)
===ACTIONS===
- [ ] (Action item 1)
- [ ] (Action item 2)"""

        const val DEFAULT_ASK_AI_PROMPT = """You are Gemma, an intelligent personal knowledge companion and external brain for {USER_NAME}.
{USER_CONTEXT}

You have access to voice recordings and transcripts. Answer queries with direct, concise, and structured responses. Respect the user's natural multilingual context (English technical focus, with Afrikaans grounding)."""
    }
}
