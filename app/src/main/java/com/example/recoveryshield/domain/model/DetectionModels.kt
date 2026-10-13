package com.example.recoveryshield.domain.model

/**
 * Output decision produced by the local detection pipeline.
 */
enum class DetectionDecision {
    /** No explicit indicators found. */
    NO_ACTION,

    /** Low-confidence or ambiguous indicators found; do NOT trigger an aggressive block. */
    POSSIBLE_TRIGGER,

    /** High-confidence explicit sexual content detected; trigger immediate intervention. */
    HIGH_CONFIDENCE_TRIGGER,

    /** Suppressed due to detected safe context (e.g. recovery group, educational, medical). */
    SUPPRESS_FOR_CONTEXT,

    /** Suppressed due to active cooldown or recent false-positive report window. */
    TEMPORARY_COOLDOWN
}

/**
 * Context category inferred for the current UI surface.
 */
enum class ContextCategory {
    GENERAL_CONTENT,
    SYSTEM_SETTINGS,
    RECOVERY_OR_EDUCATIONAL,
    COMMUNICATION_APP,
    BROWSER_VIEW
}

/**
 * Normalized UI event extracted from accessibility signals.
 * Contains only minimal, sanitized, privacy-safe metadata.
 * Raw screen text and screenshots are NEVER stored.
 */
data class NormalizedUiEvent(
    val timestampMillis: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val className: String = "",
    val eventType: Int = 0,
    val textFragments: List<String> = emptyList(),
    val contentDescriptions: List<String> = emptyList(),
    val viewIds: List<String> = emptyList(),
    val windowRole: String = "unknown",
    val isTransient: Boolean = false,
    val isSyntheticFixture: Boolean = false
)

/**
 * Individual signal layers feeding the multi-signal detection pipeline.
 */
sealed class DetectionSignal {
    data class Lexical(
        val confidence: Float,
        val matchCount: Int,
        val matchedCategories: List<String>
    ) : DetectionSignal()

    data class Contextual(
        val category: ContextCategory,
        val safeContextFactor: Float
    ) : DetectionSignal()

    data class RepetitionDensity(
        val recentHitsCount: Int,
        val densityFactor: Float
    ) : DetectionSignal()

    data class ModelScore(
        val score: Float,
        val modelIdentifier: String
    ) : DetectionSignal()
}

/**
 * Final output of detection pipeline evaluation.
 */
data class DetectionResult(
    val decision: DetectionDecision,
    val confidence: Float,
    val signals: List<DetectionSignal> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val diagnosticSafeSummary: String = ""
)

/**
 * Configurable thresholds and policy settings for the detection engine.
 */
data class DetectionPolicy(
    val highConfidenceThreshold: Float = 0.85f,
    val possibleTriggerThreshold: Float = 0.50f,
    val densityBoostWindowMillis: Long = 10_000L,
    val densityBoostStep: Float = 0.15f,
    val enableSafeContextSuppression: Boolean = true,
    val safeContextDiscount: Float = 0.60f
)
