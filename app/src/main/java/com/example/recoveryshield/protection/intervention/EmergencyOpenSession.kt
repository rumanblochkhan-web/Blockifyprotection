package com.example.recoveryshield.protection.intervention

import android.os.SystemClock

/**
 * Authoritative session tracker for Emergency Open authorization.
 * Locks the Blockify App Info and Settings page to protect user privacy.
 * Allows access only after entering a valid 8-digit emergency authorization code.
 */
object EmergencyOpenSession {

    // Default authorization window: 5 minutes once code is successfully entered
    private const val DEFAULT_AUTH_WINDOW_MS = 5 * 60 * 1000L

    @Volatile
    private var authorizedUntilUptimeMs: Long = 0L

    @Volatile
    var isPromptVisible: Boolean = false

    /**
     * Checks whether App Info access is currently authorized.
     */
    fun isAppInfoAuthorized(): Boolean {
        val now = SystemClock.uptimeMillis()
        return now < authorizedUntilUptimeMs
    }

    /**
     * Grants temporary authorization to open and view the Blockify App Info page.
     */
    fun authorizeAppInfoAccess(durationMs: Long = DEFAULT_AUTH_WINDOW_MS) {
        authorizedUntilUptimeMs = SystemClock.uptimeMillis() + durationMs
        isPromptVisible = false
    }

    /**
     * Immediately revokes authorization and locks App Info again.
     */
    fun revoke() {
        authorizedUntilUptimeMs = 0L
        isPromptVisible = false
    }
}
