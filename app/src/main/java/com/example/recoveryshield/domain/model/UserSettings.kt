package com.example.recoveryshield.domain.model

data class UserSettings(
    val cooldownDurationMinutes: Int = 120, // Default 2 hours
    val interventionCountdownSeconds: Int = 3,
    val overlayInterventionEnabled: Boolean = true,
    val safeContextFilterEnabled: Boolean = true,
    val customSafeTokens: List<String> = emptyList(),
    val antiUninstallProtectionEnabled: Boolean = true
)
