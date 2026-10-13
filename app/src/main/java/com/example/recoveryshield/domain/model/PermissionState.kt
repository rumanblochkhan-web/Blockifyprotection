package com.example.recoveryshield.domain.model

import com.example.recoveryshield.admin.DeviceManagementStatus

/**
 * Universal status enum for individual capabilities and permissions.
 */
enum class ProtectionCapabilityStatus {
    CONNECTED,
    NOT_CONNECTED,
    REQUIRED,
    OPTIONAL,
    NOT_AVAILABLE,
    ERROR
}

/**
 * Centralized snapshot of system permissions and device capabilities required for voluntary protection.
 */
data class PermissionState(
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val deviceAdminGranted: Boolean = false,
    val managementStatus: DeviceManagementStatus = DeviceManagementStatus.NOT_ACTIVE
) {
    /**
     * Protection can only be activated when the primary accessibility monitoring capability is active.
     */
    val canActivate: Boolean
        get() = accessibilityGranted

    /**
     * Whether all recommended capabilities (including non-mandatory overlay & notifications) are granted.
     */
    val isFullyConfigured: Boolean
        get() = accessibilityGranted && overlayGranted && notificationsGranted

    val accessibilityStatus: ProtectionCapabilityStatus
        get() = if (accessibilityGranted) ProtectionCapabilityStatus.CONNECTED else ProtectionCapabilityStatus.REQUIRED

    val overlayStatus: ProtectionCapabilityStatus
        get() = if (overlayGranted) ProtectionCapabilityStatus.CONNECTED else ProtectionCapabilityStatus.OPTIONAL

    val notificationStatus: ProtectionCapabilityStatus
        get() = if (notificationsGranted) ProtectionCapabilityStatus.CONNECTED else ProtectionCapabilityStatus.OPTIONAL

    val batteryOptimizationStatus: ProtectionCapabilityStatus
        get() = if (batteryOptimizationIgnored) ProtectionCapabilityStatus.CONNECTED else ProtectionCapabilityStatus.OPTIONAL

    val deviceAdminStatus: ProtectionCapabilityStatus
        get() = if (deviceAdminGranted) ProtectionCapabilityStatus.CONNECTED else ProtectionCapabilityStatus.OPTIONAL
}

enum class PermissionType {
    ACCESSIBILITY_SERVICE,
    SYSTEM_ALERT_WINDOW,
    POST_NOTIFICATIONS,
    IGNORE_BATTERY_OPTIMIZATION,
    DEVICE_ADMIN
}
