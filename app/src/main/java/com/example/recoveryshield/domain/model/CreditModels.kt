package com.example.recoveryshield.domain.model

enum class CreditState {
    AVAILABLE,
    CONSUMED,
    EXPIRED
}

/**
 * Immutable protection credit entry in the append-only reward ledger.
 */
data class ProtectionCredit(
    val creditId: String,
    val earnedAtMillis: Long,
    val source: String,
    val valueDays: Int = 1,
    val state: CreditState = CreditState.AVAILABLE,
    val consumedAtMillis: Long? = null,
    val expiryPolicy: String = "NO_EXPIRY"
)

/**
 * Live state of rewarded ad readiness and reward grants.
 */
data class AdRewardState(
    val isAdAvailable: Boolean = false,
    val isShowingAd: Boolean = false,
    val totalCreditsEarned: Int = 0,
    val activeCreditDays: Int = 0,
    val lastRewardTimestampMillis: Long? = null
)
