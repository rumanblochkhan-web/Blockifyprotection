package com.example.recoveryshield.protection.engine

import android.content.Context
import android.util.Log
import com.example.recoveryshield.data.local.RecoveryDatabase
import com.example.recoveryshield.data.repository.LocalProtectionRepository
import com.example.recoveryshield.data.repository.ProtectionRepository
import com.example.recoveryshield.data.settings.SettingsRepository
import com.example.recoveryshield.data.settings.SharedPreferencesSettingsRepository
import com.example.recoveryshield.domain.model.DetectionDecision
import com.example.recoveryshield.domain.model.DetectionResult
import com.example.recoveryshield.domain.model.DiagnosticsSummary
import com.example.recoveryshield.domain.model.NormalizedUiEvent
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.domain.model.SafeEventCode
import com.example.recoveryshield.domain.state.ProtectionStateMachine
import com.example.recoveryshield.notifications.ProtectionNotificationHelper
import com.example.recoveryshield.permissions.PermissionOrchestrator
import com.example.recoveryshield.protection.detector.DeterministicRuleDetector
import com.example.recoveryshield.protection.detector.LocalClassifier
import com.example.recoveryshield.protection.disable.DisableProtectionManager
import com.example.recoveryshield.protection.intervention.InterventionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class ProtectionEngine private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = RecoveryDatabase.getInstance(context)
    val repository: ProtectionRepository = LocalProtectionRepository(database, context)
    val settingsRepository: SettingsRepository = SharedPreferencesSettingsRepository(context)
    val permissionOrchestrator = PermissionOrchestrator(context)

    val stateMachine = ProtectionStateMachine(ProtectionState.FIRST_RUN)

    private val _protectionStateFlow = MutableStateFlow(ProtectionState.FIRST_RUN)
    val protectionStateFlow: StateFlow<ProtectionState> = _protectionStateFlow.asStateFlow()

    private val statePrefs = context.getSharedPreferences("safemind_prefs", Context.MODE_PRIVATE)
    @Volatile private var stateRestored = false

    val detector: LocalClassifier = DeterministicRuleDetector()

    // Diagnostic counters
    private val eventsReceivedCount = AtomicLong(0)
    private val eventsProcessedCount = AtomicLong(0)
    private val eventsSkippedCount = AtomicLong(0)
    private val detectorInvocationsCount = AtomicLong(0)
    private val interventionsLaunchedCount = AtomicLong(0)

    private val _diagnosticsSummary = MutableStateFlow(DiagnosticsSummary())
    val diagnosticsSummary: StateFlow<DiagnosticsSummary> = _diagnosticsSummary.asStateFlow()

    private val _lastDetectionResult = MutableStateFlow<DetectionResult?>(null)
    val lastDetectionResult: StateFlow<DetectionResult?> = _lastDetectionResult.asStateFlow()

    val interventionController: InterventionController = InterventionController(
        context = context,
        onRecordAuditEvent = { code, meta -> repository.recordAuditEvent(code, meta) },
        onRecordFalsePositive = { report -> repository.recordFalsePositive(report) }
    )

    val disableManager: DisableProtectionManager = DisableProtectionManager(
        repository = repository,
        onStateChanged = { newState -> updateState(newState) }
    )

    val notificationHelper: ProtectionNotificationHelper = ProtectionNotificationHelper(context)

    init {
        try {
            val mirrorStr = statePrefs.getString("protection_state_mirror", null)
            if (!mirrorStr.isNullOrEmpty()) {
                val mirrorState = try { ProtectionState.valueOf(mirrorStr) } catch (_: Throwable) { null }
                if (mirrorState != null) {
                    stateMachine.forceRestore(mirrorState)
                    _protectionStateFlow.value = mirrorState
                    stateRestored = true
                }
            }
        } catch (_: Throwable) {}

        scope.launch {
            try {
                val wasRestoredFromMirror = stateRestored
                // Restore persistent state on startup
                val persisted = repository.getProtectionState()
                if (!wasRestoredFromMirror) {
                    stateMachine.forceRestore(persisted)
                }

                // Reconcile with current permissions
                val permissions = permissionOrchestrator.checkAllPermissions()
                val reconciled = stateMachine.reconcileWithPermissions(permissions)

                updateState(reconciled)
                repository.recordAuditEvent(SafeEventCode.SERVICE_STARTED, "Engine initialized, State: $reconciled")
            } catch (t: Throwable) {
                Log.w("ProtectionEngine", "Init coroutine error: ${t.message}")
            } finally {
                stateRestored = true
            }
        }

        // Synchronize state machine with intervention lifecycle
        scope.launch {
            try {
                interventionController.interventionState.collect { state ->
                    if (state.isActive) {
                        if (_protectionStateFlow.value == ProtectionState.ACTIVE) {
                            updateState(ProtectionState.INTERVENTION)
                        }
                    } else {
                        if (_protectionStateFlow.value == ProtectionState.INTERVENTION) {
                            updateState(ProtectionState.ACTIVE)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun updateState(newState: ProtectionState) {
        stateMachine.transitionTo(newState)
        val finalState = stateMachine.currentState
        _protectionStateFlow.value = finalState
        try {
            statePrefs.edit().putString("protection_state_mirror", finalState.name).apply()
        } catch (_: Throwable) {}
        notificationHelper.syncNotificationWithState(finalState)
        scope.launch {
            repository.setProtectionState(finalState)
            refreshDiagnostics()
        }
    }

    fun refreshStateWithPermissions() {
        if (!stateRestored) return
        val currentState = _protectionStateFlow.value
        
        // Reconcile anti-uninstall with actual Device Admin status (PART 8)
        val actualAdminActive = permissionOrchestrator.deviceManagementManager.isDeviceAdminActive()
        if (!actualAdminActive && settingsRepository.getSettings().antiUninstallProtectionEnabled) {
            setAntiUninstallProtectionEnabled(false)
        }

        if (currentState == ProtectionState.DISABLED || currentState == ProtectionState.READY_BUT_INACTIVE) {
            val permissions = permissionOrchestrator.checkAllPermissions()
            if (!permissions.canActivate) {
                updateState(ProtectionState.PERMISSION_REQUIRED)
            }
            return
        }
        val permissions = permissionOrchestrator.checkAllPermissions()
        val reconciled = stateMachine.reconcileWithPermissions(permissions)
        updateState(reconciled)
    }

    fun activateVoluntaryProtection(): Boolean {
        val permissions = permissionOrchestrator.checkAllPermissions()
        if (!permissions.canActivate) {
            updateState(ProtectionState.PERMISSION_REQUIRED)
            return false
        }
        val plan = com.example.recoveryshield.domain.model.ProtectionPlanInfo.getCachedPlanSync(context)
        if (plan.isExpired) {
            Log.w("ProtectionEngine", "Cannot activate protection: plan/trial has expired.")
            updateState(ProtectionState.READY_BUT_INACTIVE)
            return false
        }
        updateState(ProtectionState.ACTIVE)
        scope.launch {
            repository.setProtectionState(ProtectionState.ACTIVE)
            repository.recordAuditEvent(SafeEventCode.PERMISSION_GRANTED, "Protection voluntarily enabled by user")
        }
        return true
    }

    fun isAntiUninstallProtectionEnabled(): Boolean {
        val actualAdminActive = permissionOrchestrator.deviceManagementManager.isDeviceAdminActive()
        return actualAdminActive && settingsRepository.getSettings().antiUninstallProtectionEnabled
    }

    /**
     * Authoritative check mapping every active monitoring state in the ProtectionState lifecycle.
     * Strictly enforces plan encryption: if trial/subscription is expired, shuts off immediately.
     */
    fun isProtectionRuntimeEnabled(): Boolean {
        val plan = com.example.recoveryshield.domain.model.ProtectionPlanInfo.getCachedPlanSync(context)
        if (plan.isExpired) {
            return false
        }
        if (_protectionStateFlow.value == ProtectionState.DISABLED) {
            return false
        }
        if (com.example.recoveryshield.protection.service.RecoveryAccessibilityService.isServiceRunning()) {
            return true
        }
        val prefs = context.getSharedPreferences("safemind_prefs", Context.MODE_PRIVATE)
        val hasCompletedSetup = prefs.getBoolean("has_completed_setup", false)
        if (!hasCompletedSetup) {
            return false
        }
        return when (_protectionStateFlow.value) {
            ProtectionState.ACTIVE,
            ProtectionState.INTERVENTION,
            ProtectionState.COOLDOWN_REQUESTED,
            ProtectionState.COOLDOWN_ACTIVE,
            ProtectionState.DISABLE_CONFIRMATION -> true
            else -> false
        }
    }

    fun setAntiUninstallProtectionEnabled(enabled: Boolean) {
        settingsRepository.updateAntiUninstallProtection(enabled)
    }

    fun deactivateVoluntaryProtection(): Boolean {
        updateState(ProtectionState.DISABLED)
        setAntiUninstallProtectionEnabled(false)
        scope.launch {
            repository.setProtectionState(ProtectionState.DISABLED)
            repository.recordAuditEvent(SafeEventCode.PROTECTION_DISABLED, "Protection voluntarily stopped via emergency stop")
        }
        return true
    }

    fun processNormalizedEvent(event: NormalizedUiEvent) {
        eventsReceivedCount.incrementAndGet()

        // Process events when in an active monitoring state or whenever AccessibilityService is running
        val state = _protectionStateFlow.value
        val isAccessibilityServiceRunning = com.example.recoveryshield.protection.service.RecoveryAccessibilityService.isServiceRunning()
        val isExplicitlyDisabled = state == ProtectionState.DISABLED

        if (!isProtectionRuntimeEnabled() || isExplicitlyDisabled) {
            eventsSkippedCount.incrementAndGet()
            refreshDiagnostics()
            return
        }

        eventsProcessedCount.incrementAndGet()
        detectorInvocationsCount.incrementAndGet()

        val result = detector.classify(event)
        _lastDetectionResult.value = result

        if (result.decision == DetectionDecision.HIGH_CONFIDENCE_TRIGGER) {
            android.util.Log.i("BLOCKIFY_TRIGGER", "ProtectionEngine: HIGH_CONFIDENCE_TRIGGER detected in pkg=${event.packageName}, summary='${result.diagnosticSafeSummary}'")
            interventionsLaunchedCount.incrementAndGet()
            val isAntiTamper = result.diagnosticSafeSummary.contains("Anti-tamper", ignoreCase = true)
            // Show overlay IMMEDIATELY
            interventionController.triggerIntervention(result.diagnosticSafeSummary, isAntiTamper = isAntiTamper)
            scope.launch(Dispatchers.IO) {
                repository.incrementInterventionCount()
            }
            refreshDiagnostics(force = true)
        } else {
            refreshDiagnostics()
        }
    }

    /**
     * Ultra-low latency trigger (0.0001s) when an explicit keyword or anti-tamper is detected.
     */
    fun triggerInstantIntervention(reason: String, isAntiTamper: Boolean = false) {
        if (!isProtectionRuntimeEnabled()) {
            android.util.Log.d("BLOCKIFY_TRIGGER", "ProtectionEngine: Skipping triggerInstantIntervention because protection is not active (reason='$reason')")
            return
        }
        android.util.Log.i("BLOCKIFY_TRIGGER", "ProtectionEngine: triggerInstantIntervention called with reason='$reason', isAntiTamper=$isAntiTamper")
        interventionsLaunchedCount.incrementAndGet()
        // Show overlay IMMEDIATELY
        interventionController.triggerIntervention(reason, isAntiTamper = isAntiTamper)
        scope.launch(Dispatchers.IO) {
            repository.incrementInterventionCount()
            repository.recordAuditEvent(SafeEventCode.HIGH_CONFIDENCE_INTERVENTION, reason)
        }
        refreshDiagnostics(force = true)
    }

    fun isTamperOrDeactivationAttempt(event: NormalizedUiEvent): Boolean {
        return (detector as? DeterministicRuleDetector)?.isTamperOrDeactivationAttempt(event) == true
    }

    fun recordSkippedEvent() {
        eventsSkippedCount.incrementAndGet()
        refreshDiagnostics()
    }

    fun onServiceConnected() {
        scope.launch {
            repository.recordAuditEvent(SafeEventCode.SERVICE_STARTED, "Accessibility service active")
            refreshStateWithPermissions()
        }
    }

    fun onServiceDisconnected() {
        scope.launch {
            repository.recordAuditEvent(SafeEventCode.SERVICE_STOPPED, "Accessibility service disconnected")
            refreshStateWithPermissions()
        }
    }

    private var lastDiagnosticsTime = 0L

    private fun refreshDiagnostics(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (force || (now - lastDiagnosticsTime) >= 250L) {
            lastDiagnosticsTime = now
            _diagnosticsSummary.value = DiagnosticsSummary(
                eventsReceivedCount = eventsReceivedCount.get(),
                eventsProcessedCount = eventsProcessedCount.get(),
                eventsSkippedCount = eventsSkippedCount.get(),
                detectorInvocationsCount = detectorInvocationsCount.get(),
                interventionsLaunchedCount = interventionsLaunchedCount.get(),
                lastDecisionSummary = _lastDetectionResult.value?.diagnosticSafeSummary ?: "None"
            )
        }
    }

    fun resetLocalDiagnostics() {
        eventsReceivedCount.set(0)
        eventsProcessedCount.set(0)
        eventsSkippedCount.set(0)
        detectorInvocationsCount.set(0)
        interventionsLaunchedCount.set(0)
        _lastDetectionResult.value = null
        detector.resetTemporalState()
        refreshDiagnostics(force = true)
    }

    companion object {
        @Volatile
        private var INSTANCE: ProtectionEngine? = null

        fun getInstance(context: Context): ProtectionEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProtectionEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
