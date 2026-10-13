package com.example.recoveryshield.data.repository

import com.example.recoveryshield.data.local.AuditLogEntity
import com.example.recoveryshield.data.local.DisableRequestEntity
import com.example.recoveryshield.data.local.FalsePositiveEntity
import com.example.recoveryshield.data.local.ProtectionStateEntity
import com.example.recoveryshield.data.local.RecoveryDatabase
import com.example.recoveryshield.data.local.UserStatsEntity
import com.example.recoveryshield.data.local.UserProtectionPlanEntity
import com.example.recoveryshield.domain.model.DisableRequest
import com.example.recoveryshield.domain.model.DisableRequestStatus
import com.example.recoveryshield.domain.model.EventLog
import com.example.recoveryshield.domain.model.FalsePositiveReport
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionPlanType
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.domain.model.SafeEventCode
import com.example.recoveryshield.domain.model.UserStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

interface ProtectionRepository {
    fun observeProtectionState(): Flow<ProtectionState>
    suspend fun getProtectionState(): ProtectionState
    suspend fun setProtectionState(state: ProtectionState)

    fun observeActiveDisableRequest(): Flow<DisableRequest?>
    suspend fun getActiveDisableRequest(): DisableRequest?
    suspend fun saveDisableRequest(request: DisableRequest)
    suspend fun updateDisableRequestStatus(requestId: String, status: DisableRequestStatus)

    suspend fun recordAuditEvent(code: SafeEventCode, metadata: String = "")
    fun observeAuditLogs(): Flow<List<EventLog>>

    suspend fun recordFalsePositive(report: FalsePositiveReport)
    fun observeFalsePositiveCount(): Flow<List<FalsePositiveReport>>
    suspend fun getFalsePositiveCount(): Long

    fun observeUserStats(): Flow<UserStats>
    suspend fun getUserStats(): UserStats
    suspend fun saveUserStats(stats: UserStats)
    suspend fun incrementInterventionCount()
    suspend fun resetUserStats()

    fun observeProtectionPlan(): Flow<ProtectionPlanInfo>
    suspend fun getProtectionPlan(): ProtectionPlanInfo
    suspend fun saveProtectionPlan(plan: ProtectionPlanInfo)
    suspend fun initializeTrialIfNew(): ProtectionPlanInfo
    suspend fun restoreOrResolveSubscriptionForAccount(uid: String, email: String? = null): ProtectionPlanInfo
    suspend fun onUserLoggedOut()
    suspend fun addProtectionDays(days: Int, source: String): ProtectionPlanInfo
    suspend fun activatePremium(durationDays: Int = 30): ProtectionPlanInfo

    suspend fun resetAllLocalState()
}

class LocalProtectionRepository(
    private val database: RecoveryDatabase,
    private val context: android.content.Context? = null,
    private val cloudFetcher: (suspend (uid: String, email: String?) -> CloudSubscriptionResult)? = null
) : ProtectionRepository {

    override fun observeProtectionState(): Flow<ProtectionState> {
        return database.protectionDao().observeState().map { entity ->
            entity?.let {
                runCatching { ProtectionState.valueOf(it.stateName) }.getOrDefault(ProtectionState.FIRST_RUN)
            } ?: ProtectionState.FIRST_RUN
        }
    }

    override suspend fun getProtectionState(): ProtectionState {
        val entity = database.protectionDao().getState()
        return entity?.let {
            runCatching { ProtectionState.valueOf(it.stateName) }.getOrDefault(ProtectionState.FIRST_RUN)
        } ?: ProtectionState.FIRST_RUN
    }

    override suspend fun setProtectionState(state: ProtectionState) {
        database.protectionDao().saveState(
            ProtectionStateEntity(
                id = 1,
                stateName = state.name,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    override fun observeActiveDisableRequest(): Flow<DisableRequest?> {
        return database.disableRequestDao().observeActiveRequest().map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getActiveDisableRequest(): DisableRequest? {
        return database.disableRequestDao().getActiveRequest()?.toDomain()
    }

    override suspend fun saveDisableRequest(request: DisableRequest) {
        database.disableRequestDao().insertOrUpdateRequest(
            DisableRequestEntity(
                requestId = request.requestId,
                createdAtMillis = request.createdAtMillis,
                cooldownDurationMillis = request.cooldownDurationMillis,
                expiresAtMillis = request.expiresAtMillis,
                status = request.status.name
            )
        )
    }

    override suspend fun updateDisableRequestStatus(requestId: String, status: DisableRequestStatus) {
        database.disableRequestDao().updateStatus(requestId, status.name)
    }

    override suspend fun recordAuditEvent(code: SafeEventCode, metadata: String) {
        database.auditLogDao().insertLog(
            AuditLogEntity(
                eventCode = code.name,
                timestampMillis = System.currentTimeMillis(),
                safeMetadata = metadata
            )
        )
    }

    override fun observeAuditLogs(): Flow<List<EventLog>> {
        return database.auditLogDao().observeRecentLogs().map { list ->
            list.map { entity ->
                EventLog(
                    id = entity.id,
                    eventCode = runCatching { SafeEventCode.valueOf(entity.eventCode) }
                        .getOrDefault(SafeEventCode.SETUP_STARTED),
                    timestampMillis = entity.timestampMillis,
                    safeMetadata = entity.safeMetadata
                )
            }
        }
    }

    override suspend fun recordFalsePositive(report: FalsePositiveReport) {
        database.falsePositiveDao().insertReport(
            FalsePositiveEntity(
                reportId = report.reportId,
                timestampMillis = report.timestampMillis,
                category = report.category,
                suppressionToken = report.safeSuppressionToken
            )
        )
        recordAuditEvent(SafeEventCode.FALSE_POSITIVE_REPORTED, "Category: ${report.category}")
    }

    override fun observeFalsePositiveCount(): Flow<List<FalsePositiveReport>> {
        return database.falsePositiveDao().observeReports().map { list ->
            list.map {
                FalsePositiveReport(
                    reportId = it.reportId,
                    timestampMillis = it.timestampMillis,
                    category = it.category,
                    safeSuppressionToken = it.suppressionToken
                )
            }
        }
    }

    override suspend fun getFalsePositiveCount(): Long {
        return database.falsePositiveDao().getReportCount()
    }

    override fun observeUserStats(): Flow<UserStats> {
        return database.userStatsDao().observeUserStats().map { entity ->
            entity?.toDomain() ?: UserStats()
        }
    }

    override suspend fun getUserStats(): UserStats {
        return database.userStatsDao().getUserStats()?.toDomain() ?: UserStats()
    }

    override suspend fun saveUserStats(stats: UserStats) {
        database.userStatsDao().saveUserStats(
            UserStatsEntity(
                id = 1,
                protectedDays = stats.protectedDays,
                currentStreakDays = stats.currentStreakDays,
                longestStreakDays = stats.longestStreakDays,
                interventionsCount = stats.interventionsCount,
                protectionTimeHours = stats.protectionTimeHours,
                memberSinceMillis = stats.memberSinceMillis
            )
        )
    }

    override suspend fun incrementInterventionCount() {
        val current = getUserStats()
        val updated = current.copy(
            interventionsCount = current.interventionsCount + 1
        )
        saveUserStats(updated)
    }

    override suspend fun resetUserStats() {
        database.userStatsDao().resetStats()
    }

    private val subscriptionMutex = Mutex()

    private fun getCachedAccountUid(): String? {
        return try {
            context?.getSharedPreferences("plan_prefs", android.content.Context.MODE_PRIVATE)
                ?.getString("cached_account_uid", null)
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun savePlanLocallyForAccount(uid: String, plan: ProtectionPlanInfo) {
        database.userProtectionPlanDao().savePlan(
            UserProtectionPlanEntity(
                id = 1,
                accountUid = uid,
                planType = plan.planType.name,
                trialStartedAtMillis = plan.trialStartedAtMillis,
                trialExpiresAtMillis = plan.trialExpiresAtMillis,
                protectionExpiresAtMillis = plan.protectionExpiresAtMillis,
                isPremiumActive = plan.isPremiumActive,
                premiumExpiresAtMillis = plan.premiumExpiresAtMillis,
                lastRewardedAdAtMillis = plan.lastRewardedAdAtMillis
            )
        )
        try {
            context?.getSharedPreferences("plan_prefs", android.content.Context.MODE_PRIVATE)?.edit()?.apply {
                putString("cached_account_uid", uid)
                putLong("protection_expires", plan.protectionExpiresAtMillis)
                putLong("trial_expires", plan.trialExpiresAtMillis)
                putLong("trial_started", plan.trialStartedAtMillis)
                putBoolean("is_premium", plan.isPremiumActive)
                putLong("premium_expires", plan.premiumExpiresAtMillis ?: 0L)
                putString("plan_type", plan.planType.name)
                putLong("last_rewarded_ad", plan.lastRewardedAdAtMillis ?: 0L)
                commit()
            }
        } catch (_: Throwable) {}
    }

    override fun observeProtectionPlan(): Flow<ProtectionPlanInfo> {
        return database.userProtectionPlanDao().observePlan().map { entity ->
            val currentUid = CloudSubscriptionSyncManager.getAuthenticatedUid()
            if (entity != null) {
                if (currentUid.isNullOrBlank()) {
                    if (entity.accountUid.isBlank()) entity.toDomain() else ProtectionPlanInfo()
                } else {
                    if (entity.accountUid == currentUid) entity.toDomain() else ProtectionPlanInfo()
                }
            } else {
                ProtectionPlanInfo()
            }
        }
    }

    override suspend fun restoreOrResolveSubscriptionForAccount(uid: String, email: String?): ProtectionPlanInfo = subscriptionMutex.withLock {
        if (uid.isBlank()) return@withLock ProtectionPlanInfo()
        Log.i("ProtectionRepository", "Resolving subscription for account UID: $uid")

        val cloudResult = cloudFetcher?.invoke(uid, email) ?: CloudSubscriptionSyncManager.fetchCloudSubscription(uid, email, context)
        when (cloudResult) {
            is CloudSubscriptionResult.Exists -> {
                val cloudPlan = cloudResult.plan
                Log.i("ProtectionRepository", "Existing subscription restored for UID $uid: plan=${cloudPlan.planType}, isPremium=${cloudPlan.isPremiumActive}, trialExpires=${cloudPlan.trialExpiresAtMillis}")
                savePlanLocallyForAccount(uid, cloudPlan)
                cloudPlan
            }
            is CloudSubscriptionResult.NotFound -> {
                Log.i("ProtectionRepository", "No subscription document for $uid in Firestore. Creating first-ever initial trial atomically.")
                val initialPlan = CloudSubscriptionSyncManager.createInitialTrialAtomically(uid, email ?: "", context)
                savePlanLocallyForAccount(uid, initialPlan)
                recordAuditEvent(SafeEventCode.SETUP_STARTED, "Initial trial initialized for new account $uid")
                initialPlan
            }
            is CloudSubscriptionResult.Error -> {
                Log.w("ProtectionRepository", "Firestore read error for $uid (${cloudResult.cause.message}). DO NOT CREATE TRIAL, DO NOT OVERWRITE STATE.")
                // Check if local cache matches this EXACT account UID
                val cachedUid = getCachedAccountUid()
                if (cachedUid == uid) {
                    val localPlan = database.userProtectionPlanDao().getPlanForAccount(uid)?.toDomain()
                        ?: database.userProtectionPlanDao().getPlan()?.toDomain()
                    if (localPlan != null) {
                        Log.i("ProtectionRepository", "Reusing valid local cache for $uid during read failure.")
                        return@withLock localPlan
                    }
                }
                // Keep entitlement unresolved safe plan without writing to Firestore
                ProtectionPlanInfo(
                    planType = ProtectionPlanType.FREE_TRIAL,
                    trialStartedAtMillis = 0L,
                    trialExpiresAtMillis = 0L,
                    protectionExpiresAtMillis = 0L,
                    isPremiumActive = false
                )
            }
            is CloudSubscriptionResult.NotAuthenticated -> {
                ProtectionPlanInfo()
            }
        }
    }

    override suspend fun onUserLoggedOut() {
        subscriptionMutex.withLock {
            Log.i("ProtectionRepository", "Detaching account subscription cache on logout")
            database.userProtectionPlanDao().resetPlan()
            try {
                context?.getSharedPreferences("plan_prefs", android.content.Context.MODE_PRIVATE)?.edit()?.clear()?.commit()
            } catch (_: Throwable) {}
        }
    }

    override suspend fun getProtectionPlan(): ProtectionPlanInfo {
        val currentUid = CloudSubscriptionSyncManager.getAuthenticatedUid()
        val cachedUid = getCachedAccountUid()
        val local = database.userProtectionPlanDao().getPlan()?.toDomain()

        if (!currentUid.isNullOrBlank() && cachedUid == currentUid && local != null) {
            return local
        }
        if (!currentUid.isNullOrBlank()) {
            val email = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "" } catch (_: Throwable) { "" }
            return restoreOrResolveSubscriptionForAccount(currentUid, email)
        }
        return local ?: ProtectionPlanInfo()
    }

    override suspend fun saveProtectionPlan(plan: ProtectionPlanInfo) = subscriptionMutex.withLock {
        val currentUid = CloudSubscriptionSyncManager.getAuthenticatedUid()
        if (!currentUid.isNullOrBlank()) {
            savePlanLocallyForAccount(currentUid, plan)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                CloudSubscriptionSyncManager.syncPlanToCloud(currentUid, plan, null, context)
            }
        } else {
            database.userProtectionPlanDao().savePlan(
                UserProtectionPlanEntity(
                    id = 1,
                    accountUid = "",
                    planType = plan.planType.name,
                    trialStartedAtMillis = plan.trialStartedAtMillis,
                    trialExpiresAtMillis = plan.trialExpiresAtMillis,
                    protectionExpiresAtMillis = plan.protectionExpiresAtMillis,
                    isPremiumActive = plan.isPremiumActive,
                    premiumExpiresAtMillis = plan.premiumExpiresAtMillis,
                    lastRewardedAdAtMillis = plan.lastRewardedAdAtMillis
                )
            )
        }
    }

    override suspend fun initializeTrialIfNew(): ProtectionPlanInfo {
        val uid = CloudSubscriptionSyncManager.getAuthenticatedUid()
        if (uid.isNullOrBlank()) {
            Log.d("ProtectionRepository", "Cannot initialize trial: user is not authenticated yet.")
            return ProtectionPlanInfo()
        }
        val email = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "" } catch (_: Throwable) { "" }
        return restoreOrResolveSubscriptionForAccount(uid, email)
    }

    override suspend fun addProtectionDays(days: Int, source: String): ProtectionPlanInfo {
        val current = getProtectionPlan()
        val now = System.currentTimeMillis()
        val durationMillis = days.toLong() * 24L * 60L * 60L * 1000L
        
        val baseTime = if (current.protectionExpiresAtMillis > now) current.protectionExpiresAtMillis else now
        val newExpiry = baseTime + durationMillis
        val updated = current.copy(
            planType = if (current.isPremiumActive) ProtectionPlanType.PREMIUM else ProtectionPlanType.REWARD_EXTENDED,
            protectionExpiresAtMillis = newExpiry,
            lastRewardedAdAtMillis = if (source == "REWARDED_AD") now else current.lastRewardedAdAtMillis
        )
        saveProtectionPlan(updated)
        recordAuditEvent(SafeEventCode.REWARD_EARNED, "Added $days day(s) via $source. New expiry: $newExpiry")
        return updated
    }

    override suspend fun activatePremium(durationDays: Int): ProtectionPlanInfo {
        val current = getProtectionPlan()
        val now = System.currentTimeMillis()
        val premiumDuration = durationDays.toLong() * 24L * 60L * 60L * 1000L
        val premiumExpiry = now + premiumDuration
        val updated = current.copy(
            planType = ProtectionPlanType.PREMIUM,
            isPremiumActive = true,
            premiumExpiresAtMillis = premiumExpiry,
            protectionExpiresAtMillis = maxOf(current.protectionExpiresAtMillis, premiumExpiry)
        )
        saveProtectionPlan(updated)
        recordAuditEvent(SafeEventCode.SERVICE_STARTED, "Premium activated for $durationDays days")
        return updated
    }

    override suspend fun resetAllLocalState() {
        database.protectionDao().saveState(
            ProtectionStateEntity(id = 1, stateName = ProtectionState.FIRST_RUN.name)
        )
        database.disableRequestDao().deleteCancelled()
        database.auditLogDao().clearLogs()
        database.userStatsDao().resetStats()
        database.userProtectionPlanDao().resetPlan()
    }

    private fun UserProtectionPlanEntity.toDomain(): ProtectionPlanInfo {
        val parsedType = runCatching { ProtectionPlanType.valueOf(planType) }.getOrDefault(ProtectionPlanType.FREE_TRIAL)
        val isPremium = isPremiumActive || parsedType == ProtectionPlanType.PREMIUM || ((premiumExpiresAtMillis ?: 0L) > System.currentTimeMillis())
        val finalType = if (isPremium) ProtectionPlanType.PREMIUM else parsedType
        val finalExpires = if (isPremium && (premiumExpiresAtMillis ?: 0L) > 0L) maxOf(protectionExpiresAtMillis, premiumExpiresAtMillis!!) else protectionExpiresAtMillis
        return ProtectionPlanInfo(
            planType = finalType,
            trialStartedAtMillis = trialStartedAtMillis,
            trialExpiresAtMillis = trialExpiresAtMillis,
            protectionExpiresAtMillis = finalExpires,
            isPremiumActive = isPremium,
            premiumExpiresAtMillis = premiumExpiresAtMillis,
            lastRewardedAdAtMillis = lastRewardedAdAtMillis
        )
    }

    private fun UserStatsEntity.toDomain(): UserStats {
        return UserStats(
            protectedDays = protectedDays,
            currentStreakDays = currentStreakDays,
            longestStreakDays = longestStreakDays,
            interventionsCount = interventionsCount,
            protectionTimeHours = protectionTimeHours,
            memberSinceMillis = memberSinceMillis
        )
    }

    private fun DisableRequestEntity.toDomain(): DisableRequest {
        return DisableRequest(
            requestId = requestId,
            createdAtMillis = createdAtMillis,
            cooldownDurationMillis = cooldownDurationMillis,
            expiresAtMillis = expiresAtMillis,
            status = runCatching { DisableRequestStatus.valueOf(status) }
                .getOrDefault(DisableRequestStatus.CANCELLED)
        )
    }
}
