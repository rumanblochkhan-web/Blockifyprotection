package com.example.recoveryshield.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.recoveryshield.domain.model.UserSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface SettingsRepository {
    val settingsFlow: Flow<UserSettings>
    fun getSettings(): UserSettings
    fun updateCooldownDuration(minutes: Int)
    fun updateOverlayEnabled(enabled: Boolean)
    fun updateCountdownSeconds(seconds: Int)
    fun updateSafeContextFilter(enabled: Boolean)
    fun updateAntiUninstallProtection(enabled: Boolean)
}

class SharedPreferencesSettingsRepository(
    context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("recovery_shield_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COOLDOWN_MINUTES = "cooldown_duration_minutes"
        private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_SAFE_CONTEXT = "safe_context_enabled"
        private const val KEY_ANTI_UNINSTALL_ENABLED = "anti_uninstall_protection_enabled"
    }

    override val settingsFlow: Flow<UserSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(getSettings())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getSettings())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override fun getSettings(): UserSettings {
        return UserSettings(
            cooldownDurationMinutes = prefs.getInt(KEY_COOLDOWN_MINUTES, 120),
            interventionCountdownSeconds = prefs.getInt(KEY_COUNTDOWN_SECONDS, 3),
            overlayInterventionEnabled = prefs.getBoolean(KEY_OVERLAY_ENABLED, true),
            safeContextFilterEnabled = prefs.getBoolean(KEY_SAFE_CONTEXT, true),
            antiUninstallProtectionEnabled = prefs.getBoolean(KEY_ANTI_UNINSTALL_ENABLED, true)
        )
    }

    override fun updateCooldownDuration(minutes: Int) {
        prefs.edit().putInt(KEY_COOLDOWN_MINUTES, minutes.coerceAtLeast(5)).apply()
    }

    override fun updateOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    }

    override fun updateCountdownSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_COUNTDOWN_SECONDS, seconds.coerceIn(3, 30)).apply()
    }

    override fun updateSafeContextFilter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAFE_CONTEXT, enabled).apply()
    }

    override fun updateAntiUninstallProtection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANTI_UNINSTALL_ENABLED, enabled).apply()
    }
}
