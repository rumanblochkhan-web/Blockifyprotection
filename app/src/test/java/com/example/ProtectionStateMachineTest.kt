package com.example

import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.domain.state.ProtectionStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProtectionStateMachineTest {

    private lateinit var stateMachine: ProtectionStateMachine

    @Before
    fun setUp() {
        stateMachine = ProtectionStateMachine(ProtectionState.FIRST_RUN)
    }

    @Test
    fun `initial state is FIRST_RUN`() {
        assertEquals(ProtectionState.FIRST_RUN, stateMachine.currentState)
    }

    @Test
    fun `legal transition from FIRST_RUN to SETUP_REQUIRED succeeds`() {
        val success = stateMachine.transitionTo(ProtectionState.SETUP_REQUIRED)
        assertTrue(success)
        assertEquals(ProtectionState.SETUP_REQUIRED, stateMachine.currentState)
    }

    @Test
    fun `illegal transition from FIRST_RUN directly to DISABLED is rejected`() {
        val success = stateMachine.transitionTo(ProtectionState.DISABLED)
        assertFalse(success)
        assertEquals(ProtectionState.FIRST_RUN, stateMachine.currentState)
    }

    @Test
    fun `full voluntary activation lifecycle transitions correctly`() {
        assertTrue(stateMachine.transitionTo(ProtectionState.SETUP_REQUIRED))
        assertTrue(stateMachine.transitionTo(ProtectionState.READY_BUT_INACTIVE))
        assertTrue(stateMachine.transitionTo(ProtectionState.ACTIVE))
        assertEquals(ProtectionState.ACTIVE, stateMachine.currentState)

        // Trigger intervention
        assertTrue(stateMachine.transitionTo(ProtectionState.INTERVENTION))
        assertEquals(ProtectionState.INTERVENTION, stateMachine.currentState)

        // Dismiss intervention returns to ACTIVE
        assertTrue(stateMachine.transitionTo(ProtectionState.ACTIVE))
        assertEquals(ProtectionState.ACTIVE, stateMachine.currentState)
    }

    @Test
    fun `cooldown disable workflow transitions correctly`() {
        stateMachine.forceRestore(ProtectionState.ACTIVE)

        // Request cooldown
        assertTrue(stateMachine.transitionTo(ProtectionState.COOLDOWN_ACTIVE))
        assertEquals(ProtectionState.COOLDOWN_ACTIVE, stateMachine.currentState)

        // Cooldown expires -> awaiting confirmation
        assertTrue(stateMachine.transitionTo(ProtectionState.DISABLE_CONFIRMATION))
        assertEquals(ProtectionState.DISABLE_CONFIRMATION, stateMachine.currentState)

        // Deliberate confirmation -> DISABLED
        assertTrue(stateMachine.transitionTo(ProtectionState.DISABLED))
        assertEquals(ProtectionState.DISABLED, stateMachine.currentState)
    }

    @Test
    fun `cancel during cooldown returns to ACTIVE`() {
        stateMachine.forceRestore(ProtectionState.COOLDOWN_ACTIVE)
        assertTrue(stateMachine.transitionTo(ProtectionState.ACTIVE))
        assertEquals(ProtectionState.ACTIVE, stateMachine.currentState)
    }

    @Test
    fun `permission loss degrades active protection`() {
        stateMachine.forceRestore(ProtectionState.ACTIVE)

        val revokedPermissions = PermissionState(accessibilityGranted = false)
        val reconciled = stateMachine.reconcileWithPermissions(revokedPermissions)

        assertEquals(ProtectionState.DEGRADED, reconciled)
        assertEquals(ProtectionState.DEGRADED, stateMachine.currentState)

        // Restoring permissions restores ACTIVE state
        val restoredPermissions = PermissionState(accessibilityGranted = true)
        val restored = stateMachine.reconcileWithPermissions(restoredPermissions)

        assertEquals(ProtectionState.ACTIVE, restored)
        assertEquals(ProtectionState.ACTIVE, stateMachine.currentState)
    }
}
