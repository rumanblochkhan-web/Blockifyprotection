package com.example.recoveryshield.protection.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.recoveryshield.notifications.ProtectionNotificationHelper
import com.example.recoveryshield.protection.engine.ProtectionEngine

class ProtectionKeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAlive"

        @Volatile
        var isRunning = false

        fun start(context: Context) {
            try {
                if (!isRunning) {
                    val appContext = context.applicationContext
                    val intent = Intent(appContext, ProtectionKeepAliveService::class.java)
                    ContextCompat.startForegroundService(appContext, intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "start failed: ${t.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val appContext = context.applicationContext
                appContext.stopService(Intent(appContext, ProtectionKeepAliveService::class.java))
            } catch (_: Throwable) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            val helper = ProtectionNotificationHelper(applicationContext)
            val notification = helper.buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    ProtectionNotificationHelper.NOTIFICATION_ID_STATUS,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    ProtectionNotificationHelper.NOTIFICATION_ID_STATUS,
                    notification
                )
            }
            isRunning = true
            START_STICKY
        } catch (t: Throwable) {
            Log.e(TAG, "onStartCommand error: ${t.message}", t)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Do nothing - keep running when user swipes Blockify from recents
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            if (ProtectionEngine.getInstance(applicationContext).isProtectionRuntimeEnabled()) {
                start(applicationContext)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onDestroy restart failed: ${t.message}")
        }
    }
}
