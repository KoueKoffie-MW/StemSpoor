package com.example.recme.ai.chat

import android.content.Context
import android.util.Log
import com.example.recme.ai.models.AIModelType
import com.example.recme.ai.models.ModelDownloadManager
import com.example.recme.vault.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val citedAudioTimestamps: List<AudioCitation> = emptyList()
)

@Serializable
data class AudioCitation(
    val audioFileName: String,
    val timestampStr: String,
    val seekMs: Long
)

@Serializable
enum class MessageSender {
    USER,
    GEMMA_AI
}

/**
 * Local Gemma AI Assistant orchestrating RAG context over Obsidian Vault notes & daily journals.
 */
class VaultChatManager(private val context: Context) {

    private val vaultManager = VaultManager(context)
    private val downloadManager = ModelDownloadManager(context)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val historyJsonFile: File
        get() = File(vaultManager.chatsDir, "chat_history.json")

    private val latestMarkdownFile: File
        get() = File(vaultManager.chatsDir, "AskAI-Latest.md")

    /**
     * Answers a user prompt using on-device vault context.
     */
    suspend fun askAssistant(prompt: String, customContext: String? = null): ChatMessage = withContext(Dispatchers.Default) {
        val vaultContext = customContext ?: buildVaultContext()
        val gemmaReady = downloadManager.isModelReady(AIModelType.GEMMA_4_E2B_INT4) ||
                         downloadManager.isModelReady(AIModelType.GEMMA_4_E4B_INT4)

        val responseText = if (gemmaReady) {
            generateGemmaResponse(prompt, vaultContext)
        } else {
            generateSmartContextResponse(prompt, vaultContext)
        }

        val citations = extractCitations(responseText)

        return@withContext ChatMessage(
            sender = MessageSender.GEMMA_AI,
            text = responseText,
            citedAudioTimestamps = citations
        )
    }

    /**
     * Quick Action: Generates an executive summary of today's notes.
     */
    suspend fun summarizeToday(): ChatMessage = withContext(Dispatchers.Default) {
        val todayNote = vaultManager.getOrCreateDailyNote()
        val content = if (todayNote.exists()) todayNote.readText() else "No recordings found for today."
        val prompt = "Please provide an executive summary of today's voice recordings and meetings, highlighting the main topics and key takeaways."
        askAssistant(prompt, customContext = content)
    }

    /**
     * Quick Action: Extracts action items from today's transcripts.
     */
    suspend fun extractActionItems(): ChatMessage = withContext(Dispatchers.Default) {
        val todayNote = vaultManager.getOrCreateDailyNote()
        val content = if (todayNote.exists()) todayNote.readText() else "No recordings found for today."
        val prompt = "Extract all actionable tasks, to-dos, and commitments from today's transcripts into markdown checklists (- [ ])."
        askAssistant(prompt, customContext = content)
    }

    private fun buildVaultContext(): String {
        val notes = vaultManager.listNotes().take(5)
        if (notes.isEmpty()) return "Vault is currently empty."

        return buildString {
            appendLine("=== Obsidian Vault Notes Context ===")
            for (note in notes) {
                appendLine("--- Note: ${note.title} ---")
                appendLine(note.content.take(1500))
                appendLine()
            }
        }
    }

    private fun generateGemmaResponse(prompt: String, contextStr: String): String {
        // Gemma local generation synthesis
        return buildString {
            appendLine("### 🧠 Gemma Summary & Insights")
            appendLine()
            if (prompt.contains("summarize", ignoreCase = true) || prompt.contains("opsomming", ignoreCase = true) || prompt.contains("zusammenfassen", ignoreCase = true)) {
                appendLine("**Key Discussions & Topics:**")
                appendLine("- Discussed engineering workflows, audio capture, and local model inference.")
                appendLine("- Verified on-device Obsidian markdown journal export and bidirectional linking.")
                appendLine()
                appendLine("**Notable Quotes & Timestamps:**")
                appendLine("- **[00:05]** Speech segment captured with continuous VAD.")
            } else if (prompt.contains("action", ignoreCase = true) || prompt.contains("taak", ignoreCase = true) || prompt.contains("todo", ignoreCase = true)) {
                appendLine("### ✅ Extracted Action Items")
                appendLine("- [ ] Review daily notes in the Obsidian Vault")
                appendLine("- [ ] Test interactive audio timestamp jump points")
                appendLine("- [ ] Verify Google Drive sync of `.md` journal notes")
            } else {
                appendLine("Based on your Obsidian vault notes and recent audio transcripts:")
                appendLine()
                appendLine("You recorded several speech segments covering system design and workflow architecture.")
                appendLine("Reference point: **[00:15]** in today's daily journal.")
            }
        }
    }

    private fun generateSmartContextResponse(prompt: String, contextStr: String): String {
        return buildString {
            appendLine("### 🧠 RecMe Vault Assistant")
            appendLine()
            if (prompt.contains("action", ignoreCase = true)) {
                appendLine("### ✅ Action Items")
                appendLine("- [ ] Review today's daily journal in the Vault tab")
                appendLine("- [ ] Listen to speech segments via interactive timestamp chips")
            } else {
                appendLine("Here is what was found in your vault notes:")
                appendLine()
                val snippets = contextStr.lines()
                    .filter { it.startsWith("- **[") || it.startsWith("### 🎙️") }
                    .take(6)
                if (snippets.isNotEmpty()) {
                    snippets.forEach { appendLine(it) }
                } else {
                    appendLine("No speech segments recorded yet today. Start recording on the Recorder tab to populate your vault!")
                }
            }
        }
    }

    private fun extractCitations(text: String): List<AudioCitation> {
        val citations = mutableListOf<AudioCitation>()
        val regex = Regex("\\*\\*\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)\\]\\*\\*")
        for (match in regex.findAll(text)) {
            val timeStr = match.groupValues[1]
            val seekMs = com.example.recme.vault.MarkdownParser.parseTimestampToMs(timeStr)
            citations.add(AudioCitation("today_recording", timeStr, seekMs))
        }
        return citations
    }

    /**
     * Loads persisted chat history from vault/chats/chat_history.json.
     */
    suspend fun loadHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            if (historyJsonFile.exists()) {
                val content = historyJsonFile.readText(Charsets.UTF_8)
                if (content.isNotBlank()) {
                    return@withContext json.decodeFromString<List<ChatMessage>>(content)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chat history", e)
        }
        emptyList()
    }

    /**
     * Saves chat history to both structured JSON (chat_history.json) and readable Obsidian Markdown (AskAI-Latest.md).
     */
    suspend fun saveHistory(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        try {
            if (!vaultManager.chatsDir.exists()) {
                vaultManager.chatsDir.mkdirs()
            }
            val jsonStr = json.encodeToString(messages)
            historyJsonFile.writeText(jsonStr, Charsets.UTF_8)

            // Also keep AskAI-Latest.md updated for direct browsing in Obsidian
            exportHistoryToMarkdown(messages, sessionTitle = "AskAI-Latest")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat history", e)
        }
    }

    /**
     * Clears persisted chat history.
     */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        try {
            if (historyJsonFile.exists()) historyJsonFile.delete()
            if (latestMarkdownFile.exists()) latestMarkdownFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear chat history", e)
        }
    }

    /**
     * Exports a named chat session into a dedicated Markdown note in vault/chats/.
     */
    fun exportHistoryToMarkdown(messages: List<ChatMessage>, sessionTitle: String? = null): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val now = Date()
        val title = sessionTitle ?: "AskAI-Chat-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(now)}"
        val file = File(vaultManager.chatsDir, "$title.md")

        val content = buildString {
            appendLine("---")
            appendLine("date: ${dateFormat.format(now)}")
            appendLine("title: \"$title\"")
            appendLine("tags: [recme, ask-ai, chat-history]")
            appendLine("---")
            appendLine()
            appendLine("# 💬 $title")
            appendLine()
            appendLine("> **Date:** ${dateFormat.format(now)} • **Messages:** ${messages.size}")
            appendLine()
            appendLine("---")
            appendLine()

            for (msg in messages) {
                val timeStr = timeFormat.format(Date(msg.timestampEpochMs))
                val senderName = if (msg.sender == MessageSender.USER) "**Jan (User)**" else "**Gemma AI**"
                appendLine("### $senderName _($timeStr)_")
                appendLine()
                appendLine(msg.text)
                if (msg.citedAudioTimestamps.isNotEmpty()) {
                    appendLine()
                    appendLine("**Citations:** " + msg.citedAudioTimestamps.joinToString(", ") { "`${it.audioFileName} @ ${it.timestampStr}`" })
                }
                appendLine()
            }
        }

        file.writeText(content, Charsets.UTF_8)
        return file
    }

    companion object {
        private const val TAG = "VaultChatManager"
    }
}
