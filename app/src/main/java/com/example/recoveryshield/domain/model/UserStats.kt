package com.example.recoveryshield.domain.model

/**
 * Model representing personalized user statistics.
 * Every newly registered/signed-in user starts strictly with 0 days, 0 streak, 0 interventions.
 */
data class UserStats(
    val protectedDays: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val interventionsCount: Int = 0,
    val protectionTimeHours: Float = 0f,
    val isProtectionActive: Boolean = false,
    val memberSinceMillis: Long = System.currentTimeMillis()
)
