package com.example.recoveryshield.protection.detector

import java.util.concurrent.ConcurrentHashMap

/**
 * Manages privacy-preserving local safe contexts and false-positive suppression tokens.
 * A safe context can suppress ambiguous triggers but CANNOT disable the global protection engine.
 */
class AllowlistManager {

    private val safeTokens = ConcurrentHashMap.newKeySet<String>()
    private val safePackages = ConcurrentHashMap.newKeySet<String>()

    init {
        // Known safe recovery, educational, and app identity tokens
        safeTokens.addAll(
            listOf(
                "porn protection",
                "porn blocker",
                "porn shield",
                "recovery shield",
                "recoveryshield",
                "addiction recovery",
                "quitting porn",
                "porn recovery",
                "stop porn",
                "anti porn",
                "free from porn",
                "pornography addiction",
                "sexual health clinic",
                "breast cancer awareness",
                "sex education curriculum",
                "psychotherapy",
                "support group",
                "reboot nation",
                "nofap community",
                "sober recovery"
            )
        )

        safePackages.addAll(
            listOf(
                "com.openai.chatgpt",
                "com.whatsapp",
                "com.whatsapp.w4b",
                "org.telegram.messenger",
                "com.google.android.GoogleCamera",
                "com.android.camera",
                "com.sec.android.app.camera",
                "com.google.android.apps.photos",
                "com.sec.android.gallery3d",
                "com.android.gallery3d",
                "com.google.android.apps.messaging",
                "com.android.settings",
                "com.google.android.settings",
                "com.samsung.android.settings",
                "com.miui.securitycenter",
                "com.miui.cleanmaster",
                "com.coloros.settings",
                "com.oppo.settings",
                "com.vivo.settings",
                "com.huawei.android.launcher",
                "com.android.systemui",
                "com.android.launcher3",
                "com.google.android.apps.nexuslauncher",
                "com.sec.android.app.launcher",
                "com.android.vending", // Play Store
                "com.google.android.deskclock",
                "com.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator",
                "com.android.dialer",
                "com.google.android.dialer",
                "com.samsung.android.dialer",
                "com.android.incallui",
                "com.google.android.incallui",
                "com.samsung.android.incallui",
                "com.android.phone",
                "com.android.server.telecom",
                "com.android.contacts",
                "com.google.android.contacts",
                "com.samsung.android.app.contacts"
            )
        )
    }

    fun isPackageSafeContext(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return safePackages.contains(lower) ||
                lower.contains("camera") ||
                lower.contains("gallery") ||
                lower.contains("chatgpt") ||
                lower.contains("openai") ||
                lower.contains("dialer") ||
                lower.contains("incallui") ||
                lower.contains("telecom") ||
                lower.contains("telephony") ||
                lower.contains("contacts") ||
                lower.contains("deskclock") ||
                lower.contains("calculator") ||
                lower.contains("systemui") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.endsWith(".settings") ||
                lower.contains(".settings.") ||
                lower.equals("com.android.settings", ignoreCase = true)
    }

    fun isTokenSuppressed(text: String): Boolean {
        val lower = text.lowercase()
        return safeTokens.any { safeToken -> lower.contains(safeToken) }
    }

    fun addSafeToken(token: String) {
        val cleaned = token.trim().lowercase()
        if (cleaned.length >= 4) {
            safeTokens.add(cleaned)
        }
    }

    fun getSafeTokens(): Set<String> = safeTokens.toSet()
}
