package com.example.recme.vault

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.recme.storage.RecordingItem
import com.example.recme.storage.SpeechSegmentData
import android.content.SharedPreferences
import com.example.recme.service.VadRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BatchSyncResult(
    val recordingsExported: Int,
    val dailyNotesUpdated: Int,
    val totalSpeechSegments: Int,
    val datesProcessed: List<String>
)

data class VaultNote(
    val file: File,
    val title: String,
    val relativePath: String,
    val lastModifiedMs: Long,
    val isDailyNote: Boolean,
    val dateStr: String? = null,
    val tags: List<String> = emptyList(),
    val outLinks: List<String> = emptyList(),
    val content: String = ""
)

/**
 * Manages the on-device Obsidian Vault hierarchy and bidirectional links.
 */
class VaultManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(VadRecordingService.PREFS_NAME, Context.MODE_PRIVATE)

    var isAutoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_VAULT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC_VAULT, value).apply()

    val vaultDir: File by lazy {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val recMeDir = File(docsDir, "RecMe")
        val vDir = File(recMeDir, "vault")
        if (!vDir.exists()) vDir.mkdirs()
        File(vDir, "daily").mkdirs()
        File(vDir, "topics").mkdirs()
        File(vDir, "recordings").mkdirs()
        File(vDir, "chats").mkdirs()
        vDir
    }

    val dailyDir: File get() = File(vaultDir, "daily")
    val topicsDir: File get() = File(vaultDir, "topics")
    val recordingsDir: File get() = File(vaultDir, "recordings")
    val chatsDir: File get() = File(vaultDir, "chats")

    /**
     * Lists all notes in the vault (daily notes and topics).
     */
    fun listNotes(): List<VaultNote> {
        val notes = mutableListOf<VaultNote>()
        if (!vaultDir.exists()) return emptyList()

        vaultDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .forEach { file ->
                try {
                    val content = file.readText(Charsets.UTF_8)
                    val relPath = file.relativeTo(vaultDir).path
                    val isDaily = file.parentFile?.name == "daily"
                    val dateStr = if (isDaily) file.nameWithoutExtension else null
                    val tags = extractTags(content)
                    val outLinks = extractWikiLinks(content)

                    notes.add(
                        VaultNote(
                            file = file,
                            title = file.nameWithoutExtension,
                            relativePath = relPath,
                            lastModifiedMs = file.lastModified(),
                            isDailyNote = isDaily,
                            dateStr = dateStr,
                            tags = tags,
                            outLinks = outLinks,
                            content = content
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading note: ${file.name}", e)
                }
            }

        return notes.sortedByDescending { it.lastModifiedMs }
    }

    /**
     * Gets or creates today's daily journal note.
     */
    fun getOrCreateDailyNote(date: Date = Date()): File {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = format.format(date)
        val file = File(dailyDir, "$dateStr.md")
        if (!file.exists()) {
            val initialTemplate = buildString {
                appendLine("# 📓 Daily Journal - $dateStr")
                appendLine()
                appendLine("## ⚡ Executive Summary")
                appendLine("_No summary generated yet._")
                appendLine()
                appendLine("## ✅ Action Items")
                appendLine("- [ ] Review today's recordings")
                appendLine()
                appendLine("## 🎙️ Speech Transcripts & Timeline")
                appendLine()
            }
            file.writeText(initialTemplate, Charsets.UTF_8)
        }
        return file
    }

    /**
     * Appends or replaces a transcribed recording segment into the corresponding daily note.
     * Guarantees that only one entry per recording exists in the daily note.
     */
    fun appendRecordingToDailyNote(
        recordingItem: RecordingItem,
        segments: List<SpeechSegmentData>,
        summary: String? = null
    ) {
        upsertRecordingToDailyNote(recordingItem, segments, summary)
    }

    /**
     * Resolves the actual chronological date of a recording from its sidecar,
     * filename timestamp (YYYYMMDD-HHMM), or fallback last modified timestamp.
     */
    fun getRecordingDate(recordingItem: RecordingItem): Date {
        val startedAt = recordingItem.sidecarData?.startedAtEpochMs
        if (startedAt != null && startedAt > 0L) {
            return Date(startedAt)
        }
        try {
            val base = recordingItem.baseName
            if (base.length >= 8 && base.substring(0, 8).all { it.isDigit() }) {
                val yyyy = base.substring(0, 4).toInt()
                val mm = base.substring(4, 6).toInt()
                val dd = base.substring(6, 8).toInt()
                val cal = Calendar.getInstance()
                cal.set(yyyy, mm - 1, dd)
                if (base.length >= 13 && base[8] == '-' && base.substring(9, 13).all { it.isDigit() }) {
                    val hh = base.substring(9, 11).toInt()
                    val min = base.substring(11, 13).toInt()
                    cal.set(Calendar.HOUR_OF_DAY, hh)
                    cal.set(Calendar.MINUTE, min)
                    cal.set(Calendar.SECOND, 0)
                }
                return cal.time
            }
        } catch (_: Exception) {}

        return Date(recordingItem.lastModifiedEpochMs)
    }

    /**
     * Upserts a transcribed recording into the corresponding daily note.
     * If the recording already exists in the daily note, it replaces the old section
     * in-place to ensure each recording appears exactly once without additive duplicates.
     */
    fun upsertRecordingToDailyNote(
        recordingItem: RecordingItem,
        segments: List<SpeechSegmentData>,
        summary: String? = null
    ) {
        val date = getRecordingDate(recordingItem)
        val dailyFile = getOrCreateDailyNote(date)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val startTimeStr = timeFormat.format(date)

        val newSection = buildString {
            appendLine("### 🎙️ [[${recordingItem.baseName}]] ($startTimeStr)")
            appendLine("**Audio:** `${recordingItem.audioFile.name}` | **Segments:** ${segments.size}")
            appendLine()

            if (!summary.isNullOrBlank()) {
                appendLine("> **Summary:** $summary")
                appendLine()
            }

            for (seg in segments) {
                val text = (seg.polishedText?.ifBlank { null } ?: seg.rawText?.ifBlank { null })
                    ?: continue  // Skip segments with no real speech content

                val segStartSec = (seg.audioStartMs / 1000)
                val mm = segStartSec / 60
                val ss = segStartSec % 60
                val timeStamp = String.format("%02d:%02d", mm, ss)
                val langBadge = seg.detectedLanguage?.uppercase() ?: "SPEECH"
                val speakerBadge = if (!seg.speaker.isNullOrBlank()) "**${seg.speaker}:** " else ""

                appendLine("- **[$timeStamp]** $speakerBadge`[$langBadge]` $text")
            }
        }.trimEnd()

        val sectionAnchor = "### 🎙️ [[${recordingItem.baseName}]]"

        // Also clean up any accidental instances of this recording in other daily files
        try {
            dailyDir.listFiles { f -> f.isFile && f.extension.equals("md", ignoreCase = true) && f.name != dailyFile.name }?.forEach { otherFile ->
                val otherContent = otherFile.readText(Charsets.UTF_8)
                if (otherContent.contains(sectionAnchor)) {
                    val cleaned = removeSectionFromContent(otherContent, sectionAnchor)
                    otherFile.writeText(cleaned.trimEnd() + "\n", Charsets.UTF_8)
                    Log.i(TAG, "Removed duplicate recording section ${recordingItem.baseName} from incorrect daily note: ${otherFile.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed cleaning other daily files", e)
        }

        val existingContent = dailyFile.readText(Charsets.UTF_8)

        // Collect start indices of all existing occurrences
        val occurrenceIndices = mutableListOf<Int>()
        var searchFrom = 0
        while (true) {
            val idx = existingContent.indexOf(sectionAnchor, searchFrom)
            if (idx == -1) break
            occurrenceIndices.add(idx)
            searchFrom = idx + sectionAnchor.length
        }

        val updatedContent: String
        if (occurrenceIndices.isEmpty()) {
            // No existing entry — append at end
            updatedContent = buildString {
                append(existingContent.trimEnd())
                appendLine()
                appendLine()
                appendLine(newSection)
            }
        } else {
            // Replace the FIRST occurrence in-place; delete all subsequent duplicates
            val sb = StringBuilder(existingContent)
            val toProcess = occurrenceIndices.toMutableList()

            fun sectionEnd(start: Int): Int {
                val nextHeader = Regex("(?m)^#{2,3}\\s", setOf(RegexOption.MULTILINE))
                    .find(existingContent, start + sectionAnchor.length)
                return nextHeader?.range?.first ?: existingContent.length
            }

            for (i in toProcess.indices.reversed()) {
                if (i == 0) continue  // keep first for replacement below
                val start = toProcess[i]
                val end = sectionEnd(start)
                val trimStart = if (start >= 2 && existingContent[start - 1] == '\n' && existingContent[start - 2] == '\n') start - 1 else start
                sb.delete(trimStart, end)
            }

            val updatedStr = sb.toString()
            val firstIdx = updatedStr.indexOf(sectionAnchor)
            if (firstIdx != -1) {
                val nextHeader = Regex("(?m)^#{2,3}\\s", setOf(RegexOption.MULTILINE))
                    .find(updatedStr, firstIdx + sectionAnchor.length)
                val firstEnd = nextHeader?.range?.first ?: updatedStr.length
                updatedContent = updatedStr.substring(0, firstIdx) +
                    newSection + "\n\n" +
                    updatedStr.substring(firstEnd)
            } else {
                updatedContent = updatedStr.trimEnd() + "\n\n" + newSection + "\n"
            }
        }

        dailyFile.writeText(updatedContent.trimEnd() + "\n", Charsets.UTF_8)
        Log.i(TAG, "Upserted single transcript for ${recordingItem.baseName} in ${dailyFile.name}")
    }

    private fun removeSectionFromContent(content: String, sectionAnchor: String): String {
        var result = content
        while (true) {
            val idx = result.indexOf(sectionAnchor)
            if (idx == -1) break
            val nextHeader = Regex("(?m)^#{2,3}\\s", setOf(RegexOption.MULTILINE))
                .find(result, idx + sectionAnchor.length)
            val end = nextHeader?.range?.first ?: result.length
            val trimStart = if (idx >= 2 && result[idx - 1] == '\n' && result[idx - 2] == '\n') idx - 1 else idx
            result = result.substring(0, trimStart) + result.substring(end)
        }
        return result
    }

    /**
     * Updates the Executive Summary and Action Items section in today's daily journal.
     */
    fun updateDailySummaryAndActions(date: Date, summary: String, actions: List<String>) {
        val dailyFile = getOrCreateDailyNote(date)
        var content = dailyFile.readText(Charsets.UTF_8)

        if (summary.isNotBlank()) {
            val summaryRegex = Regex("(?m)^##\\s+⚡\\s+Executive Summary\\s*\\n.*?(?=(^##\\s)|\\z)", RegexOption.DOT_MATCHES_ALL)
            val newSummaryBlock = "## ⚡ Executive Summary\n$summary\n\n"
            content = if (summaryRegex.containsMatchIn(content)) {
                summaryRegex.replace(content, newSummaryBlock)
            } else {
                content.replace("## ⚡ Executive Summary", "## ⚡ Executive Summary\n$summary")
            }
        }

        if (actions.isNotEmpty()) {
            val actionsFormatted = actions.joinToString("\n") { "- [ ] $it" }
            val actionsRegex = Regex("(?m)^##\\s+✅\\s+Action Items\\s*\\n.*?(?=(^##\\s)|\\z)", RegexOption.DOT_MATCHES_ALL)
            val newActionsBlock = "## ✅ Action Items\n$actionsFormatted\n\n"
            content = if (actionsRegex.containsMatchIn(content)) {
                actionsRegex.replace(content, newActionsBlock)
            } else {
                content.replace("## ✅ Action Items", "## ✅ Action Items\n$actionsFormatted")
            }
        }

        dailyFile.writeText(content, Charsets.UTF_8)
    }

    /**
     * Saves or creates a topic note.
     */
    fun saveTopicNote(topicName: String, content: String): File {
        val sanitized = topicName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
        val file = File(topicsDir, "$sanitized.md")
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    /**
     * Finds backlinks for a given note title.
     */
    fun getBacklinks(targetTitle: String): List<VaultNote> {
        val linkPattern = "[[${targetTitle.lowercase()}]]"
        return listNotes().filter { note ->
            note.title.lowercase() != targetTitle.lowercase() &&
            note.outLinks.any { it.lowercase() == targetTitle.lowercase() }
        }
    }

    /**
     * Executes a clean batch sync of all transcribed recordings into both
     * individual note files (vault/recordings/) and chronologically structured daily journals (vault/daily/).
     */
    suspend fun syncVaultBatch(
        onProgress: ((current: Int, total: Int, currentItem: String) -> Unit)? = null
    ): BatchSyncResult = withContext(Dispatchers.IO) {
        val storageManager = com.example.recme.storage.StorageManager(context)
        val recordings = storageManager.listRecordings()
        val transcribedItems = recordings.filter { 
            it.sidecarData != null && it.sidecarData.isTranscribed && it.sidecarData.segments.isNotEmpty()
        }.sortedBy { it.baseName }

        var recCount = 0
        var segmentCount = 0
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val processedDates = mutableSetOf<String>()

        val byDate = transcribedItems.groupBy { item ->
            val date = getRecordingDate(item)
            dateFormat.format(date)
        }

        val totalItems = transcribedItems.size

        for (item in transcribedItems) {
            val sidecar = item.sidecarData ?: continue
            recCount++
            segmentCount += sidecar.segments.size
            onProgress?.invoke(recCount, totalItems, item.baseName)

            // Export note to vault/recordings/
            com.example.recme.storage.TranscriptExporter.exportToObsidianMarkdown(
                item.audioFile,
                sidecar,
                recordingsDir
            )
            // Also maintain root directory .md export
            com.example.recme.storage.TranscriptExporter.exportToObsidianMarkdown(
                item.audioFile,
                sidecar
            )
        }

        // Rebuild clean daily note journals for each active date
        for ((dateStr, dayRecordings) in byDate) {
            processedDates.add(dateStr)
            val date = try {
                dateFormat.parse(dateStr) ?: Date()
            } catch (_: Exception) { Date() }

            val dailyFile = getOrCreateDailyNote(date)
            rebuildDailyNoteContent(dailyFile, dateStr, dayRecordings)
        }

        BatchSyncResult(
            recordingsExported = recCount,
            dailyNotesUpdated = processedDates.size,
            totalSpeechSegments = segmentCount,
            datesProcessed = processedDates.sorted().toList()
        )
    }

    private fun rebuildDailyNoteContent(
        dailyFile: File,
        dateStr: String,
        dayRecordings: List<RecordingItem>
    ) {
        val existingContent = if (dailyFile.exists()) dailyFile.readText(Charsets.UTF_8) else ""
        
        // Preserve existing summary if present
        val summaryRegex = Regex("(?m)^##\\s+⚡\\s+Executive Summary\\s*\\n(.*?)(?=(^##\\s)|\\z)", RegexOption.DOT_MATCHES_ALL)
        val existingSummary = summaryRegex.find(existingContent)?.groupValues?.get(1)?.trim()
            ?: "_No summary generated yet._"

        // Preserve existing action items if present
        val actionsRegex = Regex("(?m)^##\\s+✅\\s+Action Items\\s*\\n(.*?)(?=(^##\\s)|\\z)", RegexOption.DOT_MATCHES_ALL)
        val existingActions = actionsRegex.find(existingContent)?.groupValues?.get(1)?.trim()
            ?: "- [ ] Review today's recordings"

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val sortedRecs = dayRecordings.sortedBy { getRecordingDate(it).time }

        val newContent = buildString {
            appendLine("# 📓 Daily Journal - $dateStr")
            appendLine()
            appendLine("## ⚡ Executive Summary")
            appendLine(existingSummary)
            appendLine()
            appendLine("## ✅ Action Items")
            appendLine(existingActions)
            appendLine()
            appendLine("## 🎙️ Speech Transcripts & Timeline")
            appendLine()

            for (recItem in sortedRecs) {
                val sidecar = recItem.sidecarData ?: continue
                val recDate = getRecordingDate(recItem)
                val startTimeStr = timeFormat.format(recDate)
                val totalSec = sidecar.segments.lastOrNull()?.audioEndMs?.div(1000) ?: 0L
                val durMin = String.format(Locale.US, "%.1f", totalSec / 60.0f)
                val langs = sidecar.languagesDetected.ifEmpty { listOf("auto") }.joinToString(", ").uppercase()

                appendLine("### 🎙️ [[${recItem.baseName}]] ($startTimeStr)")
                appendLine("**Audio:** `${recItem.audioFile.name}` • **Duration:** ${durMin}m • **Languages:** $langs | **Segments:** ${sidecar.segments.size}")
                appendLine()

                for (seg in sidecar.segments) {
                    val text = (seg.polishedText?.ifBlank { null } ?: seg.rawText?.ifBlank { null })
                        ?: continue

                    val segStartSec = (seg.audioStartMs / 1000)
                    val mm = segStartSec / 60
                    val ss = segStartSec % 60
                    val timeStamp = String.format("%02d:%02d", mm, ss)
                    val langBadge = seg.detectedLanguage?.uppercase() ?: "SPEECH"
                    val speakerBadge = if (!seg.speaker.isNullOrBlank()) "**${seg.speaker}:** " else ""

                    appendLine("- **[$timeStamp]** $speakerBadge`[$langBadge]` $text")
                }
                appendLine()
            }
        }.trimEnd() + "\n"

        dailyFile.writeText(newContent, Charsets.UTF_8)
        Log.i(TAG, "Rebuilt clean daily journal: ${dailyFile.name} with ${sortedRecs.size} recordings")
    }

    companion object {
        private const val TAG = "VaultManager"
        const val KEY_AUTO_SYNC_VAULT = "key_auto_sync_vault"

        fun extractWikiLinks(text: String): List<String> {
            val regex = Regex("\\[\\[([^\\]]+)\\]\\]")
            return regex.findAll(text).map { it.groupValues[1].trim() }.distinct().toList()
        }

        fun extractTags(text: String): List<String> {
            val regex = Regex("#([a-zA-Z0-9_-]+)")
            return regex.findAll(text).map { it.groupValues[1].trim() }.distinct().toList()
        }
    }
}
