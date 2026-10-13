package com.example.recoveryshield.domain.model

/**
 * Status of a voluntary disable request.
 */
enum class DisableRequestStatus {
    PENDING_COOLDOWN,
    EXPIRED_AWAITING_CONFIRMATION,
    CONFIRMED_DISABLED,
    CANCELLED
}

/**
 * Persisted voluntary disable request with configured cooling-off delay.
 */
data class DisableRequest(
    val requestId: String,
    val createdAtMillis: Long,
    val cooldownDurationMillis: Long,
    val expiresAtMillis: Long,
    val status: DisableRequestStatus = DisableRequestStatus.PENDING_COOLDOWN
)

/**
 * Live cooldown state computed against current time source.
 */
data class CooldownState(
    val hasActiveRequest: Boolean = false,
    val requestId: String? = null,
    val requestedAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val isExpiredAwaitingConfirmation: Boolean = false,
    val status: DisableRequestStatus = DisableRequestStatus.CANCELLED
) {
    val remainingHours: Long get() = remainingMillis / (1000 * 60 * 60)
    val remainingMinutes: Long get() = (remainingMillis / (1000 * 60)) % 60
    val remainingSeconds: Long get() = (remainingMillis / 1000) % 60
}
