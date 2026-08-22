package com.example.recme

import com.example.recme.ai.whisper.WhisperTokenizer
import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperTokenizerTest {

    @Test
    fun testLanguageTokens() {
        assertEquals(50327, WhisperTokenizer.getLanguageToken("af"))
        assertEquals(50261, WhisperTokenizer.getLanguageToken("de"))
        assertEquals(50259, WhisperTokenizer.getLanguageToken("en"))
        assertEquals(50286, WhisperTokenizer.getLanguageToken("nl"))
    }

    @Test
    fun testSpecialTokens() {
        assertEquals(50257, WhisperTokenizer.EOT_TOKEN)
        assertEquals(50258, WhisperTokenizer.SOT_TOKEN)
        assertEquals(50359, WhisperTokenizer.TRANSCRIBE_TOKEN)
        assertEquals(50363, WhisperTokenizer.NO_TIMESTAMPS_TOKEN)
    }
}
