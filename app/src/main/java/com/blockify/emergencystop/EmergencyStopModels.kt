package com.blockify.emergencystop

/**
 * Status returned by the secure backend verification.
 */
enum class EmergencyCodeValidationStatus {
    SUCCESS,
    INVALID_CODE,
    EXPIRED_CODE,
    ALREADY_USED,
    RATE_LIMITED,
    NETWORK_ERROR,
    SERVER_ERROR
}

/**
 * Result of the emergency stop execution.
 */
data class EmergencyStopResult(
    val status: EmergencyCodeValidationStatus,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Processing steps displayed during verification.
 */
enum class EmergencyStopStep(val label: String) {
    VALIDATING_CODE("Validating code"),
    STOPPING_SERVICES("Stopping protection services"),
    DISABLING_MONITORING("Disabling protection monitoring"),
    REMOVING_OVERLAYS("Removing overlays")
}

/**
 * High-level screen states for Emergency Stop Flow.
 */
enum class EmergencyStopUiState {
    INPUT,
    VERIFYING,
    INVALID,
    SUCCESS_STOPPED,
    FINAL_STOPPED,
    SUPPORT
}
