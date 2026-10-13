package com.example.recoveryshield.diagnostics

import android.content.Context
import android.os.Build
import com.example.recoveryshield.domain.model.DeviceCompatibility
import com.example.recoveryshield.permissions.PermissionOrchestrator
import java.util.Locale

class DeviceCompatibilityRepository(
    private val context: Context,
    private val permissionOrchestrator: PermissionOrchestrator
) {
    fun getCompatibilityInfo(): DeviceCompatibility {
        val permissions = permissionOrchestrator.checkAllPermissions()
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val isOemKnownForAggressiveBatteryKilling = manufacturer in setOf("xiaomi", "huawei", "oppo", "vivo", "oneplus", "transsion")

        return DeviceCompatibility(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            accessibilityEnabled = permissions.accessibilityGranted,
            overlayCapable = permissions.overlayGranted,
            batteryOptimizationIgnored = permissions.batteryOptimizationIgnored,
            isMiuiOrOemRestricted = isOemKnownForAggressiveBatteryKilling
        )
    }
}
