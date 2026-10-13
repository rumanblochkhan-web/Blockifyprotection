package com.example.recoveryshield.protection.detector

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.R
import com.example.recoveryshield.permissions.PermissionOrchestrator
import com.example.recoveryshield.protection.intervention.EmergencyOpenSession
import com.example.recoveryshield.protection.service.RecoveryAccessibilityService

/**
 * SelfProtectionDetector
 *
 * Isolated detector responsible exclusively for voluntary self-protection / anti-impulsive-uninstall.
 * Shows the standard 5-second cooldown overlay ("Leave Now", "Uninstall attempt guarded by Blockify")
 * ONLY in the following 5 target situations:
 * A) Inside Android Settings on Blockify's App Info page (or tapping Blockify in Settings app list)
 * B) Inside the Uninstall confirmation dialog for Blockify
 * C) Long-pressing the Blockify icon on the home launcher (or interacting with launcher shortcut menu for Blockify)
 * D) Accessibility settings page for Blockify service, or Device Admin deactivation screen for Blockify
 * E) Blockify's page in Google Play Store (when Uninstall button is visible)
 *
 * Strictly filters out any application not in the strict allowlist (Settings, Installer, Play Store, Launcher)
 * to guarantee ZERO false positives in chat apps (ChatGPT, WhatsApp), browsers (Chrome), notes, etc.
 */
class SelfProtectionDetector(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "SelfProtectionDetector"
        const val DEBUG_SELF_PROTECT_TOASTS = true // set false after diagnosis
        const val VERBOSE_PIPELINE_LOGS = false

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.coloros.settings",
            "com.oplus.settings",
            "com.vivo.settings",
            "com.huawei.systemmanager"
        )

        private val INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )

        private const val PLAY_STORE_PACKAGE = "com.android.vending"

        private val POPULAR_LAUNCHERS = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oppo.launcher",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
            "com.motorola.launcher3",
            "com.transsion.hilauncher"
        )

        private val OTHER_POPULAR_APPS = setOf(
            "whatsapp", "instagram", "youtube", "chrome", "tiktok",
            "facebook", "twitter", "snapchat", "telegram", "spotify",
            "netflix", "gmail", "maps", "messaging", "camera", "gallery"
        )
    }

    @Volatile
    private var lastLauncherBlockifyLongPressTime = 0L

    @Volatile
    private var lastTriggerTime = 0L

    @Volatile
    private var cachedLauncherPackages: Set<String> = emptySet()

    @Volatile
    private var lastLauncherCacheUpdateTime = 0L

    @Volatile
    private var lastDebugToastTime = 0L

    @Volatile
    private var settingsLikeCacheClearedAt = 0L

    private val settingsLikeCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun debugToast(msg: String) {
        if (!DEBUG_SELF_PROTECT_TOASTS) return
        val now = SystemClock.uptimeMillis()
        if (now - lastDebugToastTime < 2500L) return
        lastDebugToastTime = now
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { android.widget.Toast.makeText(service, msg, android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
        }
    }

    @Volatile private var recheckPendingUntil = 0L

    private fun scheduleRechecks(recoveryService: RecoveryAccessibilityService) {
        val now = SystemClock.uptimeMillis()
        if (now < recheckPendingUntil) return
        recheckPendingUntil = now + 400L
        for (delay in longArrayOf(100L, 400L, 900L)) {
            recoveryService.postToWorker(delay) {
                try {
                    if (RecoveryAccessibilityService.isOverlayShowing()) return@postToWorker
                    if (EmergencyOpenSession.isAppInfoAuthorized()) return@postToWorker
                    val root = service.rootInActiveWindow ?: return@postToWorker
                    val pkg = root.packageName?.toString()?.lowercase() ?: return@postToWorker
                    if (!isSettingsLikePackage(pkg)) return@postToWorker
                    val cls = root.className?.toString() ?: ""
                    val isAppInfoClass = cls.contains("InstalledAppDetails") || cls.contains("AppInfoDashboard") || cls.contains("AppDetails") || cls.contains("SubSettings")
                    if (hasHeaderMatch(root) && (hasActionButton(root) || isAppInfoClass)) {
                        debugToast("BLK recheck hit")
                        triggerOverlay(recoveryService, "Deferred Settings re-check")
                    }
                } catch (e: Exception) { Log.w(TAG, "recheck error: ${e.message}") }
            }
        }
    }

    private fun updateLauncherCache() {
        try {
            val set = mutableSetOf<String>()
            val pm = service.packageManager
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val defaultHome = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            defaultHome?.activityInfo?.packageName?.lowercase()?.let { set.add(it) }

            val allHomes = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
            for (ri in allHomes) {
                ri.activityInfo?.packageName?.lowercase()?.let { set.add(it) }
            }

            // Exclude Settings, Installer, Play Store, and SystemUI packages
            val excludedPackages = setOf(
                "com.android.settings",
                "com.google.android.settings",
                "com.samsung.android.settings",
                "com.miui.securitycenter",
                "com.android.vending",
                "com.android.systemui"
            )
            set.removeAll { pkg ->
                excludedPackages.contains(pkg) || isSettings(pkg) || isInstaller(pkg) || isPlayStore(pkg)
            }

            cachedLauncherPackages = set
            settingsLikeCache.clear()
            lastLauncherCacheUpdateTime = SystemClock.uptimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating launcher cache: ${e.message}")
        }
    }

    fun isLauncher(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()

        // Exclude Settings, Installer, Play Store, and SystemUI first
        if (isSettings(lower) || isInstaller(lower) || isPlayStore(lower) || lower.contains("systemui")) {
            return false
        }

        if (POPULAR_LAUNCHERS.contains(lower)) return true

        val now = SystemClock.uptimeMillis()
        if (cachedLauncherPackages.isEmpty() || (now - lastLauncherCacheUpdateTime) > 30000L) {
            updateLauncherCache()
        }
        if (cachedLauncherPackages.contains(lower)) return true

        return lower.endsWith(".launcher") || lower.contains(".launcher.")
    }

    fun isSettings(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        if (SETTINGS_PACKAGES.contains(lower)) return true
        return (lower.startsWith("com.android.settings") ||
                lower.startsWith("com.google.android.settings") ||
                lower.contains(".settings") ||
                lower.contains("securitycenter") ||
                lower.contains("systemmanager")) &&
                !lower.contains("systemui") && !lower.contains("keyboard")
    }

    fun isInstaller(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        return INSTALLER_PACKAGES.contains(lower) ||
                lower.contains("packageinstaller") ||
                lower.contains("permissioncontroller")
    }

    fun isPlayStore(pkg: String): Boolean {
        return pkg.equals(PLAY_STORE_PACKAGE, ignoreCase = true)
    }

    fun isSettingsLikePackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        val now = SystemClock.uptimeMillis()
        if (now - settingsLikeCacheClearedAt > 60_000L) {
            settingsLikeCache.clear()
            settingsLikeCacheClearedAt = now
        }
        return settingsLikeCache.getOrPut(lower) {
            if (isSettings(lower) || isInstaller(lower)) true
            else if (lower.contains("systemui") || lower.contains("inputmethod") || lower.contains("keyboard") ||
                lower.contains("launcher") || lower == service.packageName.lowercase()) false
            else {
                val ai = try { service.packageManager.getApplicationInfo(lower, 0) } catch (_: Exception) { null }
                if (ai == null) false
                else {
                    val isSystemApp = (ai.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                    val hints = listOf("settings", "manager", "security", "safe", "center", "permission", "installer", "setup", "miui", "coloros", "oplus", "oppo", "vivo", "huawei", "hihonor", "samsung", "transsion", "tecno", "infinix", "itel")
                    isSystemApp && hints.any { lower.contains(it) }
                }
            }
        }
    }

    /**
     * STRICT PACKAGE ALLOWLIST
     * Returns true ONLY if package belongs to Settings, Installer, Play Store, or Launcher.
     * If false, callers MUST return false IMMEDIATELY to prevent false positives in all other apps.
     */
    fun isAllowlistedPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        // Never allow SystemUI or keyboard
        if (lower.contains("systemui") || lower.contains("inputmethod") || lower.contains("keyboard")) {
            return false
        }
        return isSettings(lower) || isInstaller(lower) || isPlayStore(lower) || isLauncher(lower) || isSettingsLikePackage(lower)
    }

    fun isBlockifyIdentifier(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        val lower = str.trim().lowercase()
        val appName = try { service.getString(R.string.app_name).lowercase() } catch (_: Exception) { "blockify" }
        val serviceName = try { service.getString(R.string.accessibility_service_label).lowercase() } catch (_: Exception) { "blockify protection" }
        val ownPkg = service.packageName.lowercase()

        return lower == "blockify" ||
                lower.contains("blockify") ||
                lower == appName ||
                lower == serviceName ||
                lower == ownPkg ||
                lower.contains("com.blockifybyroman.app") ||
                lower.contains("com.blockifyroman.app") ||
                lower.contains("com.example.recoveryshield")
    }

    /**
     * Evaluates the accessibility event against self-protection scenarios.
     * Must be invoked from onAccessibilityEvent.
     *
     * @return true if self-protection was triggered and handled, false otherwise.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, recoveryService: RecoveryAccessibilityService): Boolean {
        val eventPkg = (event.packageName?.toString() ?: "").lowercase()
        if (eventPkg.isBlank() || eventPkg == service.packageName.lowercase()) return false

        val settingsLikePkg = isSettingsLikePackage(eventPkg)
        val installerPkg = isInstaller(eventPkg)
        val playStorePkg = isPlayStore(eventPkg)
        val launcherPkg = isLauncher(eventPkg)
        val allowlisted = isSettings(eventPkg) || installerPkg || playStorePkg || launcherPkg || settingsLikePkg

        if (DEBUG_SELF_PROTECT_TOASTS && (settingsLikePkg || installerPkg || playStorePkg)) {
            val shortEv = when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE"
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT"
                AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONG"
                else -> "OTHER"
            }
            debugToast("BLK ev=$shortEv pkg=${eventPkg.takeLast(26)}")
        }

        if (VERBOSE_PIPELINE_LOGS) {
            val eventTypeName = AccessibilityEvent.eventTypeToString(event.eventType)
            Log.d(
                "BLOCKIFY_SELF_PROTECT",
                "eventPkg=$eventPkg, eventType=$eventTypeName, isAllowlisted=$allowlisted, settingsLikePkg=$settingsLikePkg, isInstaller=$installerPkg, isPlayStore=$playStorePkg, isLauncher=$launcherPkg"
            )
        }

        // 1. STRICT PACKAGE ALLOWLIST:
        // IF event.packageName is NOT in this allowlist, return false IMMEDIATELY.
        // Guarantees zero false positives in chat apps, browsers, notes, etc.
        if (!allowlisted) {
            return false
        }

        // 2. Authoritative duplicate check: If overlay is already showing, do not restart timer
        if (RecoveryAccessibilityService.isOverlayShowing()) {
            return true
        }

        // 3. Emergency open session check
        if (EmergencyOpenSession.isAppInfoAuthorized()) {
            return false
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastTriggerTime < 200L) {
            return true
        }

        val rootNode = try { service.rootInActiveWindow ?: event.source } catch (_: Exception) { null }
        val sourceNode = try { event.source } catch (_: Exception) { null }

        // E) Google Play Store check:
        if (playStorePkg) {
            if (checkPlayStoreSituation(event, rootNode)) {
                return triggerOverlay(recoveryService, "Google Play Store uninstall guarded")
            }
            return false
        }

        // B) Installer / Permission Controller check (Uninstall dialog):
        if (installerPkg && !isSettings(eventPkg)) {
            if (checkUninstallDialogSituation(event, rootNode, sourceNode)) {
                return triggerOverlay(recoveryService, "Uninstall confirmation dialog guarded")
            }
        }

        // A & D) Settings checks (App Info, Uninstall, Accessibility deactivation, Device Admin deactivation):
        if (settingsLikePkg) {
            if (checkSettingsSituations(event, rootNode, sourceNode)) {
                return triggerOverlay(recoveryService, "Settings modification of Blockify guarded")
            }
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            ) {
                scheduleRechecks(recoveryService)
            }
            return false
        }

        // C) Launcher check (evaluated LAST to prevent Settings FallbackHome from being swallowed):
        if (launcherPkg) {
            if (checkLauncherSituation(event, rootNode, sourceNode, now)) {
                return triggerOverlay(recoveryService, "Launcher interaction on Blockify guarded")
            }
            return false
        }

        return false
    }

    private fun checkLauncherSituation(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        sourceNode: AccessibilityNodeInfo?,
        now: Long
    ): Boolean {
        val eventType = event.eventType
        val sourceText = sourceNode?.text?.toString() ?: ""
        val sourceDesc = sourceNode?.contentDescription?.toString() ?: ""
        val eventTexts = event.text?.mapNotNull { it?.toString() } ?: emptyList()
        val allEventText = (eventTexts + listOf(sourceText, sourceDesc)).joinToString(" ").lowercase()

        // Situation C1: User long-pressed the Blockify icon on Launcher
        if (eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            if (isBlockifyIdentifier(sourceText) || isBlockifyIdentifier(sourceDesc) ||
                eventTexts.any { isBlockifyIdentifier(it) }
            ) {
                lastLauncherBlockifyLongPressTime = now
                Log.i(TAG, "Blockify launcher icon long-clicked! Triggering cooldown overlay.")
                return true
            }
        }

        // Situation C2: Long-press shortcut menu (App info / Uninstall / Pause) appears on launcher
        val hasAppInfo = allEventText.contains("app info") || allEventText.contains("app details")
        val hasUninstall = allEventText.contains("uninstall") || allEventText.contains("delete") || allEventText.contains("remove")
        val hasPause = allEventText.contains("pause app") || allEventText.contains("pause")

        val isShortcutMenu = hasAppInfo || hasUninstall || hasPause

        if (isShortcutMenu) {
            val mentionsBlockify = allEventText.contains("blockify") ||
                    (rootNode != null && nodeContainsBlockify(rootNode))

            val recentlyLongPressed = (now - lastLauncherBlockifyLongPressTime) <= 5000L

            if (mentionsBlockify || recentlyLongPressed) {
                lastLauncherBlockifyLongPressTime = 0L
                Log.i(TAG, "Blockify launcher shortcut popup detected! Triggering cooldown overlay.")
                return true
            }
        }

        // Check if rootNode in launcher has popup mentioning Blockify and App info / Uninstall
        if (rootNode != null && (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) {
            val hasBlockifyInTree = nodeContainsBlockify(rootNode)
            if (hasBlockifyInTree) {
                val hasShortcutAction = nodeHasAnyText(rootNode, listOf("app info", "app details", "uninstall", "pause app"))
                if (hasShortcutAction) {
                    lastLauncherBlockifyLongPressTime = 0L
                    Log.i(TAG, "Blockify popup window found in launcher tree! Triggering.")
                    return true
                }
            }
        }

        return false
    }

    private fun checkPlayStoreSituation(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        if (rootNode == null) return false
        val hasBlockify = nodeContainsBlockify(rootNode)
        if (!hasBlockify) return false

        // Check if "Uninstall" button is present in Play Store
        val hasUninstallBtn = nodeHasAnyText(rootNode, listOf("uninstall"))
        if (hasUninstallBtn) {
            Log.i(TAG, "Google Play Store page for Blockify with Uninstall button detected! Triggering.")
            return true
        }
        return false
    }

    private fun checkUninstallDialogSituation(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        sourceNode: AccessibilityNodeInfo?
    ): Boolean {
        val eventTexts = event.text?.mapNotNull { it?.toString() } ?: emptyList()
        val sourceText = sourceNode?.text?.toString() ?: ""
        val sourceDesc = sourceNode?.contentDescription?.toString() ?: ""
        val textCombined = (eventTexts + listOf(sourceText, sourceDesc)).joinToString(" ").lowercase()

        val mentionsBlockify = isBlockifyIdentifier(textCombined) ||
                (rootNode != null && nodeContainsBlockify(rootNode))

        if (!mentionsBlockify) return false

        val hasUninstallDialogText = textCombined.contains("uninstall") ||
                textCombined.contains("do you want to uninstall") ||
                (rootNode != null && nodeHasAnyText(rootNode, listOf("do you want to uninstall this app?", "do you want to uninstall", "uninstall")))

        if (hasUninstallDialogText) {
            Log.i(TAG, "Uninstall dialog for Blockify detected! Triggering.")
            return true
        }
        return false
    }

    private fun checkSettingsSituations(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        sourceNode: AccessibilityNodeInfo?
    ): Boolean {
        val eventType = event.eventType
        val eventTexts = event.text?.mapNotNull { it?.toString() } ?: emptyList()
        val sourceText = sourceNode?.text?.toString() ?: ""
        val sourceDesc = sourceNode?.contentDescription?.toString() ?: ""
        val textCombined = (eventTexts + listOf(sourceText, sourceDesc)).joinToString(" ").lowercase()

        val className = (event.className?.toString() ?: "") + " " + (rootNode?.className?.toString() ?: "")

        // 1. User taps Blockify in Settings Apps list:
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            if (PermissionOrchestrator.isCurrentlyInPermissionFlow() && (className.contains("Accessibility", true) || className.contains("DeviceAdmin", true))) return false

            val sourceParent = try { sourceNode?.parent } catch (_: Exception) { null }
            val directMatch = isBlockifyIdentifier(sourceText) ||
                    isBlockifyIdentifier(sourceDesc) ||
                    eventTexts.any { isBlockifyIdentifier(it) }

            if (directMatch || subtreeHasBlockify(sourceNode) || subtreeHasBlockify(sourceParent)) {
                Log.d("BLOCKIFY_SELF_PROTECT", "click on Blockify row detected")
                Log.i(TAG, "User clicked Blockify item in Settings! Triggering overlay.")
                val hdr = if (rootNode != null) hasHeaderMatch(rootNode) else false
                val btn = if (rootNode != null) hasActionButton(rootNode) else false
                debugToast("BLK dec root=" + (rootNode != null) + " hdr=" + hdr + " btn=" + btn + " cls=" + className.takeLast(28) + " final=true")
                return true
            }
        }

        val isAppInfoClass = className.contains("InstalledAppDetails") || className.contains("AppInfoDashboard") || className.contains("AppDetails") || className.contains("SubSettings")
        var hasHeaderMatch = false
        var hasActionButton = false

        if (rootNode != null) {
            hasHeaderMatch = hasHeaderMatch(rootNode)
            hasActionButton = hasActionButton(rootNode)
        }

        // Check if root or source contains Blockify
        val mentionsBlockify = hasHeaderMatch ||
                isBlockifyIdentifier(textCombined) ||
                (rootNode != null && nodeContainsBlockify(rootNode))

        if (!mentionsBlockify) {
            Log.d("BLOCKIFY_SELF_PROTECT", "mentionsBlockify=false, hasHeaderMatch=false, hasActionButton=false, isListScreen=false, finalDecision=false")
            return false
        }

        // Check for other app names (e.g. if user is in WhatsApp App Info, do not trigger):
        if (rootNode != null && isExplicitOtherAppScreen(rootNode)) {
            Log.d("BLOCKIFY_SELF_PROTECT", "mentionsBlockify=$mentionsBlockify, hasHeaderMatch=false, hasActionButton=false, isListScreen=false, finalDecision=false (OTHER_APP_SCREEN)")
            return false
        }

        // D) Device Admin deactivation screen:
        val isDeviceAdmin = className.contains("DeviceAdmin") || textCombined.contains("device admin")
        if (isDeviceAdmin) {
            val isDeactivating = textCombined.contains("deactivate") ||
                    textCombined.contains("remove") ||
                    (rootNode != null && nodeHasAnyText(rootNode, listOf("deactivate this device admin app", "deactivate", "remove device admin")))
            val isActivating = textCombined.contains("activate this device admin app") || textCombined.contains("activate")
            if (isDeactivating && !isActivating) {
                Log.d("BLOCKIFY_SELF_PROTECT", "mentionsBlockify=true, hasHeaderMatch=true, hasActionButton=true, isListScreen=false, finalDecision=true (DEVICE_ADMIN)")
                Log.i(TAG, "Device Admin deactivation screen for Blockify detected! Triggering.")
                return true
            }
        }

        // D) Accessibility service settings screen:
        val isAccessibilityScreen = className.contains("Accessibility") || textCombined.contains("accessibility")
        if (isAccessibilityScreen) {
            val isTurningOff = textCombined.contains("stop") || textCombined.contains("turn off") ||
                    textCombined.contains("deactivate") || textCombined.contains("disable") ||
                    (rootNode != null && nodeHasAnyText(rootNode, listOf("stop", "turn off", "deactivate")))
            // If user is currently in the initial permission setup flow, do not block enabling:
            if (isTurningOff && !PermissionOrchestrator.isCurrentlyInPermissionFlow()) {
                Log.d("BLOCKIFY_SELF_PROTECT", "mentionsBlockify=true, hasHeaderMatch=true, hasActionButton=true, isListScreen=false, finalDecision=true (ACCESSIBILITY_DEACTIVATE)")
                Log.i(TAG, "Accessibility service deactivation for Blockify detected! Triggering.")
                return true
            }
        }

        // A) Blockify App Info page in Settings:
        val finalDecision = hasHeaderMatch && (hasActionButton || isAppInfoClass)

        debugToast("BLK dec root=" + (rootNode != null) + " hdr=" + hasHeaderMatch + " btn=" + hasActionButton + " cls=" + className.takeLast(28) + " final=" + finalDecision)

        Log.d(
            "BLOCKIFY_SELF_PROTECT",
            "hasHeaderMatch=$hasHeaderMatch, hasActionButton=$hasActionButton, isAppInfoClass=$isAppInfoClass, finalDecision=$finalDecision"
        )

        if (finalDecision) {
            Log.i(TAG, "Blockify App Info screen in Settings confirmed! Triggering.")
            return true
        }

        return false
    }

    private fun subtreeHasBlockify(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var found = false
        BoundedNodeTraverser.traverse(node, maxNodes = 25, maxDepth = 4, maxChildrenPerNode = 10) { curr, _ ->
            val t = curr.text?.toString()?.trim() ?: ""
            val d = curr.contentDescription?.toString()?.trim() ?: ""
            if (isBlockifyExactMatch(t) || isBlockifyExactMatch(d)) { found = true; true } else false
        }
        return found
    }

    private fun hasHeaderMatch(root: AccessibilityNodeInfo): Boolean {
        var found = false
        val screenH = try { service.resources.displayMetrics.heightPixels } catch (_: Exception) { 2400 }
        BoundedNodeTraverser.traverse(root, maxNodes = 150, maxDepth = 10, maxChildrenPerNode = 30) { curr, _ ->
            val text = curr.text?.toString()?.trim() ?: ""
            val desc = curr.contentDescription?.toString()?.trim() ?: ""
            if (isBlockifyExactMatch(text) || isBlockifyExactMatch(desc) || isBlockifyIdentifier(text) || isBlockifyIdentifier(desc)) {
                val rawId = try { curr.viewIdResourceName?.lowercase() ?: "" } catch (_: Exception) { "" }
                val isHeaderId = rawId.contains("title") || rawId.contains("entity_header") || rawId.contains("app_snippet") || rawId.contains("app_name")
                val inUpperPart = try {
                    val rect = android.graphics.Rect()
                    curr.getBoundsInScreen(rect)
                    rect.top in 0..(screenH * 65 / 100)
                } catch (_: Exception) { true }
                if (isHeaderId || inUpperPart) { found = true; true } else false
            } else false
        }
        return found
    }

    private fun isBlockifyExactMatch(str: String): Boolean {
        if (str.isBlank()) return false
        val lower = str.lowercase().trim()
        val appName = try { service.getString(R.string.app_name).lowercase().trim() } catch (_: Exception) { "blockify" }
        return lower == "blockify" || lower == appName ||
                lower.startsWith("blockify") ||
                lower == "com.blockifybyroman.app" || lower == "com.blockifyroman.app" || lower == "com.example.recoveryshield"
    }

    private fun hasActionButton(root: AccessibilityNodeInfo): Boolean {
        val keywords = setOf("uninstall", "force stop", "disable", "clear storage", "clear data", "storage", "storage & cache", "app info", "app details", "permissions", "open by default", "remove", "delete", "stop")
        var found = false
        BoundedNodeTraverser.traverse(root, maxNodes = 150, maxDepth = 10, maxChildrenPerNode = 30) { curr, _ ->
            val text = curr.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = curr.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            if (keywords.contains(text) || keywords.contains(desc)) {
                var clickable = curr.isClickable
                var p: AccessibilityNodeInfo? = try { curr.parent } catch (_: Exception) { null }
                var hops = 0
                while (!clickable && p != null && hops < 3) {
                    clickable = p.isClickable
                    p = try { p.parent } catch (_: Exception) { null }
                    hops++
                }
                if (clickable) { found = true; true } else false
            } else false
        }
        return found
    }

    private fun nodeContainsBlockify(node: AccessibilityNodeInfo): Boolean {
        var found = false
        BoundedNodeTraverser.traverse(node, maxNodes = 150, maxDepth = 10) { curr, _ ->
            val text = curr.text?.toString() ?: ""
            val desc = curr.contentDescription?.toString() ?: ""
            if (isBlockifyIdentifier(text) || isBlockifyIdentifier(desc)) {
                found = true
                true // stop traversal
            } else {
                false
            }
        }
        return found
    }

    private fun nodeHasAnyText(node: AccessibilityNodeInfo, targets: List<String>): Boolean {
        var found = false
        val lowerTargets = targets.map { it.lowercase() }
        BoundedNodeTraverser.traverse(node, maxNodes = 60, maxDepth = 6) { curr, _ ->
            val text = curr.text?.toString()?.lowercase() ?: ""
            val desc = curr.contentDescription?.toString()?.lowercase() ?: ""
            if (lowerTargets.any { text.contains(it) || desc.contains(it) }) {
                found = true
                true
            } else {
                false
            }
        }
        return found
    }

    private fun isExplicitOtherAppScreen(node: AccessibilityNodeInfo): Boolean {
        var isOther = false
        BoundedNodeTraverser.traverse(node, maxNodes = 40, maxDepth = 5) { curr, _ ->
            val text = curr.text?.toString()?.trim()?.lowercase() ?: ""
            val viewId = try { curr.viewIdResourceName?.lowercase() ?: "" } catch (_: Exception) { "" }
            if (viewId.contains("title") || viewId.contains("entity_header")) {
                if (OTHER_POPULAR_APPS.contains(text) && !text.contains("blockify")) {
                    isOther = true
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        return isOther
    }

    private fun triggerOverlay(recoveryService: RecoveryAccessibilityService, reason: String): Boolean {
        lastTriggerTime = SystemClock.uptimeMillis()
        try {
            recheckPendingUntil = 0L
        } catch (_: Exception) {}
        debugToast("TRIGGER: $reason")
        return recoveryService.showSelfProtectionOverlay(reason)
    }
}
