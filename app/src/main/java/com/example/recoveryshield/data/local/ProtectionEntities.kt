package com.example.recoveryshield.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "protection_state")
data class ProtectionStateEntity(
    @PrimaryKey val id: Int = 1,
    val stateName: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "disable_requests")
data class DisableRequestEntity(
    @PrimaryKey val requestId: String,
    val createdAtMillis: Long,
    val cooldownDurationMillis: Long,
    val expiresAtMillis: Long,
    val status: String
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventCode: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val safeMetadata: String = ""
)

@Entity(tableName = "reward_ledger")
data class RewardLedgerEntity(
    @PrimaryKey val creditId: String,
    val earnedAtMillis: Long,
    val source: String,
    val valueDays: Int,
    val state: String,
    val consumedAtMillis: Long? = null,
    val expiryPolicy: String = "NO_EXPIRY"
)

@Entity(tableName = "false_positive_reports")
data class FalsePositiveEntity(
    @PrimaryKey val reportId: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val category: String,
    val suppressionToken: String? = null
)

@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey val id: Int = 1,
    val protectedDays: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val interventionsCount: Int = 0,
    val protectionTimeHours: Float = 0f,
    val memberSinceMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_protection_plan")
data class UserProtectionPlanEntity(
    @PrimaryKey val id: Int = 1,
    val accountUid: String = "",
    val planType: String = "FREE_TRIAL",
    val trialStartedAtMillis: Long = 0L,
    val trialExpiresAtMillis: Long = 0L,
    val protectionExpiresAtMillis: Long = 0L,
    val isPremiumActive: Boolean = false,
    val premiumExpiresAtMillis: Long? = null,
    val lastRewardedAdAtMillis: Long? = null
)

