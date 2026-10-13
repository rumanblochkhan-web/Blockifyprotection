package com.example.recoveryshield.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectionDao {
    @Query("SELECT * FROM protection_state WHERE id = 1")
    fun observeState(): Flow<ProtectionStateEntity?>

    @Query("SELECT * FROM protection_state WHERE id = 1")
    suspend fun getState(): ProtectionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: ProtectionStateEntity)
}

@Dao
interface DisableRequestDao {
    @Query("SELECT * FROM disable_requests WHERE status IN ('PENDING_COOLDOWN', 'EXPIRED_AWAITING_CONFIRMATION') ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeActiveRequest(): Flow<DisableRequestEntity?>

    @Query("SELECT * FROM disable_requests WHERE status IN ('PENDING_COOLDOWN', 'EXPIRED_AWAITING_CONFIRMATION') ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getActiveRequest(): DisableRequestEntity?

    @Query("SELECT * FROM disable_requests WHERE requestId = :id")
    suspend fun getRequestById(id: String): DisableRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRequest(request: DisableRequestEntity)

    @Query("UPDATE disable_requests SET status = :newStatus WHERE requestId = :id")
    suspend fun updateStatus(id: String, newStatus: String)

    @Query("DELETE FROM disable_requests WHERE status = 'CANCELLED'")
    suspend fun deleteCancelled()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestampMillis DESC LIMIT 100")
    fun observeRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insertLog(log: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()

    @Query("SELECT COUNT(*) FROM audit_logs WHERE eventCode = :code")
    suspend fun countByEventCode(code: String): Long
}

@Dao
interface RewardLedgerDao {
    @Query("SELECT * FROM reward_ledger ORDER BY earnedAtMillis DESC")
    fun observeAllCredits(): Flow<List<RewardLedgerEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCredit(credit: RewardLedgerEntity)

    @Query("SELECT COUNT(*) FROM reward_ledger WHERE state = 'AVAILABLE'")
    suspend fun countAvailableCredits(): Int

    @Query("UPDATE reward_ledger SET state = :newState, consumedAtMillis = :timestamp WHERE creditId = :creditId")
    suspend fun updateCreditState(creditId: String, newState: String, timestamp: Long)
}

@Dao
interface FalsePositiveDao {
    @Query("SELECT * FROM false_positive_reports ORDER BY timestampMillis DESC")
    fun observeReports(): Flow<List<FalsePositiveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: FalsePositiveEntity)

    @Query("SELECT COUNT(*) FROM false_positive_reports")
    suspend fun getReportCount(): Long
}

@Dao
interface UserStatsDao {
    @Query("SELECT * FROM user_stats WHERE id = 1")
    fun observeUserStats(): Flow<UserStatsEntity?>

    @Query("SELECT * FROM user_stats WHERE id = 1")
    suspend fun getUserStats(): UserStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserStats(stats: UserStatsEntity)

    @Query("DELETE FROM user_stats")
    suspend fun resetStats()
}

@Dao
interface UserProtectionPlanDao {
    @Query("SELECT * FROM user_protection_plan WHERE id = 1")
    fun observePlan(): Flow<UserProtectionPlanEntity?>

    @Query("SELECT * FROM user_protection_plan WHERE id = 1")
    suspend fun getPlan(): UserProtectionPlanEntity?

    @Query("SELECT * FROM user_protection_plan WHERE accountUid = :uid LIMIT 1")
    suspend fun getPlanForAccount(uid: String): UserProtectionPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlan(plan: UserProtectionPlanEntity)

    @Query("DELETE FROM user_protection_plan")
    suspend fun resetPlan()
}

