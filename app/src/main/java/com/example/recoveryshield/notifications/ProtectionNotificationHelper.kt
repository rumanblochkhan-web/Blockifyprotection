package com.example.recoveryshield.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.protection.service.ProtectionKeepAliveService

/**
 * Handles clean, non-intrusive system notifications for Blockify voluntary protection.
 * Follows Android 13+ runtime permission guidelines, creates explicit channels,
 * and maintains a single deterministic notification ID.
 */
class ProtectionNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "blockify_protection_status"
        const val CHANNEL_NAME = "Blockify Protection Status"
        const val CHANNEL_ID_RUNNING = "blockify_protection_running"
        const val CHANNEL_NAME_RUNNING = "Blockify Protection"
        const val NOTIFICATION_ID_STATUS = 1001
        const val NOTIFICATION_ID_COOLDOWN = 1002

        fun ensureRunningIfActive(context: Context) {
            try {
                if (ProtectionKeepAliveService.isRunning) return
                val engine = com.example.recoveryshield.protection.engine.ProtectionEngine.getInstance(context.applicationContext)
                if (engine.isProtectionRuntimeEnabled()) {
                    ProtectionKeepAliveService.start(context.applicationContext)
                }
            } catch (t: Throwable) {
                android.util.Log.w("NotificationHelper", "ensureRunningIfActive failed: ${t.message}")
            }
        }
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channelLegacy = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows Blockify voluntary protection status."
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channelLegacy)

                val channelRunning = NotificationChannel(
                    CHANNEL_ID_RUNNING,
                    CHANNEL_NAME_RUNNING,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows active Blockify voluntary background protection."
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channelRunning)
            } catch (_: Throwable) {}
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildForegroundNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_RUNNING)
            .setSmallIcon(R.drawable.ic_blockify_notification)
            .setContentTitle("Blockify protection is active")
            .setContentText("Running in the background to keep you protected")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    /**
     * Updates or dismisses the status notification based on current ProtectionState.
     */
    fun syncNotificationWithState(state: ProtectionState) {
        try {
            val plan = ProtectionPlanInfo.getCachedPlanSync(context)
            val shouldRun = state in setOf(
                ProtectionState.ACTIVE,
                ProtectionState.INTERVENTION,
                ProtectionState.COOLDOWN_REQUESTED,
                ProtectionState.COOLDOWN_ACTIVE,
                ProtectionState.DISABLE_CONFIRMATION
            ) && plan.isProtectionDayActive

            if (shouldRun) {
                ProtectionKeepAliveService.start(context)
            } else {
                ProtectionKeepAliveService.stop(context)
                dismissAllNotifications()
            }
        } catch (t: Throwable) {
            android.util.Log.w("NotificationHelper", "syncNotificationWithState error: ${t.message}")
        }
    }

    private fun showActiveNotification() {
        dismissAllNotifications()
    }

    private fun showCooldownNotification() {
        dismissAllNotifications()
    }

    fun dismissAllNotifications() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_STATUS)
            notificationManager.cancel(NOTIFICATION_ID_COOLDOWN)
        } catch (_: Exception) {
            // Ignore
        }
    }
}
