package com.example.recoveryshield.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.recoveryshield.admin.DeviceManagementManager
import com.example.recoveryshield.admin.DeviceManagementStatus
import com.example.recoveryshield.domain.model.PermissionState
import com.example.recoveryshield.domain.model.PermissionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionOrchestrator(
    private val context: Context
) {
    companion object {
        @Volatile
        var isPermissionConfigFlowActive: Boolean = false
            private set

        @Volatile
        var permissionConfigFlowUntil: Long = 0L
            private set

        fun markPermissionFlowActive(durationMs: Long = 120_000L) {
            isPermissionConfigFlowActive = true
            permissionConfigFlowUntil = System.currentTimeMillis() + durationMs
        }

        fun isCurrentlyInPermissionFlow(): Boolean {
            if (!isPermissionConfigFlowActive) return false
            if (System.currentTimeMillis() > permissionConfigFlowUntil) {
                isPermissionConfigFlowActive = false
                return false
            }
            return true
        }

        fun clearPermissionFlow() {
            isPermissionConfigFlowActive = false
            permissionConfigFlowUntil = 0L
        }
    }

    val deviceManagementManager by lazy { DeviceManagementManager(context) }

    private val _permissionState = MutableStateFlow(computePermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private fun computePermissionState(): PermissionState {
        val mgmtStatus = deviceManagementManager.getManagementStatus()
        val isAdminGranted = mgmtStatus != DeviceManagementStatus.NOT_ACTIVE

        return PermissionState(
            accessibilityGranted = isAccessibilityServiceEnabled(),
            overlayGranted = canDrawOverlays(),
            notificationsGranted = isNotificationPermissionGranted(),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(),
            deviceAdminGranted = isAdminGranted,
            managementStatus = mgmtStatus
        )
    }

    fun checkAllPermissions(): PermissionState {
        val state = computePermissionState()
        _permissionState.value = state
        return state
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        // 1. If our service is currently bound and active in memory
        if (com.example.recoveryshield.protection.service.RecoveryAccessibilityService.isServiceRunning()) {
            return true
        }

        // 2. Query AccessibilityManager across all feedback types
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                val expectedPackage = context.packageName
                if (enabledServices.any { it.resolveInfo?.serviceInfo?.packageName == expectedPackage }) {
                    return true
                }
            }
        } catch (_: Exception) {
            // fallback to Settings.Secure
        }

        // 3. Query Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES directly as reliable source of truth
        return try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1

            accessibilityEnabled && (
                enabledServicesSetting.contains(context.packageName) ||
                enabledServicesSetting.contains("RecoveryAccessibilityService") ||
                enabledServicesSetting.contains("ProtectionAccessibilityService")
            )
        } catch (_: Exception) {
            false
        }
    }

    fun canDrawOverlays(): Boolean {
        return try {
            Settings.canDrawOverlays(context)
        } catch (_: Exception) {
            false
        }
    }

    fun isNotificationPermissionGranted(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Prior to Android 13, notification permission is granted at install time
            }
        } catch (_: Exception) {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            true
        }
    }

    fun getExplanation(type: PermissionType): String {
        return when (type) {
            PermissionType.ACCESSIBILITY_SERVICE ->
                "Accessibility permission allows Blockify to detect when explicit content is displayed on your screen and immediately trigger a navigation action to protect you. Screen text and personal data are never stored or uploaded."

            PermissionType.SYSTEM_ALERT_WINDOW ->
                "Overlay permission lets Blockify show a clear, 3-second countdown intervention screen immediately over explicit content to guide you away safely."

            PermissionType.POST_NOTIFICATIONS ->
                "Notification permission lets Blockify inform you of the cooling-off status if you ever voluntarily request to disable protection."

            PermissionType.IGNORE_BATTERY_OPTIMIZATION ->
                "Battery optimization exemption ensures the voluntary protection service is not killed silently in the background by OEM aggressive task killers."

            PermissionType.DEVICE_ADMIN ->
                "Device Administrator strengthens voluntary protection by raising friction against accidental uninstallation or hasty bypass."
        }
    }

    fun createIntentForPermission(type: PermissionType): Intent {
        markPermissionFlowActive()
        return when (type) {
            PermissionType.ACCESSIBILITY_SERVICE -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            PermissionType.SYSTEM_ALERT_WINDOW -> {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

            PermissionType.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            }

            PermissionType.IGNORE_BATTERY_OPTIMIZATION -> {
                try {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } catch (_: Exception) {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            }

            PermissionType.DEVICE_ADMIN -> {
                deviceManagementManager.createActivateAdminIntent()
            }
        }
    }
}
