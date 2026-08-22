package com.example.recme.vault

sealed class MarkdownElement {
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class TaskItem(val lineIndex: Int, val isChecked: Boolean, val text: String) : MarkdownElement()
    data class AudioTimestampLine(
        val lineIndex: Int,
        val timestampStr: String,
        val seekMs: Long,
        val language: String?,
        val text: String
    ) : MarkdownElement()
    data class Quote(val text: String) : MarkdownElement()
    data class Bullet(val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data object Divider : MarkdownElement()
}

/**
 * Parser for interactive Obsidian GFM markdown notes with audio timestamp jump points.
 */
object MarkdownParser {

    private val audioTimestampRegex = Regex("-\\s*\\*\\*\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)\\]\\*\\*(?:\\s*`\\[([^\\]]+)\\]`)?\\s*(.*)")
    private val taskRegex = Regex("^-\\s*\\[([ xX])\\]\\s*(.*)")

    fun parse(content: String): List<MarkdownElement> {
        val elements = mutableListOf<MarkdownElement>()
        val lines = content.lines()

        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 1. Audio Timeline Item: - **[01:23]** `[EN]` Sample text
            val audioMatch = audioTimestampRegex.matchEntire(trimmed)
            if (audioMatch != null) {
                val timeStr = audioMatch.groupValues[1]
                val lang = audioMatch.groupValues[2].ifBlank { null }
                val text = audioMatch.groupValues[3]
                val seekMs = parseTimestampToMs(timeStr)
                elements.add(MarkdownElement.AudioTimestampLine(idx, timeStr, seekMs, lang, text))
                continue
            }

            // 2. Task Item: - [ ] Task or - [x] Done
            val taskMatch = taskRegex.matchEntire(trimmed)
            if (taskMatch != null) {
                val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                val text = taskMatch.groupValues[2]
                elements.add(MarkdownElement.TaskItem(idx, isChecked, text))
                continue
            }

            // 3. Headers: # H1, ## H2, ### H3
            if (trimmed.startsWith("### ")) {
                elements.add(MarkdownElement.Header(3, trimmed.removePrefix("### ")))
            } else if (trimmed.startsWith("## ")) {
                elements.add(MarkdownElement.Header(2, trimmed.removePrefix("## ")))
            } else if (trimmed.startsWith("# ")) {
                elements.add(MarkdownElement.Header(1, trimmed.removePrefix("# ")))
            } else if (trimmed.startsWith("> ")) {
                elements.add(MarkdownElement.Quote(trimmed.removePrefix("> ")))
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                elements.add(MarkdownElement.Bullet(trimmed.substring(2)))
            } else if (trimmed == "---" || trimmed == "***") {
                elements.add(MarkdownElement.Divider)
            } else {
                elements.add(MarkdownElement.Paragraph(trimmed))
            }
        }

        return elements
    }

    /**
     * Converts a timestamp formatted like "01:23" or "01:23:45" to milliseconds.
     */
    fun parseTimestampToMs(timeStr: String): Long {
        val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    /**
     * Toggles a checkbox on a specific line index in raw markdown content.
     */
    fun toggleTaskCheckbox(content: String, targetLineIndex: Int): String {
        val lines = content.lines().toMutableList()
        if (targetLineIndex in lines.indices) {
            val line = lines[targetLineIndex]
            val updated = if (line.contains("- [ ]")) {
                line.replace("- [ ]", "- [x]")
            } else if (line.contains("- [x]") || line.contains("- [X]")) {
                line.replace(Regex("-\\s*\\[[xX]\\]"), "- [ ]")
            } else {
                line
            }
            lines[targetLineIndex] = updated
        }
        return lines.joinToString("\n")
    }
}
