package com.example.recoveryshield.domain.model

/**
 * Privacy-safe operational event codes. No raw screen content is EVER recorded.
 */
enum class SafeEventCode {
    SETUP_STARTED,
    PERMISSION_REQUESTED,
    PERMISSION_GRANTED,
    SERVICE_STARTED,
    SERVICE_STOPPED,
    DETECTOR_READY,
    DETECTOR_DEGRADED,
    HIGH_CONFIDENCE_INTERVENTION,
    FALSE_POSITIVE_REPORTED,
    DISABLE_REQUESTED,
    COOLDOWN_STARTED,
    COOLDOWN_COMPLETED,
    PROTECTION_DISABLED,
    REWARD_EARNED,
    REWARD_REJECTED
}

data class EventLog(
    val id: Long = 0,
    val eventCode: SafeEventCode,
    val timestampMillis: Long = System.currentTimeMillis(),
    val safeMetadata: String = ""
)

data class DeviceCompatibility(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val accessibilityEnabled: Boolean,
    val overlayCapable: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val isMiuiOrOemRestricted: Boolean = false
)

data class DiagnosticsSummary(
    val eventsReceivedCount: Long = 0,
    val eventsProcessedCount: Long = 0,
    val eventsSkippedCount: Long = 0,
    val detectorInvocationsCount: Long = 0,
    val interventionsLaunchedCount: Long = 0,
    val falsePositivesReportedCount: Long = 0,
    val lastSafeEvent: SafeEventCode? = null,
    val lastDecisionSummary: String = "None"
)

data class FalsePositiveReport(
    val reportId: String,
    val timestampMillis: Long,
    val category: String,
    val safeSuppressionToken: String? = null
)
