package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.recoveryshield.admin.DeviceManagementManager
import com.example.recoveryshield.admin.DeviceManagementStatus
import com.example.recoveryshield.domain.model.ProtectionCapabilityStatus
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.notifications.ProtectionNotificationHelper
import com.example.recoveryshield.permissions.PermissionOrchestrator
import com.example.recoveryshield.protection.intervention.InterventionController
import com.example.recoveryshield.protection.network.DefaultNetworkProtectionProvider
import com.example.recoveryshield.protection.network.NetworkProtectionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardenedInterventionLifecycleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `consecutive triggers in quick succession do not freeze or lock up`() = runTest {
        val controller = InterventionController(
            context = context,
            onRecordAuditEvent = { _, _ -> },
            onRecordFalsePositive = { _ -> }
        )

        // Trigger 1
        val trigger1 = controller.triggerIntervention("Trigger 1 test", isAntiTamper = false)
        assertTrue("Trigger 1 must initiate successfully", trigger1)
        assertTrue("Controller state must be active during trigger 1", controller.interventionState.value.isActive)

        // Dismiss 1 (simulating user leaving or countdown completing)
        controller.dismissIntervention()
        assertFalse("Controller state must reset to inactive after dismissal", controller.interventionState.value.isActive)

        // Trigger 2 (immediately following cleanup)
        val trigger2 = controller.triggerIntervention("Trigger 2 test", isAntiTamper = false)
        assertTrue("Trigger 2 must fire immediately without one-time lockout", trigger2)
        assertTrue("Controller state must be active during trigger 2", controller.interventionState.value.isActive)

        // Dismiss 2
        controller.dismissIntervention()
        assertFalse("Controller state must reset to inactive after dismissal 2", controller.interventionState.value.isActive)

        // Trigger 3 (third consecutive trigger)
        val trigger3 = controller.triggerIntervention("Trigger 3 test", isAntiTamper = false)
        assertTrue("Trigger 3 must fire immediately without lockout", trigger3)
        assertTrue("Controller state must be active during trigger 3", controller.interventionState.value.isActive)

        // Cleanup
        controller.reset()
        assertFalse("Controller must be completely idle after reset", controller.interventionState.value.isActive)
    }

    @Test
    fun `device management manager handles checks safely without throwing exceptions`() {
        val manager = DeviceManagementManager(context)
        val status = manager.getManagementStatus()
        assertNotNull(status)
        // In standard test environment, should be NOT_ACTIVE or standard admin
        assertTrue(
            status == DeviceManagementStatus.NOT_ACTIVE ||
            status == DeviceManagementStatus.DEVICE_ADMIN
        )

        val intent = manager.createActivateAdminIntent()
        assertNotNull(intent)
        assertEquals(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.action)

        // Test safe policy application (must return boolean without unhandled security exceptions)
        val policyApplied = manager.applyAuthorizedPolicies()
        assertFalse(policyApplied) // In standard unit test, non-device-owner gracefully returns false

        val policyRemoved = manager.removeAuthorizedPolicies()
        assertFalse(policyRemoved)
    }

    @Test
    fun `permission orchestrator checks all capabilities and exposes protection status`() {
        val orchestrator = PermissionOrchestrator(context)
        val state = orchestrator.checkAllPermissions()
        assertNotNull(state)
        assertNotNull(state.accessibilityStatus)
        assertNotNull(state.overlayStatus)
        assertNotNull(state.notificationStatus)
        assertNotNull(state.deviceAdminStatus)
        assertNotNull(state.managementStatus)
    }

    @Test
    fun `protection notification helper handles state transitions safely`() {
        val helper = ProtectionNotificationHelper(context)
        helper.syncNotificationWithState(ProtectionState.ACTIVE)
        helper.syncNotificationWithState(ProtectionState.COOLDOWN_ACTIVE)
        helper.syncNotificationWithState(ProtectionState.DISABLED)
        helper.dismissAllNotifications()
    }

    @Test
    fun `network protection provider safe default abstraction works cleanly`() = runTest {
        val provider = DefaultNetworkProtectionProvider()
        assertEquals(NetworkProtectionStatus.NOT_CONFIGURED, provider.status.value)
        val enabled = provider.enableNetworkProtection()
        assertFalse(enabled)
        val disabled = provider.disableNetworkProtection()
        assertTrue(disabled)
        assertEquals(NetworkProtectionStatus.DISABLED, provider.status.value)
    }

    @Test
    fun `repeated anti-tamper App Info triggers execute reliably through full lifecycle without one-time lockout`() = runTest {
        val controller = InterventionController(
            context = context,
            onRecordAuditEvent = { _, _ -> },
            onRecordFalsePositive = { _ -> }
        )

        // Attempt 1: App Info opened -> Anti-tamper trigger
        val attempt1 = controller.triggerIntervention("Anti-tamper: App Info", isAntiTamper = true)
        assertTrue("Attempt 1 must succeed", attempt1)
        assertTrue("Must be in active intervention state", controller.interventionState.value.isActive)

        // Duplicate event while countdown is active must be ignored
        val duplicateAttempt = controller.triggerIntervention("Duplicate event while countdown running", isAntiTamper = true)
        assertFalse("Duplicate event during active countdown must be ignored", duplicateAttempt)

        // Dismiss 1 (countdown completed, user navigated away)
        controller.dismissIntervention()
        assertFalse("Must return cleanly to IDLE state", controller.interventionState.value.isActive)

        // Attempt 2: User immediately re-opens App Info
        val attempt2 = controller.triggerIntervention("Anti-tamper: App Info attempt 2", isAntiTamper = true)
        assertTrue("Attempt 2 must succeed immediately with zero artificial delay", attempt2)
        assertTrue("Must be active for attempt 2", controller.interventionState.value.isActive)

        // Dismiss 2
        controller.dismissIntervention()
        assertFalse("Must return cleanly to IDLE state after attempt 2", controller.interventionState.value.isActive)

        // Attempt 3: User tries again
        val attempt3 = controller.triggerIntervention("Anti-tamper: App Info attempt 3", isAntiTamper = true)
        assertTrue("Attempt 3 must succeed without one-time trigger bug", attempt3)
        assertTrue("Must be active for attempt 3", controller.interventionState.value.isActive)

        // Cleanup
        controller.reset()
        assertFalse("Must be completely idle after reset", controller.interventionState.value.isActive)
    }
}
