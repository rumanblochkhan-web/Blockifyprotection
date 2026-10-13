package com.example.recoveryshield.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Standard Android DeviceAdminReceiver for Blockify voluntary protection.
 * Activates Device Administrator privileges strictly upon explicit user opt-in
 * via the system Device Administration dialog.
 */
class BlockifyDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "BlockifyDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled voluntarily by user.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.i(TAG, "Device Admin deactivation requested by user.")
        return "Blockify voluntary protection is active. Deactivating Device Admin lowers uninstall deterrence. Use Emergency Stop inside Blockify to safely disable."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled.")
    }
}
