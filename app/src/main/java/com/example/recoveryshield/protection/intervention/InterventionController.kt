package com.example.recoveryshield.protection.intervention

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.recoveryshield.domain.model.FalsePositiveReport
import com.example.recoveryshield.domain.model.InterventionState
import com.example.recoveryshield.domain.model.SafeEventCode
import com.example.recoveryshield.protection.service.RecoveryAccessibilityService
import com.example.recoveryshield.protection.util.BlockifyPerfTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class InterventionController(
    private val context: Context,
    private val onRecordAuditEvent: suspend (SafeEventCode, String) -> Unit,
    private val onRecordFalsePositive: suspend (FalsePositiveReport) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _interventionState = MutableStateFlow(InterventionState())
    val interventionState: StateFlow<InterventionState> = _interventionState.asStateFlow()

    private var countdownJob: Job? = null
    private var lastInterventionTimestamp: Long = 0L
    private var debounceSuppressionWindowMs = 50L

    fun triggerIntervention(diagnosticSummary: String, isAntiTamper: Boolean = false): Boolean {
        BlockifyPerfTracker.onInterventionRequested()
        SystemInterventionOverlay.activeDiagnosticContext?.markInterventionRequested()

        val now = SystemClock.uptimeMillis()
        val isOverlayActive = RecoveryAccessibilityService.isOverlayShowing()
        val isServiceRunning = RecoveryAccessibilityService.isServiceRunning()

        android.util.Log.i("BLOCKIFY_CONTEXT_ENGINE", """
            |--- BLOCKIFY_CONTEXT_ENGINE triggerIntervention ---
            |timestamp: ${System.currentTimeMillis()}
            |isServiceRunning: $isServiceRunning
            |isOverlayActive: $isOverlayActive
            |controllerIsActive: ${_interventionState.value.isActive}
            |isAntiTamper: $isAntiTamper
            |summary: $diagnosticSummary
        """.trimMargin())

        // 1. Check for duplicate triggers
        if (_interventionState.value.isActive) {
            // If service is running and overlay is active, definitely skip duplicate
            if (isOverlayActive) {
                android.util.Log.d("BLOCKIFY_TRIGGER", "InterventionController: Overlay already actively displayed, skipping duplicate")
                return false
            }
            
            // If service is NOT running (e.g. Unit Tests), trust controller's isActive state for duplicate detection
            if (!isServiceRunning) {
                android.util.Log.d("BLOCKIFY_TRIGGER", "InterventionController: Service not running and controller active, skipping duplicate")
                return false
            }

            // If service IS running but overlay is NOT active, self-heal: previous intervention likely ended abruptly
            android.util.Log.w("BLOCKIFY_TRIGGER", "InterventionController: Controller active but overlay missing - self-healing state")
            _interventionState.value = InterventionState(isActive = false)
        }

        // Ultra-short debounce window to enable instant repeated testing
        val debounceWindow = if (isAntiTamper) 0L else debounceSuppressionWindowMs
        if (lastInterventionTimestamp > 0L && (now - lastInterventionTimestamp) < debounceWindow) {
            android.util.Log.d("BLOCKIFY_TRIGGER", "InterventionController: Within debounce window (${now - lastInterventionTimestamp}ms < ${debounceWindow}ms), skipping duplicate")
            return false
        }

        lastInterventionTimestamp = now

        // 1. SHOW SYSTEM FLOATING OVERLAY FIRST WITH ZERO DELAY
        val overlayShown = if (isServiceRunning) {
            RecoveryAccessibilityService.showSystemIntervention(isAntiTamper, diagnosticSummary) {
                dismissIntervention()
            }
        } else {
            false
        }

        if (!overlayShown) {
            android.util.Log.i("BLOCKIFY_TRIGGER", "InterventionController: Overlay not displayed, launching InterventionActivity fallback")
            val fallbackSuccess = launchInterventionActivity()
            if (!fallbackSuccess) {
                _interventionState.value = InterventionState(isActive = false)
                return false
            }
            startCountdown()
        }

        // 2. Perform state updates & background audit logging AFTER overlay is triggered
        val interventionId = UUID.randomUUID().toString()
        _interventionState.value = InterventionState(
            isActive = true,
            interventionId = interventionId,
            triggerTimestampMillis = System.currentTimeMillis(),
            countdownRemainingSeconds = 3,
            statusMessage = if (isAntiTamper) {
                "Blockify Protection is active. Tampering or disabling is blocked."
            } else {
                "You asked Blockify to protect you from this content."
            },
            navigationAttempted = false,
            fallbackRequired = !overlayShown,
            isAntiTamper = isAntiTamper
        )

        scope.launch(Dispatchers.IO) {
            onRecordAuditEvent(SafeEventCode.HIGH_CONFIDENCE_INTERVENTION, "Intervention initiated: $interventionId (antiTamper=$isAntiTamper, overlayShown=$overlayShown)")
        }

        return true
    }

    private fun launchInterventionActivity(): Boolean {
        return try {
            val intent = Intent(context, InterventionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            performNavigationAction()
            false
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (sec in 3 downTo 1) {
                _interventionState.value = _interventionState.value.copy(
                    countdownRemainingSeconds = sec
                )
                delay(1000L)
            }
            performNavigationAction()
        }
    }

    /**
     * Executes the strongest compliant navigation action permitted by Android.
     */
    fun performNavigationAction() {
        countdownJob?.cancel()
        val serviceNavigated = RecoveryAccessibilityService.performCompliantNavigationAway()

        if (!serviceNavigated) {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        _interventionState.value = _interventionState.value.copy(
            navigationAttempted = true,
            fallbackRequired = false,
            statusMessage = "Protected! Navigated to safe screen."
        )
        dismissIntervention()
    }

    fun dismissIntervention() {
        android.util.Log.i("BLOCKIFY_TRIGGER", "InterventionController: dismissIntervention called, reset isActive=false")
        countdownJob?.cancel()
        countdownJob = null
        RecoveryAccessibilityService.dismissSystemIntervention()
        _interventionState.value = InterventionState(isActive = false)
        // Reset timestamp so subsequent genuine triggers can fire immediately
        lastInterventionTimestamp = 0L
    }

    fun reset() {
        dismissIntervention()
    }

    fun reportFalsePositive(category: String, suppressionToken: String? = null) {
        scope.launch(Dispatchers.IO) {
            val report = FalsePositiveReport(
                reportId = UUID.randomUUID().toString(),
                timestampMillis = System.currentTimeMillis(),
                category = category,
                safeSuppressionToken = suppressionToken
            )
            onRecordFalsePositive(report)
            dismissIntervention()
        }
    }
}
