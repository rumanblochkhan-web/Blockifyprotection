package com.example.recoveryshield.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.recoveryshield.protection.engine.ProtectionEngine

/**
 * Ensures state persistence and reconciliation across device reboot and app updates.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("BootReceiver", "Reboot or update detected ($action). Initializing ProtectionEngine.")
            // Trigger singleton initialization to load state and reconcile
            ProtectionEngine.getInstance(context).refreshStateWithPermissions()
            try { com.example.recoveryshield.notifications.ProtectionNotificationHelper.ensureRunningIfActive(context) } catch (_: Throwable) {}
        }
    }
}
