package com.example

import com.example.recoveryshield.protection.detector.EventNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventNormalizerTest {

    @Test
    fun `sanitizeAndTokenize strips diacritics and converts to lowercase`() {
        val input = "Café & Résumé"
        val output = EventNormalizer.sanitizeAndTokenize(input)
        assertEquals("cafe & resume", output)
    }

    @Test
    fun `sanitizeAndTokenize handles blank inputs gracefully`() {
        assertNull(EventNormalizer.sanitizeAndTokenize(null))
        assertNull(EventNormalizer.sanitizeAndTokenize("   "))
    }
}
