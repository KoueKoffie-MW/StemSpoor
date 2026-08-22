package com.example.recme.storage

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports transcripts to Obsidian / Second-Brain compatible Markdown files (.md)
 * and updates companion sidecar JSON metadata.
 */
object TranscriptExporter {
    private const val TAG = "TranscriptExporter"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Exports a clean, formatted Markdown document (.md) suitable for Obsidian / Notion.
     */
    fun exportToObsidianMarkdown(
        audioFile: File,
        sidecarData: SidecarData,
        destinationDir: File = audioFile.parentFile ?: File(".")
    ): File {
        val baseName = audioFile.nameWithoutExtension
        val mdFile = File(destinationDir, "$baseName.md")

        val dateStr = dateFormat.format(Date(sidecarData.startedAtEpochMs))
        val totalSec = sidecarData.segments.lastOrNull()?.audioEndMs?.div(1000) ?: 0L
        val durationMin = String.format(Locale.US, "%.1f", totalSec / 60.0f)
        val languages = sidecarData.languagesDetected.ifEmpty { listOf("auto") }

        val content = buildString {
            // Frontmatter
            appendLine("---")
            appendLine("date: $dateStr")
            appendLine("title: \"Recording $baseName\"")
            appendLine("session_id: \"${sidecarData.recordingSessionId}\"")
            appendLine("audio_file: \"${audioFile.name}\"")
            appendLine("duration_minutes: $durationMin")
            appendLine("languages: [${languages.joinToString(", ")}]")
            appendLine("tags: [recme, voice-note, transcript]")
            appendLine("---")
            appendLine()
            appendLine("# Recording $baseName")
            appendLine()
            appendLine("> **Date:** $dateStr • **Duration:** ${durationMin}m • **Languages:** ${languages.joinToString(", ").uppercase()}")
            appendLine()
            appendLine("---")
            appendLine()

            if (sidecarData.segments.isEmpty()) {
                appendLine("*No speech segments recorded.*")
            } else {
                for (segment in sidecarData.segments) {
                    val timeStr = timeFormat.format(Date(segment.speechStartEpochMs))
                    val langTag = segment.detectedLanguage?.uppercase() ?: "SPEECH"
                    val text = segment.polishedText ?: segment.rawText ?: "(Inaudible / Silence)"
                    val speakerPrefix = if (!segment.speaker.isNullOrBlank()) "**${segment.speaker}:** " else ""

                    appendLine("**[$timeStr]** $speakerPrefix`[$langTag]` $text")
                    appendLine()
                }
            }
        }

        mdFile.writeText(content, Charsets.UTF_8)
        Log.i(TAG, "Exported Obsidian markdown note: ${mdFile.absolutePath}")
        return mdFile
    }

    /**
     * Updates an existing companion .json sidecar with enriched transcript data.
     */
    fun updateSidecarJson(
        jsonFile: File,
        updatedSegments: List<SpeechSegmentData>,
        languagesDetected: List<String>
    ): SidecarData? {
        if (!jsonFile.exists()) return null

        return try {
            val content = jsonFile.readText(Charsets.UTF_8)
            val sidecar = json.decodeFromString<SidecarData>(content)
            val updated = sidecar.copy(
                isTranscribed = true,
                languagesDetected = languagesDetected,
                segments = updatedSegments
            )
            jsonFile.writeText(json.encodeToString(SidecarData.serializer(), updated))
            Log.i(TAG, "Updated sidecar JSON with transcript: ${jsonFile.name}")
            updated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sidecar JSON with transcript", e)
            null
        }
    }
}
