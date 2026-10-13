package com.example.recoveryshield.points

import com.example.recoveryshield.data.local.RecoveryDatabase
import com.example.recoveryshield.data.local.RewardLedgerEntity
import com.example.recoveryshield.domain.model.CreditState
import com.example.recoveryshield.domain.model.ProtectionCredit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Append-only reward ledger interface.
 */
interface RewardLedger {
    fun observeCredits(): Flow<List<ProtectionCredit>>
    suspend fun recordCredit(credit: ProtectionCredit)
    suspend fun getAvailableCreditsCount(): Int
    suspend fun consumeCredit(creditId: String): Boolean
}

class RoomRewardLedger(
    private val database: RecoveryDatabase
) : RewardLedger {

    override fun observeCredits(): Flow<List<ProtectionCredit>> {
        return database.rewardLedgerDao().observeAllCredits().map { list ->
            list.map { entity ->
                ProtectionCredit(
                    creditId = entity.creditId,
                    earnedAtMillis = entity.earnedAtMillis,
                    source = entity.source,
                    valueDays = entity.valueDays,
                    state = runCatching { CreditState.valueOf(entity.state) }.getOrDefault(CreditState.AVAILABLE),
                    consumedAtMillis = entity.consumedAtMillis,
                    expiryPolicy = entity.expiryPolicy
                )
            }
        }
    }

    override suspend fun recordCredit(credit: ProtectionCredit) {
        database.rewardLedgerDao().insertCredit(
            RewardLedgerEntity(
                creditId = credit.creditId,
                earnedAtMillis = credit.earnedAtMillis,
                source = credit.source,
                valueDays = credit.valueDays,
                state = credit.state.name,
                consumedAtMillis = credit.consumedAtMillis,
                expiryPolicy = credit.expiryPolicy
            )
        )
    }

    override suspend fun getAvailableCreditsCount(): Int {
        return database.rewardLedgerDao().countAvailableCredits()
    }

    override suspend fun consumeCredit(creditId: String): Boolean {
        database.rewardLedgerDao().updateCreditState(
            creditId = creditId,
            newState = CreditState.CONSUMED.name,
            timestamp = System.currentTimeMillis()
        )
        return true
    }
}
