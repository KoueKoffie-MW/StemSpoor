package com.example.recme

import com.example.recme.vault.MarkdownElement
import com.example.recme.vault.MarkdownParser
import com.example.recme.vault.VaultManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMarkdownParserTest {

    @Test
    fun testParseMarkdownElements() {
        val markdown = """
            # Daily Journal
            ## Action Items
            - [ ] Task 1: Check Simscape
            - [x] Task 2: Done
            
            ## Timeline
            - **[01:30]** `[EN]` Testing speech recognition
            - **[02:15:30]** `[AF]` Afrikaanse praatjie
            
            > Important note on architecture
            - Regular bullet point
        """.trimIndent()

        val elements = MarkdownParser.parse(markdown)

        assertTrue(elements.any { it is MarkdownElement.Header && it.level == 1 && it.text == "Daily Journal" })
        assertTrue(elements.any { it is MarkdownElement.TaskItem && !it.isChecked && it.text == "Task 1: Check Simscape" })
        assertTrue(elements.any { it is MarkdownElement.TaskItem && it.isChecked && it.text == "Task 2: Done" })

        val audioItem1 = elements.filterIsInstance<MarkdownElement.AudioTimestampLine>().first()
        assertEquals("01:30", audioItem1.timestampStr)
        assertEquals(90000L, audioItem1.seekMs)
        assertEquals("EN", audioItem1.language)
        assertEquals("Testing speech recognition", audioItem1.text)

        val audioItem2 = elements.filterIsInstance<MarkdownElement.AudioTimestampLine>()[1]
        assertEquals("02:15:30", audioItem2.timestampStr)
        assertEquals(8130000L, audioItem2.seekMs)
    }

    @Test
    fun testToggleTaskCheckbox() {
        val original = """
            # Notes
            - [ ] Unfinished task
            - [x] Finished task
        """.trimIndent()

        val toggled1 = MarkdownParser.toggleTaskCheckbox(original, 1)
        assertTrue(toggled1.contains("- [x] Unfinished task"))

        val toggled2 = MarkdownParser.toggleTaskCheckbox(toggled1, 1)
        assertTrue(toggled2.contains("- [ ] Unfinished task"))
    }

    @Test
    fun testWikiLinksAndTagsExtraction() {
        val text = "Discussed [[Simscape]] multibody model with #engineering team and [[Gemma]] LLM #ai"
        val links = VaultManager.extractWikiLinks(text)
        val tags = VaultManager.extractTags(text)

        assertEquals(listOf("Simscape", "Gemma"), links)
        assertEquals(listOf("engineering", "ai"), tags)
    }
}
