package com.example.recoveryshield.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * Accurately detects and represents the device administration and enterprise management status.
 */
enum class DeviceManagementStatus {
    NOT_ACTIVE,
    DEVICE_ADMIN,
    PROFILE_OWNER,
    DEVICE_OWNER
}

/**
 * Isolated Device Admin & Management Manager for Blockify.
 * Strictly uses official Android DevicePolicyManager APIs and never pretends
 * standard Device Admin is Device Owner.
 */
class DeviceManagementManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceMgmtManager"
    }

    val adminComponent: ComponentName by lazy {
        ComponentName(context, BlockifyDeviceAdminReceiver::class.java)
    }

    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    /**
     * Determines the exact management level currently active for Blockify.
     */
    fun getManagementStatus(): DeviceManagementStatus {
        val packageName = context.packageName

        // 1. Check Device Owner (highest privilege, enterprise provisioned)
        try {
            if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
                return DeviceManagementStatus.DEVICE_OWNER
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking isDeviceOwnerApp: ${e.message}")
        }

        // 2. Check Profile Owner (work profile)
        try {
            if (devicePolicyManager.isProfileOwnerApp(packageName)) {
                return DeviceManagementStatus.PROFILE_OWNER
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking isProfileOwnerApp: ${e.message}")
        }

        // 3. Check Standard Voluntary Device Admin
        try {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                return DeviceManagementStatus.DEVICE_ADMIN
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking isAdminActive: ${e.message}")
        }

        return DeviceManagementStatus.NOT_ACTIVE
    }

    /**
     * Returns true if any level of Device Admin or Owner is active.
     */
    fun isDeviceAdminActive(): Boolean {
        return getManagementStatus() != DeviceManagementStatus.NOT_ACTIVE
    }

    /**
     * Creates standard system intent for the user to voluntarily activate Device Admin.
     * Note: FLAG_ACTIVITY_NEW_TASK must NOT be set on ACTION_ADD_DEVICE_ADMIN because Android's
     * system DeviceAdminAdd activity explicitly checks for it and finishes immediately if present.
     */
    fun createActivateAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Used only to prevent unauthorized uninstall of Blockify. Will not track location, take screenshots, or wipe your device."
            )
        }
    }

    /**
     * Applies restrictions only if authorized in the current management context.
     * Always catches SecurityException and never crashes.
     */
    fun applyAuthorizedPolicies(): Boolean {
        val status = getManagementStatus()
        try {
            if (status == DeviceManagementStatus.DEVICE_OWNER || status == DeviceManagementStatus.PROFILE_OWNER) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
                    Log.i(TAG, "Applied DISALLOW_UNINSTALL_APPS restriction successfully.")
                    return true
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException applying policies: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error applying policies: ${e.message}")
        }
        return false
    }

    /**
     * Removes restrictions gracefully if clearing protection or running Emergency Stop.
     */
    fun removeAuthorizedPolicies(): Boolean {
        val status = getManagementStatus()
        try {
            if (status == DeviceManagementStatus.DEVICE_OWNER || status == DeviceManagementStatus.PROFILE_OWNER) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS)
                    Log.i(TAG, "Cleared DISALLOW_UNINSTALL_APPS restriction successfully.")
                    return true
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException removing policies: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error removing policies: ${e.message}")
        }
        return false
    }
}
