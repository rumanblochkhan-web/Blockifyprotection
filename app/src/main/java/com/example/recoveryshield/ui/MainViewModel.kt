package com.example.recoveryshield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.recoveryshield.ads.MockAdProvider
import com.example.recoveryshield.domain.model.CooldownState
import com.example.recoveryshield.domain.model.DeviceCompatibility
import com.example.recoveryshield.domain.model.EventLog
import com.example.recoveryshield.domain.model.NormalizedUiEvent
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.PermissionType
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionPlanType
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.domain.model.SafeEventCode
import com.example.recoveryshield.domain.model.UserStats
import com.example.recoveryshield.points.ProtectionCreditManager
import com.example.recoveryshield.points.RoomRewardLedger
import com.example.recoveryshield.protection.detector.HarnessEvaluationResult
import com.example.recoveryshield.protection.detector.SyntheticDetectorHarness
import com.example.recoveryshield.protection.engine.ProtectionEngine
import com.example.recoveryshield.support.DefaultSupportRepository
import com.example.recoveryshield.support.SupportRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val engine = ProtectionEngine.getInstance(application)
    val supportRepository: SupportRepository = DefaultSupportRepository()
    private val rewardLedger = RoomRewardLedger(engine.database)
    val adMobManager = com.example.recoveryshield.ads.AdMobRewardedAdManager(application, engine.repository)
    val creditManager = ProtectionCreditManager(adMobManager, rewardLedger, engine.repository)

    val protectionState: StateFlow<ProtectionState> = engine.protectionStateFlow
    val permissionState: StateFlow<PermissionState> = engine.permissionOrchestrator.permissionState
    val cooldownState: StateFlow<CooldownState> = engine.disableManager.cooldownState
    val diagnosticsSummary = engine.diagnosticsSummary
    val lastDetectionResult = engine.lastDetectionResult

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user != null && user.uid.isNotBlank()) {
            viewModelScope.launch {
                engine.repository.restoreOrResolveSubscriptionForAccount(user.uid, user.email)
            }
        } else {
            viewModelScope.launch {
                engine.repository.onUserLoggedOut()
            }
        }
    }

    val auditLogs: StateFlow<List<EventLog>> = engine.repository.observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userStats: StateFlow<UserStats> = engine.repository.observeUserStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserStats())

    val protectionPlan: StateFlow<ProtectionPlanInfo> = engine.repository.observeProtectionPlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProtectionPlanInfo())

    val userSettings: StateFlow<com.example.recoveryshield.domain.model.UserSettings> = engine.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), engine.settingsRepository.getSettings())

    private val _harnessResult = MutableStateFlow<HarnessEvaluationResult?>(null)
    val harnessResult: StateFlow<HarnessEvaluationResult?> = _harnessResult.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setAntiUninstallProtectionEnabled(enabled: Boolean) {
        engine.setAntiUninstallProtectionEnabled(enabled)
        if (enabled) {
            _statusMessage.value = "Anti-Uninstall Protection activated. Use Emergency Stop to disable."
        } else {
            _statusMessage.value = "Anti-Uninstall Protection deactivated."
        }
    }

    init {
        viewModelScope.launch {
            creditManager.refreshLedgerState()
        }
        try {
            FirebaseAuth.getInstance().addAuthStateListener(authListener)
        } catch (_: Throwable) {}
    }

    fun restoreSubscriptionOnLogin(uid: String, email: String? = null) {
        viewModelScope.launch {
            engine.repository.restoreOrResolveSubscriptionForAccount(uid, email)
        }
    }

    fun onUserLoggedOut() {
        viewModelScope.launch {
            engine.repository.onUserLoggedOut()
        }
    }

    fun onResume() {
        engine.refreshStateWithPermissions()
        try { com.example.recoveryshield.notifications.ProtectionNotificationHelper.ensureRunningIfActive(getApplication()) } catch (_: Throwable) {}
    }

    fun activateProtection() {
        val permissions = engine.permissionOrchestrator.checkAllPermissions()
        if (!permissions.canActivate) {
            _statusMessage.value = "Please enable the required Accessibility Service in system settings first."
            return
        }
        val plan = com.example.recoveryshield.domain.model.ProtectionPlanInfo.getCachedPlanSync(com.example.BlockifyApplication.instance)
        if (plan.isExpired) {
            _statusMessage.value = "Subscription required. Your 3-day trial has expired."
            return
        }
        val success = engine.activateVoluntaryProtection()
        if (!success) {
            _statusMessage.value = "Could not activate protection. Check permissions or subscription."
        } else {
            _statusMessage.value = "Protection activated successfully."
        }
    }

    fun deactivateProtection() {
        val success = engine.deactivateVoluntaryProtection()
        if (success) {
            _statusMessage.value = "Protection paused."
        }
    }

    fun requestDisable(cooldownMinutes: Int = 120) {
        viewModelScope.launch {
            val cooldownMs = cooldownMinutes * 60 * 1000L
            engine.disableManager.requestDisable(cooldownMs)
            _statusMessage.value = "Cooling-off period started ($cooldownMinutes min). Protection remains active."
        }
    }

    fun cancelDisableRequest() {
        viewModelScope.launch {
            engine.disableManager.cancelDisableRequest()
            _statusMessage.value = "Disable request cancelled. Protection remains active."
        }
    }

    fun confirmDisable() {
        viewModelScope.launch {
            val success = engine.disableManager.confirmDisableAfterCooldown()
            if (success) {
                _statusMessage.value = "Protection disabled voluntarily."
            } else {
                _statusMessage.value = "Cooldown has not elapsed yet."
            }
        }
    }

    fun runSyntheticHarness() {
        viewModelScope.launch {
            val evaluation = SyntheticDetectorHarness.evaluate(engine.detector)
            _harnessResult.value = evaluation
            _statusMessage.value = "Harness completed: ${evaluation.passed}/${evaluation.totalFixtures} passed. Precision: ${(evaluation.precision * 100).toInt()}%, Recall: ${(evaluation.recall * 100).toInt()}%"
        }
    }

    fun injectSyntheticTrigger(isExplicit: Boolean) {
        if (protectionState.value != ProtectionState.ACTIVE && protectionState.value != ProtectionState.COOLDOWN_ACTIVE) {
            activateProtection()
        }
        val event = if (isExplicit) {
            NormalizedUiEvent(
                packageName = "synthetic.test.browser",
                textFragments = listOf("hardcore", "pornography", "stream"),
                isSyntheticFixture = true
            )
        } else {
            NormalizedUiEvent(
                packageName = "synthetic.test.benign",
                textFragments = listOf("street corner", "hornet hive", "corn field"),
                isSyntheticFixture = true
            )
        }
        engine.processNormalizedEvent(event)
    }

    fun testPornhubTrigger() {
        if (protectionState.value != ProtectionState.ACTIVE && protectionState.value != ProtectionState.COOLDOWN_ACTIVE) {
            activateProtection()
        }
        val event = NormalizedUiEvent(
            packageName = "com.android.chrome",
            textFragments = listOf("pornhub.com", "watch porn video online", "explicit stream"),
            isSyntheticFixture = true
        )
        engine.processNormalizedEvent(event)
        _statusMessage.value = "🚨 Explicit adult content detected (pornhub.com)! Launching shield countdown..."
    }

    fun scanCustomContent(query: String) {
        if (query.isBlank()) return
        if (protectionState.value != ProtectionState.ACTIVE && protectionState.value != ProtectionState.COOLDOWN_ACTIVE) {
            activateProtection()
        }
        val event = NormalizedUiEvent(
            packageName = "test.browser.input",
            textFragments = listOf(query),
            isSyntheticFixture = true
        )
        engine.processNormalizedEvent(event)
        val result = engine.lastDetectionResult.value
        if (result?.decision == com.example.recoveryshield.domain.model.DetectionDecision.HIGH_CONFIDENCE_TRIGGER) {
            _statusMessage.value = "🚨 Trigger detected in \"$query\"! Intervention executed."
        } else {
            _statusMessage.value = "✅ Safe content: \"$query\" passed scan."
        }
    }

    fun watchRewardedAd(activity: android.app.Activity) {
        viewModelScope.launch {
            adMobManager.showRewardedAd(
                activity = activity,
                onRewardGranted = {
                    _statusMessage.value = "+1 Day Added"
                },
                onAdFailedOrCancelled = { reason ->
                    if (reason == "Reward not earned") {
                        _statusMessage.value = "Reward not earned"
                    } else {
                        _statusMessage.value = "Ad unavailable. Please try again."
                    }
                }
            )
        }
    }

    fun watchRewardedAd() {
        viewModelScope.launch {
            val success = creditManager.watchAdForCredit(isInterventionActive = false)
            if (success) {
                _statusMessage.value = "+1 Day Added"
            } else {
                _statusMessage.value = "Ad unavailable. Please try again."
            }
        }
    }

    fun upgradeToPremium(durationDays: Int = 30) {
        viewModelScope.launch {
            engine.repository.activatePremium(durationDays = durationDays)
            _statusMessage.value = "Subscription active ($durationDays days)! Protection unlocked."
        }
    }

    fun resetLocalTestState() {
        viewModelScope.launch {
            engine.repository.resetAllLocalState()
            engine.resetLocalDiagnostics()
            engine.updateState(ProtectionState.FIRST_RUN)
            _harnessResult.value = null
            _statusMessage.value = "Local test state reset to FIRST_RUN."
        }
    }

    fun initializeNewUserStats() {
        viewModelScope.launch {
            engine.repository.saveUserStats(
                UserStats(
                    protectedDays = 0,
                    currentStreakDays = 0,
                    longestStreakDays = 0,
                    interventionsCount = 0,
                    protectionTimeHours = 0f,
                    isProtectionActive = false
                )
            )
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun getPermissionExplanation(type: PermissionType): String {
        return engine.permissionOrchestrator.getExplanation(type)
    }

    fun getDeviceCompatibility(): DeviceCompatibility {
        return com.example.recoveryshield.diagnostics.DeviceCompatibilityRepository(
            getApplication(),
            engine.permissionOrchestrator
        ).getCompatibilityInfo()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        } catch (_: Throwable) {}
    }
}
