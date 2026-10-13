package com.example.recoveryshield.domain.model

/**
 * State of an active intervention.
 */
data class InterventionState(
    val isActive: Boolean = false,
    val interventionId: String = "",
    val triggerTimestampMillis: Long = 0L,
    val countdownRemainingSeconds: Int = 5,
    val statusMessage: String = "",
    val navigationAttempted: Boolean = false,
    val fallbackRequired: Boolean = false,
    val isAntiTamper: Boolean = false
)
