package com.example.recoveryshield.protection.detector

import com.example.recoveryshield.domain.model.DetectionDecision
import com.example.recoveryshield.domain.model.NormalizedUiEvent

data class SyntheticFixture(
    val name: String,
    val event: NormalizedUiEvent,
    val expectedDecision: DetectionDecision,
    val description: String
)

data class HarnessEvaluationResult(
    val totalFixtures: Int,
    val passed: Int,
    val failed: Int,
    val truePositives: Int,
    val falsePositives: Int,
    val trueNegatives: Int,
    val falseNegatives: Int,
    val precision: Float,
    val recall: Float,
    val details: List<String>
)

object SyntheticDetectorHarness {

    fun getStandardFixtures(): List<SyntheticFixture> {
        return listOf(
            SyntheticFixture(
                name = "High-Confidence Direct Explicit",
                event = NormalizedUiEvent(
                    packageName = "com.android.chrome",
                    textFragments = listOf("hardcore", "pornography", "stream", "video"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.HIGH_CONFIDENCE_TRIGGER,
                description = "Clear explicit indicators without recovery context"
            ),
            SyntheticFixture(
                name = "Adversarial Spacing and Punctuation",
                event = NormalizedUiEvent(
                    packageName = "com.android.chrome",
                    textFragments = listOf("p o r n", "h a r d c o r e", "v-i-d-e-o"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.HIGH_CONFIDENCE_TRIGGER,
                description = "Spaced and hyphenated obfuscation should be deobfuscated"
            ),
            SyntheticFixture(
                name = "Leetspeak Obfuscation",
                event = NormalizedUiEvent(
                    packageName = "com.example.browser",
                    textFragments = listOf("p0rn0", "xxx", "h4rdc0re"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.HIGH_CONFIDENCE_TRIGGER,
                description = "Common number-for-letter leetspeak substitution"
            ),
            SyntheticFixture(
                name = "Benign Substring Match (Corner, Hornet, Corn)",
                event = NormalizedUiEvent(
                    packageName = "com.google.android.youtube",
                    textFragments = listOf("street corner bakery", "giant hornet documentary", "corn on the cob recipe"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.NO_ACTION,
                description = "Boundary regexes should prevent false positive triggers on substrings"
            ),
            SyntheticFixture(
                name = "Recovery and Educational Context",
                event = NormalizedUiEvent(
                    packageName = "com.recovery.group",
                    textFragments = listOf("pornography addiction recovery group", "quitting porn workshop", "healing journal"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.SUPPRESS_FOR_CONTEXT,
                description = "Educational and recovery keywords should suppress triggers"
            ),
            SyntheticFixture(
                name = "Medical and Clinical Context",
                event = NormalizedUiEvent(
                    packageName = "org.wikipedia",
                    textFragments = listOf("sexual health clinic", "breast cancer awareness research"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.SUPPRESS_FOR_CONTEXT,
                description = "Medical contexts should not trigger aggressive blocking"
            ),
            SyntheticFixture(
                name = "Noisy General Social Feed",
                event = NormalizedUiEvent(
                    packageName = "com.social.app",
                    textFragments = listOf("good morning friends", "check out my new workout shoes", "coffee time"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.NO_ACTION,
                description = "General benign text should result in NO_ACTION"
            ),
            SyntheticFixture(
                name = "Ambiguous Single Term (Erotica / Sensual)",
                event = NormalizedUiEvent(
                    packageName = "com.books.reader",
                    textFragments = listOf("classical renaissance art", "sensual poetry", "library catalog"),
                    isSyntheticFixture = true
                ),
                expectedDecision = DetectionDecision.POSSIBLE_TRIGGER,
                description = "Single ambiguous term produces at most POSSIBLE_TRIGGER, never HIGH_CONFIDENCE"
            )
        )
    }

    fun evaluate(detector: LocalClassifier = DeterministicRuleDetector()): HarnessEvaluationResult {
        val fixtures = getStandardFixtures()
        var passed = 0
        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0
        val details = mutableListOf<String>()

        fixtures.forEach { fixture ->
            detector.resetTemporalState()
            val result = detector.classify(fixture.event)
            val isMatch = result.decision == fixture.expectedDecision

            if (isMatch) {
                passed++
                details.add("PASS: [${fixture.name}] -> Decision: ${result.decision}")
            } else {
                details.add("FAIL: [${fixture.name}] -> Expected: ${fixture.expectedDecision}, Got: ${result.decision} (${result.diagnosticSafeSummary})")
            }

            val isExpectedPositive = fixture.expectedDecision == DetectionDecision.HIGH_CONFIDENCE_TRIGGER
            val isActualPositive = result.decision == DetectionDecision.HIGH_CONFIDENCE_TRIGGER

            when {
                isExpectedPositive && isActualPositive -> tp++
                !isExpectedPositive && isActualPositive -> fp++
                !isExpectedPositive && !isActualPositive -> tn++
                isExpectedPositive && !isActualPositive -> fn++
            }
        }

        val precision = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 1.0f
        val recall = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 1.0f

        return HarnessEvaluationResult(
            totalFixtures = fixtures.size,
            passed = passed,
            failed = fixtures.size - passed,
            truePositives = tp,
            falsePositives = fp,
            trueNegatives = tn,
            falseNegatives = fn,
            precision = precision,
            recall = recall,
            details = details
        )
    }
}
