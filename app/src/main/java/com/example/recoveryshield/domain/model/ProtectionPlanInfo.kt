package com.example.recoveryshield.domain.model

import android.content.Context

enum class ProtectionPlanType {
    FREE_TRIAL,
    REWARD_EXTENDED,
    PREMIUM,
    EXPIRED
}

/**
 * Authoritative protection plan & days model.
 * Uses timestamps rather than static counters to prevent client clock tampering.
 * Enforces subscription encryption: all protection shuts off when trial/plan expires.
 */
data class ProtectionPlanInfo(
    val planType: ProtectionPlanType = ProtectionPlanType.FREE_TRIAL,
    val trialStartedAtMillis: Long = 0L,
    val trialExpiresAtMillis: Long = 0L,
    val protectionExpiresAtMillis: Long = 0L,
    val isPremiumActive: Boolean = false,
    val premiumExpiresAtMillis: Long? = null,
    val lastRewardedAdAtMillis: Long? = null
) {
    companion object {
        fun getCachedPlanSync(context: Context): ProtectionPlanInfo {
            return try {
                val prefs = context.getSharedPreferences("plan_prefs", Context.MODE_PRIVATE)
                val isPremium = prefs.getBoolean("is_premium", false)
                val trialExpires = prefs.getLong("trial_expires", 0L)
                val protectionExpires = prefs.getLong("protection_expires", trialExpires)
                val premiumExpires = prefs.getLong("premium_expires", 0L)
                val planTypeName = prefs.getString("plan_type", ProtectionPlanType.FREE_TRIAL.name) ?: ProtectionPlanType.FREE_TRIAL.name
                val planType = runCatching { ProtectionPlanType.valueOf(planTypeName) }.getOrDefault(ProtectionPlanType.FREE_TRIAL)
                val lastRewarded = prefs.getLong("last_rewarded_ad", 0L)
                val trialStarted = prefs.getLong("trial_started", 0L)

                val now = System.currentTimeMillis()
                val effectiveTrialExpires = if (trialExpires <= 0L) {
                    val defaultTrial = now + 20L * 24L * 60L * 60L * 1000L
                    prefs.edit()
                        .putLong("trial_expires", defaultTrial)
                        .putLong("protection_expires", defaultTrial)
                        .putLong("trial_started", now)
                        .putString("plan_type", ProtectionPlanType.FREE_TRIAL.name)
                        .apply()
                    defaultTrial
                } else {
                    trialExpires
                }
                val effectiveProtectionExpires = if (protectionExpires <= 0L) effectiveTrialExpires else protectionExpires

                ProtectionPlanInfo(
                    planType = if (isPremium) ProtectionPlanType.PREMIUM else planType,
                    trialStartedAtMillis = if (trialStarted <= 0L) now else trialStarted,
                    trialExpiresAtMillis = effectiveTrialExpires,
                    protectionExpiresAtMillis = effectiveProtectionExpires,
                    isPremiumActive = isPremium,
                    premiumExpiresAtMillis = if (premiumExpires > 0L) premiumExpires else null,
                    lastRewardedAdAtMillis = if (lastRewarded > 0L) lastRewarded else null
                )
            } catch (_: Throwable) {
                ProtectionPlanInfo()
            }
        }
    }

    val isLifetimePremium: Boolean
        get() = isPremiumActive && (premiumExpiresAtMillis == null || premiumExpiresAtMillis <= 0L || premiumExpiresAtMillis > 2500000000000L)

    val isProtectionDayActive: Boolean
        get() {
            val now = System.currentTimeMillis()
            if (isPremiumActive) {
                if (isLifetimePremium) return true
                val expiry = premiumExpiresAtMillis ?: 0L
                return expiry > now
            }
            return now < protectionExpiresAtMillis || now < trialExpiresAtMillis
        }

    val hasAccess: Boolean
        get() = isProtectionDayActive

    val isExpired: Boolean
        get() = !isProtectionDayActive

    val remainingMillis: Long
        get() {
            val now = System.currentTimeMillis()
            if (isPremiumActive) {
                if (isLifetimePremium) return Long.MAX_VALUE
                val expiry = premiumExpiresAtMillis ?: 0L
                val diff = expiry - now
                return if (diff > 0L) diff else 0L
            }
            val expiry = maxOf(protectionExpiresAtMillis, trialExpiresAtMillis)
            val diff = expiry - now
            return if (diff > 0L) diff else 0L
        }

    val remainingDays: Long
        get() {
            if (isLifetimePremium) return 3650L
            return remainingMillis / (1000L * 60L * 60L * 24L)
        }

    val remainingHoursPart: Long
        get() {
            if (isLifetimePremium) return 0L
            return (remainingMillis / (1000L * 60L * 60L)) % 24L
        }

    val remainingMinutesPart: Long
        get() {
            if (isLifetimePremium) return 0L
            return (remainingMillis / (1000L * 60L)) % 60L
        }

    val remainingSecondsPart: Long
        get() {
            if (isLifetimePremium) return 0L
            return (remainingMillis / 1000L) % 60L
        }

    val remainingHours: Long
        get() {
            val now = System.currentTimeMillis()
            if (isPremiumActive) {
                if (isLifetimePremium) return 99999L
                val expiry = premiumExpiresAtMillis ?: 0L
                val diff = expiry - now
                return if (diff > 0) diff / (1000 * 60 * 60) else 0L
            }
            val expiry = maxOf(protectionExpiresAtMillis, trialExpiresAtMillis)
            val diff = expiry - now
            return if (diff > 0) diff / (1000 * 60 * 60) else 0L
        }

    val remainingDaysCeil: Int
        get() {
            val now = System.currentTimeMillis()
            if (isPremiumActive) {
                if (isLifetimePremium) return 3650
                val expiry = premiumExpiresAtMillis ?: 0L
                val diff = expiry - now
                return if (diff > 0) ((diff + 86400000L - 1L) / 86400000L).toInt() else 0
            }
            val expiry = maxOf(protectionExpiresAtMillis, trialExpiresAtMillis)
            val diff = expiry - now
            return if (diff > 0) ((diff + 86400000L - 1L) / 86400000L).toInt() else 0
        }

    val liveCountdownFormatted: String
        get() {
            if (!isProtectionDayActive) {
                return "Expired"
            }
            if (isLifetimePremium) {
                return "Lifetime Unlimited"
            }
            val d = remainingDays
            val h = remainingHoursPart
            val m = remainingMinutesPart
            val s = remainingSecondsPart
            return when {
                d > 0 -> "${d}d ${h}h ${m}m ${s}s"
                h > 0 -> "${h}h ${m}m ${s}s"
                else -> "${m}m ${s}s"
            }
        }

    val countdownFullFormatted: String
        get() {
            if (!isProtectionDayActive) {
                return "Subscription Required"
            }
            if (isLifetimePremium) {
                return "Lifetime Active"
            }
            val d = remainingDays
            val h = remainingHoursPart
            val m = remainingMinutesPart
            return when {
                d > 0 -> "$d Days $h Hours $m Mins Left"
                h > 0 -> "$h Hours $m Mins Left"
                else -> "$m Mins Left"
            }
        }

    val remainingTimeFormatted: String
        get() {
            if (!isProtectionDayActive) {
                return "Expired"
            }
            if (isLifetimePremium) {
                return "Lifetime Active"
            }
            val days = remainingDaysCeil
            val hours = remainingHours % 24
            return when {
                days > 1 -> "$days days remaining"
                days == 1 -> "1 day (${remainingHours}h) remaining"
                else -> "${remainingHours}h remaining"
            }
        }

    val canWatchRewardedAd: Boolean
        get() {
            if (isPremiumActive && isProtectionDayActive) return false
            return true
        }

    val hoursUntilNextAd: Long
        get() {
            val lastAd = lastRewardedAdAtMillis ?: return 0L
            val nextAllowed = lastAd + 24 * 60 * 60 * 1000L
            val diff = nextAllowed - System.currentTimeMillis()
            return if (diff > 0) (diff / (1000 * 60 * 60)) + 1 else 0L
        }
}
