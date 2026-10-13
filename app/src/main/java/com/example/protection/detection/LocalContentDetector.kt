package com.example.protection.detection

import com.example.recoveryshield.protection.detector.DeterministicRuleDetector

enum class DetectionDecision {
    SAFE,
    AMBIGUOUS_MONITOR,
    HIGH_CONFIDENCE_TRIGGER
}

data class DetectionResult(
    val decision: DetectionDecision,
    val confidenceScore: Float,
    val matchedSignalsCount: Int,
    val diagnosticSafeSummary: String
)

class LocalContentDetector {
    private val underlyingDetector = DeterministicRuleDetector()

    fun detect(text: String, pkg: String = ""): DetectionResult {
        val dummyEvent = com.example.recoveryshield.domain.model.NormalizedUiEvent(
            packageName = pkg,
            textFragments = listOf(text),
            contentDescriptions = emptyList(),
            timestampMillis = System.currentTimeMillis()
        )
        val res = underlyingDetector.classify(dummyEvent)
        val decision = when (res.decision) {
            com.example.recoveryshield.domain.model.DetectionDecision.HIGH_CONFIDENCE_TRIGGER -> DetectionDecision.HIGH_CONFIDENCE_TRIGGER
            com.example.recoveryshield.domain.model.DetectionDecision.POSSIBLE_TRIGGER -> DetectionDecision.AMBIGUOUS_MONITOR
            else -> DetectionDecision.SAFE
        }
        return DetectionResult(
            decision = decision,
            confidenceScore = res.confidence,
            matchedSignalsCount = res.signals.size,
            diagnosticSafeSummary = res.diagnosticSafeSummary
        )
    }
}

