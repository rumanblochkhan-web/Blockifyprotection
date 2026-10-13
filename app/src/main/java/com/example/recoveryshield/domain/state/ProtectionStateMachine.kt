package com.example.recoveryshield.domain.state

import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.ProtectionState

/**
 * Deterministic state machine governing the voluntary protection lifecycle.
 * Rejects illegal transitions safely without crashing.
 */
class ProtectionStateMachine(initialState: ProtectionState = ProtectionState.FIRST_RUN) {

    var currentState: ProtectionState = initialState
        private set

    /**
     * Attempts a validated state transition.
     * @return true if the transition succeeded; false if rejected as illegal.
     */
    fun transitionTo(targetState: ProtectionState): Boolean {
        if (isValidTransition(currentState, targetState)) {
            currentState = targetState
            return true
        }
        return false
    }

    /**
     * Determines whether a transition from [from] to [to] is permitted by policy.
     */
    fun isValidTransition(from: ProtectionState, to: ProtectionState): Boolean {
        if (from == to) return true // Idempotent

        return when (from) {
            ProtectionState.FIRST_RUN -> to in setOf(
                ProtectionState.SETUP_REQUIRED,
                ProtectionState.PERMISSION_REQUIRED,
                ProtectionState.READY_BUT_INACTIVE,
                ProtectionState.ACTIVE
            )

            ProtectionState.SETUP_REQUIRED -> to in setOf(
                ProtectionState.PERMISSION_REQUIRED,
                ProtectionState.READY_BUT_INACTIVE,
                ProtectionState.ACTIVE
            )

            ProtectionState.PERMISSION_REQUIRED -> to in setOf(
                ProtectionState.READY_BUT_INACTIVE,
                ProtectionState.SETUP_REQUIRED,
                ProtectionState.DEGRADED,
                ProtectionState.ACTIVE
            )

            ProtectionState.READY_BUT_INACTIVE -> to in setOf(
                ProtectionState.ACTIVE,
                ProtectionState.PERMISSION_REQUIRED,
                ProtectionState.DEGRADED
            )

            ProtectionState.ACTIVE -> to in setOf(
                ProtectionState.INTERVENTION,
                ProtectionState.COOLDOWN_REQUESTED,
                ProtectionState.COOLDOWN_ACTIVE,
                ProtectionState.DISABLED,
                ProtectionState.READY_BUT_INACTIVE,
                ProtectionState.DEGRADED,
                ProtectionState.ERROR
            )

            ProtectionState.INTERVENTION -> to in setOf(
                ProtectionState.ACTIVE,
                ProtectionState.COOLDOWN_ACTIVE,
                ProtectionState.DEGRADED
            )

            ProtectionState.COOLDOWN_REQUESTED -> to in setOf(
                ProtectionState.COOLDOWN_ACTIVE,
                ProtectionState.ACTIVE,
                ProtectionState.DEGRADED
            )

            ProtectionState.COOLDOWN_ACTIVE -> to in setOf(
                ProtectionState.DISABLE_CONFIRMATION,
                ProtectionState.INTERVENTION,
                ProtectionState.ACTIVE, // If user cancels disable
                ProtectionState.DEGRADED
            )

            ProtectionState.DISABLE_CONFIRMATION -> to in setOf(
                ProtectionState.DISABLED,
                ProtectionState.ACTIVE, // If user cancels at final confirmation
                ProtectionState.COOLDOWN_ACTIVE,
                ProtectionState.DEGRADED
            )

            ProtectionState.DISABLED -> to in setOf(
                ProtectionState.READY_BUT_INACTIVE,
                ProtectionState.SETUP_REQUIRED,
                ProtectionState.ACTIVE
            )

            ProtectionState.DEGRADED -> to in setOf(
                ProtectionState.ACTIVE,
                ProtectionState.READY_BUT_INACTIVE,
                ProtectionState.PERMISSION_REQUIRED,
                ProtectionState.COOLDOWN_ACTIVE,
                ProtectionState.DISABLED
            )

            ProtectionState.ERROR -> to in setOf(
                ProtectionState.SETUP_REQUIRED,
                ProtectionState.PERMISSION_REQUIRED,
                ProtectionState.READY_BUT_INACTIVE,
                ProtectionState.ACTIVE
            )
        }
    }

    /**
     * Reconciles current state with live system permissions.
     * Prevents falsely claiming active protection when permissions were revoked.
     */
    fun reconcileWithPermissions(permissions: PermissionState): ProtectionState {
        when (currentState) {
            ProtectionState.ACTIVE, ProtectionState.COOLDOWN_ACTIVE -> {
                if (!permissions.canActivate) {
                    currentState = ProtectionState.DEGRADED
                }
            }
            ProtectionState.DEGRADED -> {
                if (permissions.canActivate) {
                    currentState = ProtectionState.ACTIVE
                }
            }
            ProtectionState.PERMISSION_REQUIRED -> {
                if (permissions.canActivate) {
                    currentState = ProtectionState.READY_BUT_INACTIVE
                }
            }
            ProtectionState.FIRST_RUN -> {
                if (permissions.canActivate) {
                    currentState = ProtectionState.READY_BUT_INACTIVE
                } else {
                    currentState = ProtectionState.PERMISSION_REQUIRED
                }
            }
            ProtectionState.READY_BUT_INACTIVE -> {
                if (!permissions.canActivate) {
                    currentState = ProtectionState.PERMISSION_REQUIRED
                }
                // Keep READY_BUT_INACTIVE when permissions are granted until user explicitly activates
            }
            ProtectionState.DISABLED -> {
                if (!permissions.canActivate) {
                    currentState = ProtectionState.PERMISSION_REQUIRED
                }
                // Keep DISABLED until user explicitly taps activate
            }
            else -> {
                // Keep current state
            }
        }
        return currentState
    }

    fun forceRestore(restoredState: ProtectionState) {
        currentState = restoredState
    }
}
