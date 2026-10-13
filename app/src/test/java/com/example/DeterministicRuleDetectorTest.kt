package com.example

import com.example.recoveryshield.domain.model.DetectionDecision
import com.example.recoveryshield.domain.model.NormalizedUiEvent
import com.example.recoveryshield.protection.detector.DeterministicRuleDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeterministicRuleDetectorTest {

    private lateinit var detector: DeterministicRuleDetector

    @Before
    fun setUp() {
        detector = DeterministicRuleDetector()
    }

    @Test
    fun `direct explicit content triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.browser.app",
            textFragments = listOf("free", "pornography", "stream", "hardcore")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun `adversarial spaced and punctuated content is deobfuscated and triggers`() {
        val event = NormalizedUiEvent(
            packageName = "com.browser.app",
            textFragments = listOf("p o r n", "h a r d c o r e")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
    }

    @Test
    fun `leetspeak content is normalized and triggers`() {
        val event = NormalizedUiEvent(
            packageName = "com.browser.app",
            textFragments = listOf("p0rn0", "xxx", "h4rdc0re")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
    }

    @Test
    fun `benign words with common substrings do not false positive`() {
        val event = NormalizedUiEvent(
            packageName = "com.social.app",
            textFragments = listOf("street corner", "giant hornet", "sweet corn recipe")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.NO_ACTION, result.decision)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `recovery educational context is suppressed`() {
        val event = NormalizedUiEvent(
            packageName = "com.recovery.group",
            textFragments = listOf("pornography addiction recovery meeting", "quitting porn support")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.SUPPRESS_FOR_CONTEXT, result.decision)
    }

    @Test
    fun `clinical and health context is suppressed`() {
        val event = NormalizedUiEvent(
            packageName = "org.wikipedia",
            textFragments = listOf("sexual health clinic", "breast cancer awareness")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.SUPPRESS_FOR_CONTEXT, result.decision)
    }

    @Test
    fun `opening android settings with Porn Protection visible does not trigger intervention`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.settings",
            textFragments = listOf("Settings", "Accessibility", "Porn Protection", "Downloaded apps", "Display", "Sound")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.SUPPRESS_FOR_CONTEXT, result.decision)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `attempting to uninstall Porn Protection in settings triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.settings",
            textFragments = listOf("App info", "Porn Protection", "Uninstall", "Force stop")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
    }

    @Test
    fun `attempting to turn off accessibility for Porn Protection in settings triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.settings",
            textFragments = listOf("Accessibility", "Porn Protection", "Stop Porn Protection?", "Turn off service")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
    }

    @Test
    fun `packageinstaller uninstall prompt triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.google.android.packageinstaller",
            textFragments = listOf("Do you want to uninstall this app?", "Porn Protection", "Cancel", "OK")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
    }

    @Test
    fun `deactivating service switch in accessibility settings triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.settings",
            textFragments = listOf("Use Porn Protection", "Stop", "Turn off", "Cancel")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
    }

    @Test
    fun `entering Porn Protection accessibility settings page triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.settings",
            textFragments = listOf("Porn Protection", "Downloaded services", "Use service", "Shortcut")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
    }

    @Test
    fun `explicit adult video keywords in browser triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.chrome",
            textFragments = listOf("Search or type URL", "watch porn", "free sex video")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun `sexual terms like big ass or romantic sex triggers HIGH_CONFIDENCE_TRIGGER`() {
        val event = NormalizedUiEvent(
            packageName = "com.instagram.android",
            textFragments = listOf("Post", "look at this big ass", "hot romance")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun `TEST_TRIGGER fires HIGH_CONFIDENCE_TRIGGER instantly`() {
        val event = NormalizedUiEvent(
            packageName = "com.google.android.youtube",
            textFragments = listOf("Search", "TEST_TRIGGER")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.90f)

        val instant = com.example.recoveryshield.protection.detector.InstantKeywordMatcher.findInstantTrigger("TEST_TRIGGER")
        assertEquals("TEST_TRIGGER", instant)
    }

    @Test
    fun `single word porn fires HIGH_CONFIDENCE_TRIGGER instantly`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.chrome",
            textFragments = listOf("porn")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.90f)

        val instant = com.example.recoveryshield.protection.detector.InstantKeywordMatcher.findInstantTrigger("porn")
        assertEquals("porn", instant)
    }

    @Test
    fun `single word sex fires HIGH_CONFIDENCE_TRIGGER instantly`() {
        val event = NormalizedUiEvent(
            packageName = "com.google.android.youtube",
            textFragments = listOf("sex")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.90f)

        val instant = com.example.recoveryshield.protection.detector.InstantKeywordMatcher.findInstantTrigger("sex")
        assertEquals("sex", instant)
    }

    @Test
    fun `repeated triggering works consecutively without getting blocked or silenced`() {
        for (i in 1..5) {
            val event = NormalizedUiEvent(
                packageName = "com.android.chrome",
                textFragments = listOf("porn")
            )
            val result = detector.classify(event)
            assertEquals("Iteration $i must trigger", DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        }
    }

    @Test
    fun `entering Blockify App Info screen triggers anti-tamper before uninstall click`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.settings",
            textFragments = listOf("App info", "Blockify", "Open", "Uninstall", "Force stop", "Storage & cache")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
        assertTrue(detector.isTamperOrDeactivationAttempt(event))
    }

    @Test
    fun `entering Blockify accessibility settings page triggers anti-tamper before turn off click`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.settings",
            textFragments = listOf("Blockify", "Use service", "Service is on", "Shortcut", "Allow full control")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
        assertTrue(detector.isTamperOrDeactivationAttempt(event))
    }

    @Test
    fun `viewing Blockify in Play Store with uninstall option triggers anti-tamper`() {
        val event = NormalizedUiEvent(
            packageName = "com.android.vending",
            textFragments = listOf("Blockify: Porn & Adult Blocker", "Uninstall", "Open", "What's new")
        )
        val result = detector.classify(event)
        assertEquals(DetectionDecision.HIGH_CONFIDENCE_TRIGGER, result.decision)
        assertTrue(result.confidence >= 0.95f)
        assertTrue(detector.isTamperOrDeactivationAttempt(event))
    }
}
