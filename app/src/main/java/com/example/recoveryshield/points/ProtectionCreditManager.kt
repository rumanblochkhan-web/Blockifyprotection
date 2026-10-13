package com.example.recoveryshield.points

import com.example.recoveryshield.ads.AdProvider
import com.example.recoveryshield.ads.AdShowResult
import com.example.recoveryshield.data.repository.ProtectionRepository
import com.example.recoveryshield.domain.model.AdRewardState
import com.example.recoveryshield.domain.model.CreditState
import com.example.recoveryshield.domain.model.ProtectionCredit
import com.example.recoveryshield.domain.model.SafeEventCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ProtectionCreditManager(
    private val adProvider: AdProvider,
    private val rewardLedger: RewardLedger,
    private val repository: ProtectionRepository
) {
    private val _rewardState = MutableStateFlow(AdRewardState())
    val rewardState: StateFlow<AdRewardState> = _rewardState.asStateFlow()

    suspend fun refreshLedgerState() {
        val count = rewardLedger.getAvailableCreditsCount()
        _rewardState.value = _rewardState.value.copy(
            isAdAvailable = true,
            totalCreditsEarned = count,
            activeCreditDays = count
        )
    }

    /**
     * Voluntarily watch a rewarded ad to earn a protection credit.
     * Guaranteed safe:
     * - Only granted when provider returns Completed.
     * - Early cancel or error yields zero reward.
     * - Never shown during an active intervention.
     */
    suspend fun watchAdForCredit(isInterventionActive: Boolean): Boolean {
        if (isInterventionActive) {
            repository.recordAuditEvent(SafeEventCode.REWARD_REJECTED, "Cannot watch ads during intervention")
            return false
        }

        // Check if 24h limit has elapsed
        val plan = repository.getProtectionPlan()
        if (!plan.canWatchRewardedAd) {
            repository.recordAuditEvent(SafeEventCode.REWARD_REJECTED, "24h rewarded ad limit in effect")
            return false
        }

        _rewardState.value = _rewardState.value.copy(isShowingAd = true)
        val result = adProvider.showRewardedAd()
        _rewardState.value = _rewardState.value.copy(isShowingAd = false)

        return when (result) {
            is AdShowResult.Completed -> {
                val credit = ProtectionCredit(
                    creditId = UUID.randomUUID().toString(),
                    earnedAtMillis = System.currentTimeMillis(),
                    source = "REWARDED_AD",
                    valueDays = 1,
                    state = CreditState.AVAILABLE
                )
                rewardLedger.recordCredit(credit)
                // Extend protection by 1 authoritative day
                repository.addProtectionDays(1, "REWARDED_AD")
                repository.recordAuditEvent(SafeEventCode.REWARD_EARNED, "CreditId: ${credit.creditId}")
                refreshLedgerState()
                true
            }
            is AdShowResult.Cancelled -> {
                repository.recordAuditEvent(SafeEventCode.REWARD_REJECTED, "Ad cancelled: ${result.reason}")
                false
            }
            is AdShowResult.Error -> {
                repository.recordAuditEvent(SafeEventCode.REWARD_REJECTED, "Ad error: ${result.message}")
                false
            }
        }
    }
}
