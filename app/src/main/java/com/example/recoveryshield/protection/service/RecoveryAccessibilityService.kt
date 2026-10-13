package com.example.recoveryshield.protection.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.R
import com.example.recoveryshield.domain.model.DetectionDecision
import com.example.recoveryshield.domain.model.SafeEventCode
import com.example.recoveryshield.protection.detector.BlockifyAccessibilitySettingsDetector
import com.example.recoveryshield.protection.detector.BlockifyAppInfoDetector
import com.example.recoveryshield.protection.detector.BoundedNodeTraverser
import com.example.recoveryshield.protection.detector.DetectionRuleScope
import com.example.recoveryshield.protection.detector.EventNormalizer
import com.example.recoveryshield.protection.detector.InstantKeywordMatcher
import com.example.recoveryshield.protection.detector.ScopedShortLivedContextMemory
import com.example.recoveryshield.protection.detector.SettingsContextResult
import com.example.recoveryshield.protection.detector.SettingsContextType
import com.example.recoveryshield.protection.detector.TriggerDecision
import com.example.recoveryshield.protection.engine.ProtectionEngine
import com.example.recoveryshield.protection.intervention.EmergencyOpenActivity
import com.example.recoveryshield.protection.intervention.EmergencyOpenSession
import com.example.recoveryshield.protection.intervention.SystemInterventionOverlay
import com.example.recoveryshield.protection.util.BlockifyPerfTracker
import java.lang.ref.WeakReference

/**
 * Voluntary AccessibilityService dedicated exclusively to detecting explicit sexual content
 * signals on the user's screen and executing permitted navigation away from triggering content.
 *
 * Privacy & Security Guarantees:
 * 1. Strictly local processing. Zero remote network connections.
 * 2. Event filtering limited to window state, text change, and window content changes.
 * 3. Does not collect passwords or read private chat messages.
 * 4. Never attempts to intercept Android system security dialogs or prevent app uninstallation.
 */
class RecoveryAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RecoveryAccessService"
        private const val THROTTLE_INTERVAL_MS = 50L
        const val USE_BACKGROUND_EVENT_WORKER = false
        const val VERBOSE_PIPELINE_LOGS = false

        private var instanceRef: WeakReference<RecoveryAccessibilityService>? = null

        fun isServiceRunning(): Boolean = instanceRef?.get() != null

        fun isOverlayShowing(): Boolean = instanceRef?.get()?.systemOverlay?.isShowing() == true

        /**
         * Displays the floating transparent intervention countdown timer directly on top
         * of whatever screen or website the user is on (e.g. YouTube, Chrome), WITHOUT
         * redirecting to or opening the Blockify app.
         */
        fun showSystemIntervention(
            isAntiTamper: Boolean = false,
            message: String? = null,
            onDismissed: (() -> Unit)? = null
        ): Boolean {
            val service = instanceRef?.get() ?: return false
            if (!service.isProtectionActive()) {
                android.util.Log.d("BLOCKIFY_TRIGGER", "RecoveryAccessibilityService: showSystemIntervention skipped because protection is not active")
                return false
            }
            android.util.Log.i("BLOCKIFY_TRIGGER", "RecoveryAccessibilityService: showSystemIntervention requested (msg=$message)")
            val overlay = service.systemOverlay ?: return false
            return overlay.show(
                isAntiTamper = isAntiTamper,
                customMessage = message,
                customTitle = if (isAntiTamper) "Protection Active" else "Shield Active",
                durationMillis = 3000L,
                navigateAwayOnFinish = true,
                onNavigateBack = {
                    service.postInterventionCooldownUntil = 0L
                    service.lastBlockifyLongPressTime = 0L
                    service.targetIsBlockify = false
                    performCompliantNavigationAway()
                    onDismissed?.invoke()
                }
            )
        }

        fun dismissSystemIntervention() {
            val service = instanceRef?.get() ?: return
            android.util.Log.i("BLOCKIFY_TRIGGER", "RecoveryAccessibilityService: dismissSystemIntervention called")
            service.postInterventionCooldownUntil = 0L
            service.lastBlockifyLongPressTime = 0L
            service.targetIsBlockify = false
            service.systemOverlay?.dismiss()
        }

        /**
         * Safely navigates away from triggering content and returns to the device Home screen.
         */
        fun performCompliantNavigationAway(): Boolean {
            val service = instanceRef?.get() ?: return false
            android.util.Log.i("BLOCKIFY_TRIGGER", "RecoveryAccessibilityService: Performing compliant navigation away (Back / Home)")
            // 1. Perform BACK action to exit current player/dialog
            service.performGlobalAction(GLOBAL_ACTION_BACK)

            // 2. Send user directly to device Home screen away from the triggered app
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun launchEmergencyOpenOverlay(): Boolean {
            return false
        }
    }

    fun launchEmergencyOpenOverlay() {
        // Disabled completely: standard intervention is used instead of 8-digit code screen
    }

    private val selfProtectionDetector by lazy {
        com.example.recoveryshield.protection.detector.SelfProtectionDetector(this)
    }

    fun getOrCreateOverlay(): SystemInterventionOverlay {
        return systemOverlay ?: SystemInterventionOverlay(this).also { systemOverlay = it }
    }

    fun showSelfProtectionOverlay(reason: String): Boolean {
        val overlay = getOrCreateOverlay()
        android.util.Log.i("BLOCKIFY_TRIGGER", "SelfProtectionDetector triggered overlay: $reason")
        return overlay.show(
            isAntiTamper = true,
            customTitle = "Protection Active",
            customMessage = "Uninstall attempt guarded by Blockify",
            durationMillis = 5000L,
            navigateAwayOnFinish = true,
            onNavigateBack = {
                performCompliantNavigationAway()
            }
        )
    }

    private var systemOverlay: SystemInterventionOverlay? = null
    private var lastProcessedTimestamp: Long = 0L
    private var lastPackageName: String = ""
    @Volatile
    private var lastBlockifyLongPressTime: Long = 0L
    @Volatile
    private var targetIsBlockify: Boolean = false
    @Volatile
    private var postInterventionCooldownUntil: Long = 0L

    @Volatile private var foregroundRecheckUntil = 0L

    private fun scheduleForegroundRecheck() {
        val now = SystemClock.uptimeMillis()
        if (now < foregroundRecheckUntil) return
        foregroundRecheckUntil = now + 3600L
        for (d in longArrayOf(120L, 400L, 900L, 1800L, 3500L)) postToWorker(d) { evaluateForegroundWindow() }
    }

    private fun evaluateForegroundWindow() {
        try {
            if (!isProtectionActive() || isOverlayShowing()) return
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: return
            if (pkg.equals(packageName, ignoreCase = true)) return
            @Suppress("DEPRECATION")
            val synthetic = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            try {
                synthetic.packageName = pkg
                synthetic.className = root.className ?: ""
                synthetic.eventTime = SystemClock.uptimeMillis()
                selfProtectionDetector.onAccessibilityEvent(synthetic, this)
            } finally {
                @Suppress("DEPRECATION") try { synthetic.recycle() } catch (_: Exception) {}
            }
        } catch (t: Throwable) { Log.w(TAG, "foreground recheck error: ${t.message}") }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val eventWorkerThread = android.os.HandlerThread("BlockifyEventWorker", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    private val eventWorkerHandler = android.os.Handler(eventWorkerThread.looper)
    private val contentEventPending = java.util.concurrent.atomic.AtomicBoolean(false)

    fun postToWorker(delayMs: Long = 0L, block: () -> Unit) {
        eventWorkerHandler.postDelayed({
            try {
                block()
            } catch (t: Throwable) {
                Log.e(TAG, "worker error: ${t.message}")
            }
        }, delayMs)
    }

    private val relevanceCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    @Volatile private var relevanceCacheClearedAt = 0L

    private fun isPackageRelevantFast(pkg: String): Boolean {
        val lower = pkg.lowercase()
        val now = SystemClock.uptimeMillis()
        if (now - relevanceCacheClearedAt > 60_000L) {
            relevanceCache.clear()
            relevanceCacheClearedAt = now
        }
        // dynamic: user block rules (cheap list scan, no IPC)
        val hasRule = try {
            com.example.recoveryshield.protection.block.BlockRulesManager.getInstance(applicationContext).getRules().any {
                val rulePkg = it.appPackage.lowercase()
                (rulePkg == lower || (rulePkg == "com.whatsapp" && lower == "com.whatsapp.w4b") || (rulePkg == "com.whatsapp.w4b" && lower == "com.whatsapp")) && it.active && !it.paused
            }
        } catch (_: Exception) { true }
        if (hasRule) return true
        return relevanceCache.getOrPut(lower) {
            isSettingsOrManagementPackage(lower) || selfProtectionDetector.isAllowlistedPackage(lower) ||
            isBrowserOrSearchApp(lower) || isLauncherPackage(lower)
        }
    }
    @Volatile
    private var cachedLauncherPackages: Set<String> = emptySet()
    @Volatile
    private var lastLauncherCacheUpdateTime: Long = 0L

    private fun updateCachedLauncherPackages() {
        try {
            val set = mutableSetOf<String>()
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val currentLauncher = resolveInfo?.activityInfo?.packageName?.lowercase()
            if (!currentLauncher.isNullOrBlank()) {
                set.add(currentLauncher)
            }
            val allHomeActivities = packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
            for (ri in allHomeActivities) {
                val p = ri.activityInfo?.packageName?.lowercase()
                if (!p.isNullOrBlank()) {
                    set.add(p)
                }
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
                excludedPackages.contains(pkg) || isSettingsOrManagementPackage(pkg) || pkg.equals("com.android.vending", ignoreCase = true)
            }

            cachedLauncherPackages = set
            lastLauncherCacheUpdateTime = SystemClock.uptimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving launcher packages: ${e.message}")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        try { selfProtectionDetector.debugToast("BLK v0.0.0.9 service connected") } catch (_: Exception) {}

        // Clear stale runtime state cleanly on startup / post-reboot with zero blind window
        lastBlockifyLongPressTime = 0L
        targetIsBlockify = false
        lastProcessedTimestamp = 0L
        postInterventionCooldownUntil = 0L

        // Cache launcher packages
        updateCachedLauncherPackages()

        // 1. Initialize overlay and schedule prewarm safely without blocking service connection
        systemOverlay = SystemInterventionOverlay(this).apply {
            reset()
        }
        mainHandler.post {
            systemOverlay?.prewarm()
        }

        // 2. Configure service with low-latency framework notification timeout (25ms)
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.eventTypes = info.eventTypes or
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            info.notificationTimeout = 50L
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring service info: ${e.message}")
        }

        Log.i("BLOCKIFY_TRIGGER", "RecoveryAccessibilityService connected, instance reference established, views pre-warmed.")
        com.example.recoveryshield.protection.util.BlockifyLatencyTracker.onServiceConnected()

        // Pre-warm keyword matchers and block rules in memory
        postToWorker { com.example.recoveryshield.protection.detector.InstantKeywordMatcher.findInstantTrigger("warmup_test") }
        postToWorker { com.example.recoveryshield.protection.block.BlockRulesManager.getInstance(applicationContext).getRules() }

        val engine = ProtectionEngine.getInstance(applicationContext)
        engine.refreshStateWithPermissions()
        
        // Proper state reconciliation (Accessibility connected + actual Device Admin status + user setup completion):
        val actualAdminActive = engine.permissionOrchestrator.deviceManagementManager.isDeviceAdminActive()
        val userSettingEnabled = engine.settingsRepository.getSettings().antiUninstallProtectionEnabled
        val prefs = getSharedPreferences("safemind_prefs", android.content.Context.MODE_PRIVATE)
        val hasCompletedSetup = prefs.getBoolean("has_completed_setup", false)
        val plan = com.example.recoveryshield.domain.model.ProtectionPlanInfo.getCachedPlanSync(applicationContext)
        val shouldEnableAntiUninstall = actualAdminActive && userSettingEnabled && hasCompletedSetup && !plan.isExpired &&
                (engine.stateMachine.currentState == com.example.recoveryshield.domain.model.ProtectionState.ACTIVE ||
                 engine.stateMachine.currentState == com.example.recoveryshield.domain.model.ProtectionState.COOLDOWN_ACTIVE)
        
        engine.setAntiUninstallProtectionEnabled(shouldEnableAntiUninstall)
        engine.onServiceConnected()
        try { com.example.recoveryshield.notifications.ProtectionNotificationHelper.ensureRunningIfActive(applicationContext) } catch (_: Throwable) {}
    }

    /**
     * Authoritative check mapping every active monitoring state in the ProtectionState lifecycle.
     */
    private fun isProtectionRuntimeEnabled(): Boolean {
        return ProtectionEngine.getInstance(applicationContext).isProtectionRuntimeEnabled()
    }

    @Volatile private var overlayPermCheckedAt = 0L
    @Volatile private var overlayPermGranted = false
    private fun canDrawOverlaysCached(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - overlayPermCheckedAt > 2000L) {
            overlayPermGranted = try { android.provider.Settings.canDrawOverlays(applicationContext) } catch (_: Exception) { true }
            overlayPermCheckedAt = now
        }
        return overlayPermGranted
    }

    private fun isProtectionActive(): Boolean {
        return try {
            if (com.example.recoveryshield.permissions.PermissionOrchestrator.isCurrentlyInPermissionFlow()) {
                selfProtectionDetector.debugToast("BLK: In permission flow")
                Log.e(TAG, "isProtectionActive: In permission flow, returning false")
                return false
            }

            val plan = com.example.recoveryshield.domain.model.ProtectionPlanInfo.getCachedPlanSync(applicationContext)
            if (plan.isExpired) {
                selfProtectionDetector.debugToast("BLK: Plan expired")
                Log.e(TAG, "isProtectionActive: Plan expired, returning false")
                return false
            }

            if (!plan.isProtectionDayActive) {
                selfProtectionDetector.debugToast("BLK: Not a protection day")
                Log.e(TAG, "isProtectionActive: Not a protection day, returning false")
                return false
            }
            val gatePrefs = getSharedPreferences("safemind_prefs", android.content.Context.MODE_PRIVATE)
            if (!gatePrefs.getBoolean("has_completed_setup", false)) {
                selfProtectionDetector.debugToast("BLK: Setup incomplete")
                Log.e(TAG, "isProtectionActive: Setup not completed, returning false")
                return false
            }
            if (!canDrawOverlaysCached()) {
                selfProtectionDetector.debugToast("BLK: Overlays disabled")
                Log.e(TAG, "isProtectionActive: Cannot draw overlays, returning false")
                return false
            }
            val engine = ProtectionEngine.getInstance(applicationContext)
            val currentState = engine.stateMachine.currentState
            val isActive = when (currentState) {
                com.example.recoveryshield.domain.model.ProtectionState.ACTIVE,
                com.example.recoveryshield.domain.model.ProtectionState.INTERVENTION,
                com.example.recoveryshield.domain.model.ProtectionState.COOLDOWN_REQUESTED,
                com.example.recoveryshield.domain.model.ProtectionState.COOLDOWN_ACTIVE,
                com.example.recoveryshield.domain.model.ProtectionState.DISABLE_CONFIRMATION -> true
                else -> false
            }
            if (!isActive) {
                selfProtectionDetector.debugToast("BLK: State inactive: $currentState")
                Log.e(TAG, "isProtectionActive: State machine not in active state: $currentState")
            }
            isActive
        } catch (e: Exception) {
            selfProtectionDetector.debugToast("BLK: Error in check")
            Log.e(TAG, "isProtectionActive: Error, returning false", e)
            false
        }
    }

    enum class ManagementContext {
        NONE,
        BLOCKIFY_APP_INFO,
        BLOCKIFY_ACCESSIBILITY_SETTINGS,
        OTHER_APP_INFO,
        GENERIC_SETTINGS
    }

    @Volatile
    var currentManagementContext: ManagementContext = ManagementContext.NONE
        private set

    /**
     * Checks if a package belongs to Android System Settings, Security Center,
     * or Package Installer, explicitly excluding Blockify's own application
     * and SystemUI (Notification shade, Quick Settings, Status bar).
     */
    private fun isSettingsOrManagementPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lower = pkg.lowercase()
        val ownPkg = packageName.lowercase()
        if (lower == ownPkg || lower == "com.blockifyroman.app" || lower == "com.example.recoveryshield" || lower == "com.example") {
            return false
        }
        // Explicitly exempt SystemUI / Notification Shade / Status Bar / Quick Settings / Keyboards
        if (lower.contains("systemui") || lower.contains("notification") || lower.contains("statusbar") ||
            lower.contains("quicksettings") || lower.contains("recents") || lower.contains("overview") ||
            lower.contains("inputmethod") || lower.contains("keyboard") || lower.contains("system_ui")
        ) {
            return false
        }
        return lower == "com.android.settings" ||
                lower == "com.google.android.settings" ||
                lower.contains(".settings") ||
                lower.startsWith("com.android.settings") ||
                lower.startsWith("com.google.android.settings") ||
                lower.contains("securitycenter") ||
                lower.contains("safecenter") ||
                lower.contains("securityguard") ||
                lower.contains("permissioncontroller") ||
                lower.contains("packageinstaller") ||
                lower.contains("appmanager") ||
                lower.contains("phonemaster") ||
                (lower.contains("settings") && !lower.contains("systemui"))
    }

    /**
     * Checks whether text or package explicitly identifies another popular application.
     */
    private fun isExplicitOtherApp(text: String?, desc: String?, pkg: String?): Boolean {
        val otherPackages = listOf(
            "whatsapp", "instagram", "youtube", "chrome", "tiktok",
            "facebook", "twitter", "snapchat", "telegram", "spotify",
            "netflix", "gmail", "maps", "messaging", "camera", "gallery"
        )
        val sText = text?.trim()?.lowercase() ?: ""
        val sDesc = desc?.trim()?.lowercase() ?: ""
        val sPkg = pkg?.trim()?.lowercase() ?: ""

        return otherPackages.any {
            (sPkg.contains(it) && !sPkg.contains("blockify")) ||
            (sText == it || sText.startsWith("$it ") || sText.endsWith(" $it")) ||
            (sDesc == it || sDesc.startsWith("$it ") || sDesc.endsWith(" $it"))
        }
    }

    private fun nodeOrDescendantsContainText(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false
        val t = node.text?.toString()?.lowercase() ?: ""
        val d = node.contentDescription?.toString()?.lowercase() ?: ""
        if (t.contains(target) || d.contains(target)) return true
        val count = node.childCount
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val found = nodeOrDescendantsContainText(child, target)
            try { child.recycle() } catch (_: Exception) {}
            if (found) return true
        }
        return false
    }

    /**
     * Helper to confirm exact Blockify identity using package name, app labels, and aliases (PART 4).
     */
    private fun isBlockifyExactTarget(text: String?, desc: String?): Boolean {
        val appLabel = getString(R.string.app_name).trim().lowercase()
        val serviceLabel = try { getString(R.string.accessibility_service_label).trim().lowercase() } catch (_: Exception) { "blockify protection" }
        val pkgLower = packageName.lowercase()

        fun matches(s: String?): Boolean {
            if (s.isNullOrBlank()) return false
            val lower = s.trim().lowercase()
            return lower == appLabel ||
                    lower == serviceLabel ||
                    lower == "blockify" ||
                    lower == "blockify protection" ||
                    lower == "blockify app" ||
                    lower == pkgLower ||
                    lower == "com.blockifyroman.app" ||
                    lower == "com.example" ||
                    lower == "com.example.recoveryshield"
        }

        return matches(text) || matches(desc)
    }

    private fun isDirectBlockifyTarget(text: String?, desc: String?): Boolean {
        return isBlockifyExactTarget(text, desc)
    }

    // Scoped temporal context memory & dedicated context detectors
    private val scopedMemory = ScopedShortLivedContextMemory(ttlMillis = 2500L)
    private val appInfoDetector = BlockifyAppInfoDetector(scopedMemory)
    private val accessibilitySettingsDetector = BlockifyAccessibilitySettingsDetector(scopedMemory)
    private val allowlistManager = com.example.recoveryshield.protection.detector.AllowlistManager()

    private fun isBrowserOrSearchApp(p: String): Boolean {
        if (p.isBlank()) return false
        val lower = p.lowercase()
        return lower == "com.android.chrome" ||
                lower == "org.mozilla.firefox" ||
                lower == "com.microsoft.emmx" ||
                lower == "com.opera.browser" ||
                lower == "com.opera.mini.native" ||
                lower == "com.opera.gx" ||
                lower == "com.brave.browser" ||
                lower == "com.duckduckgo.mobile.android" ||
                lower == "com.sec.android.app.sbrowser" ||
                lower == "com.google.android.youtube" ||
                lower == "com.google.android.apps.youtube.music" ||
                lower == "com.ucmobile.intl" ||
                lower == "com.google.android.googlequicksearchbox" ||
                lower.contains(".chrome") ||
                lower.contains(".browser") ||
                lower.contains(".firefox") ||
                lower.contains(".brave") ||
                lower.contains(".duckduckgo") ||
                lower.contains("instagram") ||
                lower.contains("youtube") ||
                lower.contains("tiktok") ||
                lower.contains("facebook") ||
                lower.contains("twitter") ||
                lower.contains("reddit")
    }

    @Volatile
    private var lastTriggeredFingerprintKey: String = ""
    @Volatile
    private var lastTriggeredFingerprintTime: Long = 0L

    data class ResolvedWindowContext(
        val windowId: Int,
        val packageName: String,
        val rootNode: AccessibilityNodeInfo?,
        val sourceNode: AccessibilityNodeInfo?,
        val windowClass: String,
        val isInteractiveWindowMatched: Boolean
    )

    /**
     * Resolves the current authoritative Accessibility context in strict priority order:
     * 1. event.windowId
     * 2. event.source.windowId
     * 3. getRootInActiveWindow()
     * 4. getWindows() / matching AccessibilityWindowInfo
     * 5. source fallback
     */
    private fun resolveCurrentAccessibilityContext(event: AccessibilityEvent): ResolvedWindowContext {
        val eventPkg = (event.packageName?.toString() ?: "").lowercase()
        val sourceNode = try { event.source } catch (_: Exception) { null }
        val targetWindowId = if (event.windowId >= 0) {
            event.windowId
        } else {
            (try { sourceNode?.windowId } catch (_: Exception) { -1 }) ?: -1
        }

        var matchedRoot: AccessibilityNodeInfo? = null
        var isMatchedFromWindows = false
        var windowClass = ""

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                val exactMatch = windowList.firstOrNull { w ->
                    w.id == targetWindowId && w.root?.packageName?.toString()?.lowercase() == eventPkg
                }
                val idMatch = exactMatch ?: windowList.firstOrNull { w -> w.id == targetWindowId }
                val pkgMatch = idMatch ?: windowList.firstOrNull { w ->
                    w.root?.packageName?.toString()?.lowercase() == eventPkg
                }

                val chosenWindow = pkgMatch ?: windowList.firstOrNull { it.isFocused || it.isActive }
                if (chosenWindow != null) {
                    matchedRoot = chosenWindow.root
                    windowClass = chosenWindow.title?.toString() ?: ""
                    isMatchedFromWindows = matchedRoot != null
                }
            }
        } catch (_: Exception) {
            // Safe handling for windows-changed or permission exceptions
        }

        if (matchedRoot == null) {
            matchedRoot = try { rootInActiveWindow } catch (_: Exception) { null }
        }

        if (matchedRoot == null) {
            matchedRoot = sourceNode
        }

        val resolvedPkg = (matchedRoot?.packageName?.toString() ?: eventPkg).lowercase()
        val finalClass = if (windowClass.isNotBlank()) windowClass else (matchedRoot?.className?.toString() ?: event.className?.toString() ?: "")

        return ResolvedWindowContext(
            windowId = targetWindowId,
            packageName = resolvedPkg,
            rootNode = matchedRoot,
            sourceNode = sourceNode,
            windowClass = finalClass,
            isInteractiveWindowMatched = isMatchedFromWindows
        )
    }

    private fun logPipelineDiagnostics(
        timestamp: Long,
        eventType: Int,
        eventPackage: String,
        eventWindowId: Int,
        sourceNode: AccessibilityNodeInfo?,
        resolvedWindow: ResolvedWindowContext,
        detectorSelected: String,
        contextType: String,
        identityFound: Boolean,
        primarySignalFound: Boolean,
        nodesScanned: Int,
        ruleId: String,
        ruleScope: DetectionRuleScope,
        decision: String,
        decisionReason: String
    ) {
        if (!VERBOSE_PIPELINE_LOGS) {
            return
        }
        val sourcePackage = try { sourceNode?.packageName?.toString() ?: "" } catch (_: Exception) { "" }
        val sourceClass = try { sourceNode?.className?.toString() ?: "" } catch (_: Exception) { "" }
        val sourceViewId = try { sourceNode?.viewIdResourceName ?: "" } catch (_: Exception) { "" }
        val sourceTextLength = try { sourceNode?.text?.length ?: 0 } catch (_: Exception) { 0 }

        Log.d("BLOCKIFY_DETECTION_PIPELINE", """
            |--- BLOCKIFY_DETECTION_PIPELINE ---
            |timestamp: $timestamp
            |eventType: ${eventTypeToString(eventType)}
            |eventPackage: $eventPackage
            |eventWindowId: $eventWindowId
            |sourcePackage: $sourcePackage
            |sourceClass: $sourceClass
            |sourceViewId: $sourceViewId
            |sourceTextLength: $sourceTextLength
            |activeWindowPackage: ${resolvedWindow.packageName}
            |activeWindowId: ${resolvedWindow.windowId}
            |rootPackage: ${resolvedWindow.rootNode?.packageName ?: ""}
            |rootClass: ${resolvedWindow.windowClass}
            |detectorSelected: $detectorSelected
            |contextType: $contextType
            |identityFound: $identityFound
            |primarySignalFound: $primarySignalFound
            |nodesScanned: $nodesScanned
            |ruleId: $ruleId
            |ruleScope: $ruleScope
            |decision: $decision
            |decisionReason: $decisionReason
            |-----------------------------------
        """.trimMargin())
    }

    /**
     * Reliable Blockify App Info State Detection.
     */
    fun detectBlockifyAppInfoContext(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        activeWindowPkg: String
    ): Boolean {
        if (!isSettingsOrManagementPackage(pkg) && !isSettingsOrManagementPackage(activeWindowPkg)) return false
        val windowId = if (event.windowId >= 0) event.windowId else (try { rootNode?.windowId ?: event.source?.windowId ?: -1 } catch (_: Exception) { -1 })
        val res = appInfoDetector.detect(
            rootNode = rootNode,
            windowId = windowId,
            packageName = pkg,
            ownPackageName = packageName,
            appLabel = getString(R.string.app_name),
            serviceLabel = try { getString(R.string.accessibility_service_label) } catch (_: Exception) { "Blockify Protection" },
            isSettingsPackage = true
        )
        return res.contextType == SettingsContextType.BLOCKIFY_APP_INFO && res.identityFound && res.primarySignalFound
    }

    /**
     * Reliable Blockify Accessibility Settings State Detection.
     */
    fun detectBlockifyAccessibilitySettingsContext(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        activeWindowPkg: String
    ): Boolean {
        if (!isSettingsOrManagementPackage(pkg) && !isSettingsOrManagementPackage(activeWindowPkg)) return false
        val windowId = if (event.windowId >= 0) event.windowId else (try { rootNode?.windowId ?: event.source?.windowId ?: -1 } catch (_: Exception) { -1 })
        val res = accessibilitySettingsDetector.detect(
            rootNode = rootNode,
            windowId = windowId,
            packageName = pkg,
            ownPackageName = packageName,
            appLabel = getString(R.string.app_name),
            serviceLabel = try { getString(R.string.accessibility_service_label) } catch (_: Exception) { "Blockify Protection" },
            isSettingsPackage = true
        )
        return res.contextType == SettingsContextType.BLOCKIFY_ACCESSIBILITY_SETTINGS && res.identityFound && res.primarySignalFound
    }

    /**
     * Inspects accessibility events and node hierarchies in Settings, Package Installer,
     * or Security Center to identify if the user is targeting Blockify specifically
     * for uninstallation, force stop, storage clearing, or accessibility deactivation.
     */
    private fun isBlockifyAppInfoOrTamperContext(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        activeWindowPkg: String,
        windowId: Int
    ): SettingsContextResult {
        if (!isSettingsOrManagementPackage(pkg) && !isSettingsOrManagementPackage(activeWindowPkg)) {
            currentManagementContext = ManagementContext.NONE
            return SettingsContextResult(
                contextType = SettingsContextType.NONE,
                packageName = pkg,
                windowId = windowId,
                rootPackage = activeWindowPkg,
                identityFound = false,
                primarySignalFound = false,
                confidence = 0f,
                reason = "Not in system settings"
            )
        }

        val appLabel = getString(R.string.app_name)
        val serviceLabel = try { getString(R.string.accessibility_service_label) } catch (_: Exception) { "Blockify Protection" }

        val appInfoRes = appInfoDetector.detect(
            rootNode = rootNode,
            windowId = windowId,
            packageName = pkg,
            ownPackageName = packageName,
            appLabel = appLabel,
            serviceLabel = serviceLabel,
            isSettingsPackage = true
        )

        if (appInfoRes.contextType == SettingsContextType.BLOCKIFY_APP_INFO && appInfoRes.identityFound && appInfoRes.primarySignalFound) {
            currentManagementContext = ManagementContext.BLOCKIFY_APP_INFO
            return appInfoRes
        }

        val accRes = accessibilitySettingsDetector.detect(
            rootNode = rootNode,
            windowId = windowId,
            packageName = pkg,
            ownPackageName = packageName,
            appLabel = appLabel,
            serviceLabel = serviceLabel,
            isSettingsPackage = true
        )

        if (accRes.contextType == SettingsContextType.BLOCKIFY_ACCESSIBILITY_SETTINGS && accRes.identityFound && accRes.primarySignalFound) {
            currentManagementContext = ManagementContext.BLOCKIFY_ACCESSIBILITY_SETTINGS
            return accRes
        }

        if (appInfoRes.contextType == SettingsContextType.OTHER_SYSTEM_SETTINGS) {
            currentManagementContext = ManagementContext.OTHER_APP_INFO
            return appInfoRes
        }

        currentManagementContext = ManagementContext.NONE
        return SettingsContextResult(
            contextType = SettingsContextType.NONE,
            packageName = pkg,
            windowId = windowId,
            rootPackage = activeWindowPkg,
            identityFound = false,
            primarySignalFound = false,
            confidence = 0f,
            reason = "No target match"
        )
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val lowerPkg = pkg.lowercase()

        // Exclude Settings, Installer, Play Store, and SystemUI first
        if (isSettingsOrManagementPackage(lowerPkg) ||
            lowerPkg.equals("com.android.vending", ignoreCase = true) ||
            lowerPkg.contains("systemui")
        ) {
            return false
        }

        if (cachedLauncherPackages.contains(lowerPkg)) {
            return true
        }

        val now = SystemClock.uptimeMillis()
        if (cachedLauncherPackages.isEmpty() || (now - lastLauncherCacheUpdateTime) > 30000L) {
            updateCachedLauncherPackages()
            if (cachedLauncherPackages.contains(lowerPkg)) {
                return true
            }
        }

        return lowerPkg.endsWith(".launcher") ||
                lowerPkg.contains(".launcher.") ||
                lowerPkg == "com.android.launcher3" ||
                lowerPkg == "com.google.android.apps.nexuslauncher" ||
                lowerPkg == "com.sec.android.app.launcher" ||
                lowerPkg == "com.miui.home" ||
                lowerPkg == "com.oppo.launcher" ||
                lowerPkg == "com.huawei.android.launcher" ||
                lowerPkg == "com.oneplus.launcher"
    }

    private fun eventTypeToString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "TYPE_VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "TYPE_VIEW_TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            else -> "EVENT_$eventType"
        }
    }

    private fun handleLauncherEvent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?,
        pkg: String,
        activeWindowPkg: String,
        now: Long
    ) {
        if (!isProtectionActive()) {
            return
        }
        val eventTypeStr = eventTypeToString(event.eventType)
        val eventPackage = event.packageName?.toString() ?: ""
        val sourcePackage = event.source?.packageName?.toString() ?: ""
        val windowPackage = activeWindowPkg
        val windowClass = rootNode?.className?.toString() ?: ""
        val sourceClass = event.source?.className?.toString() ?: ""
        val sourceText = event.source?.text?.toString()?.trim() ?: ""
        val contentDescription = event.source?.contentDescription?.toString()?.trim() ?: ""
        
        val eventTexts = event.text?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
        val allTextJoined = (eventTexts + listOf(sourceText, contentDescription)).joinToString(" ").lowercase()

        val appLabel = getString(R.string.app_name).trim().lowercase()
        val pkgName = packageName.lowercase()

        val detectedTargetPackage = if (sourcePackage.isNotBlank()) sourcePackage else eventPackage

        val isExplicitBlockifyText = sourceText.lowercase() == appLabel ||
                sourceText.lowercase() == "blockify" ||
                sourceText.lowercase() == pkgName ||
                contentDescription.lowercase() == appLabel ||
                contentDescription.lowercase() == "blockify" ||
                eventTexts.any { it.lowercase() == appLabel || it.lowercase() == "blockify" }

        val isExplicitOtherAppText = sourceText.lowercase() in listOf("whatsapp", "instagram", "youtube", "chrome", "tiktok", "facebook", "twitter", "snapchat", "telegram", "spotify", "netflix", "gmail", "maps", "messaging", "camera", "gallery") ||
                detectedTargetPackage.contains("whatsapp") ||
                detectedTargetPackage.contains("instagram") ||
                detectedTargetPackage.contains("youtube") ||
                detectedTargetPackage.contains("chrome") ||
                sourceText.lowercase().contains("whatsapp") ||
                sourceText.lowercase().contains("instagram") ||
                sourceText.lowercase().contains("youtube")

        val isBlockifyTarget: Boolean
        if (isExplicitOtherAppText) {
            isBlockifyTarget = false
        } else if (isExplicitBlockifyText || detectedTargetPackage == pkgName || detectedTargetPackage.contains("blockify")) {
            isBlockifyTarget = true
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            val isBlockifyLongPress = isDirectBlockifyTarget(sourceText, contentDescription)
            isBlockifyTarget = isBlockifyLongPress
            if (isBlockifyLongPress) {
                lastBlockifyLongPressTime = now
                targetIsBlockify = true
            } else {
                lastBlockifyLongPressTime = 0L
                targetIsBlockify = false
            }
        } else {
            // For click or window state changes, require confirmed targetIsBlockify within short window
            isBlockifyTarget = targetIsBlockify && (now - lastBlockifyLongPressTime <= 5000L)
        }

        val appInfoDetected = allTextJoined.contains("app info") || allTextJoined.contains("app details")
        val uninstallDetected = allTextJoined.contains("uninstall") || allTextJoined.contains("delete") || allTextJoined.contains("remove")

        val finalDecision: String
        val shouldTrigger: Boolean

        if (isBlockifyTarget && (appInfoDetected || uninstallDetected || event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED && isExplicitBlockifyText)) {
            finalDecision = "ALLOW EXISTING BLOCKIFY PROTECTION"
            shouldTrigger = true
        } else {
            finalDecision = "REJECT"
            shouldTrigger = false
        }

        if (VERBOSE_PIPELINE_LOGS && Log.isLoggable("BLOCKIFY_TARGET_DEBUG", Log.DEBUG)) {
            Log.d("BLOCKIFY_TARGET_DEBUG", """
                |--- BLOCKIFY_TARGET_DEBUG ---
                |eventType: $eventTypeStr
                |eventPackage: $eventPackage
                |sourcePackage: $sourcePackage
                |windowPackage: $windowPackage
                |windowClass: $windowClass
                |sourceClass: $sourceClass
                |sourceText: $sourceText
                |contentDescription: $contentDescription
                |targetNodeInfo: text=$sourceText, desc=$contentDescription
                |parentInfo: ${try { event.source?.parent?.className } catch (_: Exception) { "null" }}
                |candidateApplication: $eventPackage
                |detectedTargetPackage: $detectedTargetPackage
                |isBlockifyTarget: $isBlockifyTarget
                |appInfoDetected: $appInfoDetected
                |uninstallDetected: $uninstallDetected
                |finalDecision: $finalDecision
                |-----------------------------
            """.trimMargin())
        }

        if (shouldTrigger && isBlockifyTarget) {
            lastBlockifyLongPressTime = 0L
            targetIsBlockify = false
            postInterventionCooldownUntil = 0L
            Log.i("BLOCKIFY_LAUNCHER", "Confirmed Blockify management action -> Triggering intervention!")
            BlockifyPerfTracker.onMatchConfirmed("Blockify launcher target action confirmed")
            ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                "Blockify Protection Active: Modifying or uninstalling Blockify is guarded",
                isAntiTamper = true
            )
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("RecoveryAccessService", "onAccessibilityEvent: $event")
        if (event == null) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            scheduleForegroundRecheck()
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                selfProtectionDetector.debugToast("BLK: Event $type")
            }
        }
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg.equals(packageName, ignoreCase = true)) return
        if (!isPackageRelevantFast(pkg)) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (USE_BACKGROUND_EVENT_WORKER) postToWorker { scopedMemory.clearAll() } else scopedMemory.clearAll()
            }
            return
        }
        if (!USE_BACKGROUND_EVENT_WORKER) { processAccessibilityEvent(event); return }
        val isContent = type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (isContent && !contentEventPending.compareAndSet(false, true)) return    // coalesce bursts: at most one pending content event
        @Suppress("DEPRECATION")
        val copy = try { AccessibilityEvent.obtain(event) } catch (_: Exception) { if (isContent) contentEventPending.set(false); processAccessibilityEvent(event); return }
        val delay = if (isContent) 80L else 0L        // STATE_CHANGED, CLICKED, LONG_CLICKED, TEXT_CHANGED, FOCUSED are NEVER delayed or dropped
        eventWorkerHandler.postDelayed({
            try {
                if (isContent) contentEventPending.set(false)
                processAccessibilityEvent(copy)
            } catch (t: Throwable) {
                Log.e(TAG, "event worker error: ${t.message}")
            } finally {
                @Suppress("DEPRECATION") try { copy.recycle() } catch (_: Exception) {}
            }
        }, delay)
    }

    private fun processAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isProtectionActive()) return

        // 1. SelfProtectionDetector (isolated detector for self-protection)
        // Must never cause an early return that blocks other detectors, and other detectors must never block it.
        try {
            val handled = selfProtectionDetector.onAccessibilityEvent(event, this)
            if (handled) {
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SelfProtectionDetector: ${e.message}", e)
        }

        if (!isProtectionActive()) {
            return
        }
        BlockifyPerfTracker.onEventReceived()

        val eventType = event.eventType
        // Filter strictly to relevant UI state/content changes and text editing keystrokes
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        ) {
            return
        }

        val now = SystemClock.uptimeMillis()

        // Authoritative Duplicate Guard: If intervention overlay or emergency open prompt is already actively displayed,
        // ignore duplicate events so countdown or interactive code entry completes cleanly without interruption.
        if (isOverlayShowing() || EmergencyOpenSession.isPromptVisible) {
            if (!isOverlayShowing()) {
                // Self-healing: if overlay is no longer showing, clear the prompt visible flag
                EmergencyOpenSession.isPromptVisible = false
            } else {
                return
            }
        }

        val pkg = (event.packageName?.toString() ?: "").lowercase()

        // Fast-filter: SystemUI (Notification Shade / Status Bar / Quick Settings / Volume Panel / Keyboards)
        // Strictly exempt user notification shade and quick toggles so reading Blockify notification never triggers an overlay
        val isSystemUiOrNotification = pkg.contains("systemui") ||
                pkg.contains("notification") ||
                pkg.contains("quicksettings") ||
                pkg.contains("statusbar") ||
                pkg.contains("recents") ||
                pkg.contains("inputmethod") ||
                pkg.contains("keyboard") ||
                pkg.contains("system_ui") ||
                (event.className?.toString()?.lowercase()?.let {
                    it.contains("notification") || it.contains("shade") || it.contains("quicksettings") ||
                    it.contains("statusbar") || it.contains("qscontainer") || it.contains("centralsurfaces")
                } == true)

        if (isSystemUiOrNotification) {
            return
        }

        val isSettingsOrMgmt = isSettingsOrManagementPackage(pkg)
        val isLauncherPkg = isLauncherPackage(pkg)

        if (!isProtectionActive()) {
            return
        }

        if (!isSettingsOrManagementPackage(pkg)) {
            scopedMemory.clearAll()
        }
        BlockifyPerfTracker.onPackageIdentified()

        // Fast-filter: Exempt our own application immediately (False positive rule)
        val ownPkg = packageName.lowercase()
        val isExplicitlyOurApp = !isSettingsOrMgmt &&
                (pkg == ownPkg || pkg == "com.blockifyroman.app" || pkg == "com.example" || pkg == "com.example.recoveryshield") &&
                !pkg.contains("inputmethod") && !pkg.contains("keyboard")
        if (isExplicitlyOurApp) {
            return
        }

        // =========================================================================
        // PRIORITY 1: FULL APP BLOCKING RULES (e.g. WhatsApp, YouTube, Instagram, etc.)
        // Checked FIRST before any camera, safe context, or system filters!
        // =========================================================================
        val blockRulesMgr = com.example.recoveryshield.protection.block.BlockRulesManager.getInstance(applicationContext)
        val fullBlockRule = blockRulesMgr.checkFullAppBlock(pkg)
        val hasActiveRuleForPkg = fullBlockRule != null || blockRulesMgr.getRules().any {
            (it.appPackage.equals(pkg, ignoreCase = true) || (it.appPackage.equals("com.whatsapp", ignoreCase = true) && pkg.equals("com.whatsapp.w4b", ignoreCase = true))) && it.active && !it.paused
        }

        if (fullBlockRule != null) {
            performCompliantNavigationAway()
            ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                "Blocked App: ${fullBlockRule.appName} is blocked by your protection schedule",
                isAntiTamper = false
            )
            return
        }
        
        // =========================================================================
        // PRIORITY 1b: FEATURE-LEVEL BLOCKING RULES
        // Evaluated if full app blocking rule is absent.
        // =========================================================================
        var rootNode = try { rootInActiveWindow ?: event.source } catch (_: Exception) { null }
        val featureBlockResult = blockRulesMgr.checkFeatureBlock(
            packageName = pkg,
            textFragments = emptyList(), // Can be extended with actual scanned text
            contentDescriptions = emptyList(),
            className = event.className?.toString() ?: "",
            viewIds = emptyList(),
            eventType = event.eventType,
            eventText = event.text?.joinToString(" ") ?: "",
            rootNode = rootNode
        )
        
        if (featureBlockResult != null) {
            val (rule, feature) = featureBlockResult
            ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                "Blocked Feature: ${feature.title} in ${rule.appName} is blocked",
                isAntiTamper = false
            )
            return
        }

        // =========================================================================
        // PRIORITY 2: BROWSER & KEYWORD INSTANT CONTENT TRIGGERS
        // Evaluates immediately for browsers & search/social platforms (Chrome, YouTube, Instagram, etc.)
        // =========================================================================
        val isBrowser = isBrowserOrSearchApp(pkg)
        // rootNode already defined above

        if (isBrowser) {
            if (evaluateAllContentTriggers(event, rootNode)) {
                return
            }
        }

        // Fast-filter: Camera apps, live cameras, WhatsApp in-chat camera & photo capture are strictly exempt
        val eventClass = event.className?.toString()?.lowercase() ?: ""
        val isCameraContext = pkg.contains("camera") ||
                (pkg.contains("whatsapp") && (
                    eventClass.contains("camera") ||
                    eventClass.contains("mediacomposer") ||
                    eventClass.contains("photo") ||
                    eventClass.contains("capture")
                ))
        if (isCameraContext && !isSettingsOrMgmt) {
            return
        }

        // Fast-filter: Phone calls, dialers, contacts, alarms, and safe utility apps are strictly exempt
        if (allowlistManager.isPackageSafeContext(pkg) && !isSettingsOrMgmt) {
            return
        }

        // If it's not settings, not launcher, not a web browser, and user has not created an active block rule:
        // DO NOT scan or pop up overlays (protects apps like ChatGPT, phone calls, games, etc.)
        if (!isSettingsOrMgmt && !isLauncherPkg && !isBrowser && !hasActiveRuleForPkg) {
            return
        }

        val diag = com.example.recoveryshield.protection.util.BlockifyLatencyTracker.EventDiagnosticContext(
            EVENT_TYPE = eventTypeToString(eventType),
            EVENT_PACKAGE = pkg,
            EVENT_WINDOW = event.className?.toString() ?: "",
            EVENT_CLASS = event.source?.className?.toString() ?: "",
            eventTimeMillis = event.eventTime
        )
        SystemInterventionOverlay.activeDiagnosticContext = diag

        // =========================================================================
        // ULTRA-FAST ZERO-IPC PATH (Keystrokes, URLs, Typed Text, Direct Captions)
        // Evaluates BEFORE calling rootInActiveWindow to avoid Binder IPC latency!
        // =========================================================================
        diag.markTextExtractionStart()

        fun isDirectAntiTamperText(s: String?): Boolean {
            if (!isSettingsOrMgmt) return false
            if (isBrowser || isBrowserOrSearchApp(pkg)) return false
            if (s.isNullOrBlank()) return false
            if (com.example.recoveryshield.permissions.PermissionOrchestrator.isCurrentlyInPermissionFlow()) return false
            if (!isProtectionActive()) return false
            val engine = ProtectionEngine.getInstance(applicationContext)
            if (!engine.isAntiUninstallProtectionEnabled()) return false

            val lower = s.lowercase().trim()
            val ownPkgLower = packageName.lowercase()
            val hasBlockifyMention = lower.contains("blockify") || lower.contains(ownPkgLower) || lower.contains("com.blockifyroman.app")
            if (hasBlockifyMention) {
                // If user is simply allowing, turning on, or viewing permission descriptions, exempt:
                if (lower.contains("use blockify") || lower.contains("allow") || lower.contains("enable") ||
                    lower.contains("turn on") || lower.contains("permission") || lower.contains("accessibility") ||
                    lower.contains("shortcut") || lower.contains("device admin") || lower.contains("activate this device admin")) {
                    return false
                }
                if (lower.contains("uninstall") || lower.contains("force stop") ||
                    lower.contains("clear all data") || lower.contains("deactivate device admin") ||
                    lower.contains("deactivate this device admin app") || lower.contains("remove device admin")) {
                    return true
                }
            }
            return false
        }

        val eventTexts = event.text
        if (!eventTexts.isNullOrEmpty()) {
            for (charSeq in eventTexts) {
                val textStr = charSeq?.toString() ?: continue
                diag.markTextExtractionEnd()
                diag.markMatchStart()
                if (isDirectAntiTamperText(textStr)) {
                    diag.markMatchEnd("MATCHED_INSTANT_ANTI_TAMPER_TEXT: $textStr")
                    BlockifyPerfTracker.onMatchConfirmed("Instant Anti-Tamper in event.text: $textStr")
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                        "Blockify Protection Active: Modifying or disabling protection is guarded",
                        isAntiTamper = true
                    )
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
                val trigger = InstantKeywordMatcher.findInstantTrigger(textStr)
                if (trigger != null) {
                    diag.markMatchEnd("MATCHED_INSTANT_EVENT_TEXT: $trigger")
                    BlockifyPerfTracker.onMatchConfirmed("Instant trigger in event.text: $trigger")
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
        }
        val descStr = event.contentDescription?.toString()
        if (!descStr.isNullOrBlank()) {
            diag.markTextExtractionEnd()
            diag.markMatchStart()
            if (isDirectAntiTamperText(descStr)) {
                diag.markMatchEnd("MATCHED_INSTANT_ANTI_TAMPER_DESC: $descStr")
                BlockifyPerfTracker.onMatchConfirmed("Instant Anti-Tamper in contentDescription: $descStr")
                ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                    "Blockify Protection Active: Modifying or disabling protection is guarded",
                    isAntiTamper = true
                )
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
            val trigger = InstantKeywordMatcher.findInstantTrigger(descStr)
            if (trigger != null) {
                diag.markMatchEnd("MATCHED_INSTANT_CONTENT_DESC: $trigger")
                BlockifyPerfTracker.onMatchConfirmed("Instant trigger in contentDescription: $trigger")
                ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        // Check source node text & description directly
        val sourceNode = try { event.source } catch (_: Exception) { null }
        if (sourceNode != null) {
            val sText = sourceNode.text?.toString()
            if (!sText.isNullOrBlank()) {
                diag.markTextExtractionEnd()
                diag.markMatchStart()
                if (isDirectAntiTamperText(sText)) {
                    diag.markMatchEnd("MATCHED_INSTANT_ANTI_TAMPER_SOURCE: $sText")
                    BlockifyPerfTracker.onMatchConfirmed("Instant Anti-Tamper in sourceNode.text: $sText")
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                        "Blockify Protection Active: Modifying or disabling protection is guarded",
                        isAntiTamper = true
                    )
                    return
                }
                val trigger = InstantKeywordMatcher.findInstantTrigger(sText)
                if (trigger != null) {
                    diag.markMatchEnd("MATCHED_INSTANT_SOURCE_TEXT: $trigger")
                    BlockifyPerfTracker.onMatchConfirmed("Instant trigger in sourceNode.text: $trigger")
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
            val sDesc = sourceNode.contentDescription?.toString()
            if (!sDesc.isNullOrBlank()) {
                diag.markTextExtractionEnd()
                diag.markMatchStart()
                if (isDirectAntiTamperText(sDesc)) {
                    diag.markMatchEnd("MATCHED_INSTANT_ANTI_TAMPER_SOURCE_DESC: $sDesc")
                    BlockifyPerfTracker.onMatchConfirmed("Instant Anti-Tamper in sourceNode.contentDescription: $sDesc")
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                        "Blockify Protection Active: Modifying or disabling protection is guarded",
                        isAntiTamper = true
                    )
                    return
                }
                val trigger = InstantKeywordMatcher.findInstantTrigger(sDesc)
                if (trigger != null) {
                    diag.markMatchEnd("MATCHED_INSTANT_SOURCE_DESC: $trigger")
                    BlockifyPerfTracker.onMatchConfirmed("Instant trigger in sourceNode.contentDescription: $trigger")
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
        }
        diag.markTextExtractionEnd()

        // Defer rootInActiveWindow until needed for tree traversal or management contexts
        if (rootNode == null) rootNode = try { rootInActiveWindow ?: sourceNode } catch (_: Exception) { null }
        val activeWindowPkg = (rootNode?.packageName?.toString() ?: "").lowercase()

        // Strict Normal App Check (Browsers & Social Media)
        val isNormalApp = isBrowser ||
                pkg.contains("whatsapp") ||
                pkg.contains("instagram") || pkg.contains("youtube") ||
                pkg.contains("chrome") || pkg.contains("tiktok") ||
                pkg.contains("facebook") || pkg.contains("twitter") ||
                pkg.contains("telegram") || pkg.contains("snapchat") ||
                pkg.contains("spotify") || pkg.contains("netflix") ||
                activeWindowPkg.contains("whatsapp") ||
                activeWindowPkg.contains("instagram") || activeWindowPkg.contains("youtube") ||
                activeWindowPkg.contains("chrome") || activeWindowPkg.contains("tiktok")

        val isSystemSettingsOrInstaller = isSettingsOrManagementPackage(pkg) || isSettingsOrManagementPackage(activeWindowPkg)

        val contextType = when {
            isNormalApp -> "NORMAL_APP"
            isSystemSettingsOrInstaller -> {
                val sourceText = event.source?.text?.toString()?.lowercase() ?: ""
                if (sourceText.contains("accessibility") || windowClassContainsAccessibility(rootNode)) {
                    "ANDROID_ACCESSIBILITY_SETTINGS_CONTEXT"
                } else {
                    "BLOCKIFY_APP_INFO_CONTEXT"
                }
            }
            pkg.contains("packageinstaller") || activeWindowPkg.contains("packageinstaller") -> "BLOCKIFY_APP_INFO_CONTEXT"
            isLauncherPackage(pkg) || isLauncherPackage(activeWindowPkg) -> "LAUNCHER_MANAGEMENT_CONTEXT"
            else -> "CONTENT_TRIGGER"
        }

        if (Log.isLoggable("BLOCKIFY_CONTEXT_DEBUG", Log.DEBUG)) {
            val timestamp = System.currentTimeMillis()
            val eventTypeStr = eventTypeToString(eventType)
            val sourcePackage = event.source?.packageName?.toString() ?: ""
            val sourceClass = event.source?.className?.toString() ?: ""
            val sourceText = event.source?.text?.toString()?.trim() ?: ""
            val nodeBounds = try {
                val rect = android.graphics.Rect()
                event.source?.getBoundsInScreen(rect)
                rect.toString()
            } catch (_: Exception) { "unknown" }

            val blockifyTargetConfirmed = contextType == "LAUNCHER_MANAGEMENT_CONTEXT" || contextType == "BLOCKIFY_APP_INFO_CONTEXT" || contextType == "ANDROID_ACCESSIBILITY_SETTINGS_CONTEXT"
            val decision = if (isNormalApp && (sourceText.contains("Accessibility") || sourceText.contains("App info") || sourceText.contains("Uninstall"))) "REJECT" else if (blockifyTargetConfirmed) "ACCEPT_CANDIDATE" else "REJECT"
            val decisionReason = if (isNormalApp) "ordinary text in normal app ($pkg)" else "context type: $contextType"

            Log.d("BLOCKIFY_CONTEXT_DEBUG", """
                |--- BLOCKIFY_CONTEXT_DEBUG ---
                |timestamp: $timestamp
                |eventType: $eventTypeStr
                |eventPackage: $pkg
                |windowPackage: $activeWindowPkg
                |windowClass: ${rootNode?.className ?: ""}
                |sourcePackage: $sourcePackage
                |sourceClass: $sourceClass
                |targetPackage: $pkg
                |targetLabel: $sourceText
                |nodeBounds: $nodeBounds
                |contextType: $contextType
                |candidateType: MANAGEMENT_CANDIDATE
                |blockifyTargetConfirmed: $blockifyTargetConfirmed
                |decision: $decision
                |decisionReason: $decisionReason
                |-----------------------------
            """.trimMargin())
        }

        // If it's a web browser, evaluate instant content triggers first
        if (isBrowser) {
            if (evaluateAllContentTriggers(event, rootNode)) {
                return
            }
        }

        // System UI, Overview/Recents, Keyboards exempt from popups
        if (pkg.contains("systemui") ||
            pkg.contains("recents") ||
            pkg.contains("inputmethod") ||
            pkg.contains("keyboard")
        ) {
            return
        }

        // =========================================================================
        // NODE-BASED EVALUATION
        // =========================================================================
        BlockifyPerfTracker.onContentObtained()

        if (activeWindowPkg == packageName.lowercase() || activeWindowPkg == "com.blockifyroman.app" || activeWindowPkg == "com.example" || activeWindowPkg == "com.example.recoveryshield") {
            if (!pkg.contains("inputmethod") && !pkg.contains("keyboard")) return
        }

        // System Settings, Package Installer, Security Center (computed before launcher to prevent Settings FallbackHome being swallowed):
        if (isSystemSettingsOrInstaller) {
            val engine = ProtectionEngine.getInstance(applicationContext)
            if (!engine.isAntiUninstallProtectionEnabled() || !isProtectionActive() || com.example.recoveryshield.permissions.PermissionOrchestrator.isCurrentlyInPermissionFlow()) {
                // If anti-uninstall protection is not enabled, setup in progress, or protection inactive, do not restrict system settings navigation
                return
            }

            // Explicitly exempt permission configuration and device admin activation screens
            val classNameStr = (event.className?.toString() ?: "") + " " + (rootNode?.className?.toString() ?: "")
            if (classNameStr.contains("DeviceAdminAdd") || classNameStr.contains("AccessibilitySettings") ||
                classNameStr.contains("ManageAppExternalSources") || classNameStr.contains("AppDrawOverlay") ||
                classNameStr.contains("NotificationAccess") || classNameStr.contains("AppNotification") ||
                classNameStr.contains("SpecialAppAccess") || classNameStr.contains("Permission")
            ) {
                return
            }

            // 1. If App Info is currently authorized via a valid Emergency Open code, allow user access
            if (EmergencyOpenSession.isAppInfoAuthorized()) {
                Log.d(TAG, "Blockify App Info access authorized via active Emergency Open session")
                return
            }

            // 2. Direct App Info recognition in Settings:
            // Check if window or event currently displays Blockify App Info
            val eventTextsJoined = event.text.joinToString(" ") { it.toString().lowercase() }
            val ownPkgLower = packageName.lowercase()
            val hasBlockifyInEventText = eventTextsJoined.contains("blockify") || eventTextsJoined.contains(ownPkgLower) || eventTextsJoined.contains("com.blockifyroman.app")

            val sourceTextAll = (event.source?.text?.toString() ?: "") + " " + (event.source?.contentDescription?.toString() ?: "")
            val hasBlockifyInSource = sourceTextAll.lowercase().contains("blockify") || sourceTextAll.lowercase().contains(ownPkgLower) || sourceTextAll.lowercase().contains("com.blockifyroman.app")

            val hasBlockifyInWindow = rootNode?.let { node ->
                try {
                    val byText = node.findAccessibilityNodeInfosByText("Blockify")
                    (!byText.isNullOrEmpty()) || nodeOrDescendantsContainText(node, "blockify")
                } catch (_: Exception) { false }
            } ?: false

            val hasBlockify = hasBlockifyInEventText || hasBlockifyInSource || hasBlockifyInWindow

            if (hasBlockify) {
                // Check if this screen is an App Info screen targeting uninstall/force stop:
                val isAppInfoScreen = rootNode?.let { node ->
                    try {
                        val hasUninstall = node.findAccessibilityNodeInfosByText("Uninstall").isNotEmpty()
                        val hasForceStop = node.findAccessibilityNodeInfosByText("Force stop").isNotEmpty()
                        val hasStorage = node.findAccessibilityNodeInfosByText("Storage").isNotEmpty() || node.findAccessibilityNodeInfosByText("Storage & cache").isNotEmpty()
                        hasUninstall || (hasForceStop && hasStorage)
                    } catch (_: Exception) { false }
                } ?: (eventTextsJoined.contains("uninstall") || (eventTextsJoined.contains("force stop") && eventTextsJoined.contains("storage")))

                val classNameStr = (event.className?.toString() ?: "") + " " + (rootNode?.className?.toString() ?: "")
                val isAppInfoClass = classNameStr.contains("AppDetails") || classNameStr.contains("InstalledAppDetails") ||
                        classNameStr.contains("SpaActivity") || classNameStr.contains("AppInfo")

                if (isAppInfoScreen || (isAppInfoClass && (eventTextsJoined.contains("uninstall") || sourceTextAll.lowercase().contains("uninstall")))) {
                    Log.i(TAG, "Blockify App Info screen confirmed with Anti-Uninstall -> Guarding settings")
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                        "Blockify Protection Active: Modifying Blockify settings is guarded",
                        isAntiTamper = true
                    )
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }
            return
        }

        // Android Home / Launcher Context:
        val isLauncher = !isSystemSettingsOrInstaller && (isLauncherPackage(pkg) || isLauncherPackage(activeWindowPkg))
        if (isLauncher) {
            handleLauncherEvent(event, rootNode, pkg, activeWindowPkg, now)
            return
        }

        // Focused Search Input check (using already obtained rootNode, zero new IPC) - strictly for web browsers
        if (isBrowser && rootNode != null) {
            val focusedInput = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedInput != null) {
                val focusedText = focusedInput.text?.toString()
                if (!focusedText.isNullOrBlank()) {
                    val trigger = InstantKeywordMatcher.findInstantTrigger(focusedText)
                    if (trigger != null) {
                        BlockifyPerfTracker.onMatchConfirmed("Instant trigger in focused input: $trigger")
                        ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                        return
                    }
                }
            }

            // Quick scan of top nodes
            val treeTrigger = quickScanNodeForTrigger(rootNode, maxNodes = 40)
            if (treeTrigger != null) {
                BlockifyPerfTracker.onMatchConfirmed("Instant trigger in screen tree: $treeTrigger")
                ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant screen trigger: $treeTrigger")
                return
            }
        }

        // Full App & Feature Block Rules (YouTube Shorts, Instagram Reels, etc.)
        val blockRulesManager = blockRulesMgr

        val isCriticalEvent = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                hasActiveRuleForPkg

        if (!isCriticalEvent && pkg == lastPackageName && (now - lastProcessedTimestamp) < THROTTLE_INTERVAL_MS) {
            ProtectionEngine.getInstance(applicationContext).recordSkippedEvent()
            return
        }

        lastProcessedTimestamp = now
        lastPackageName = pkg

        try {
            val normalizedEvent = EventNormalizer.normalize(event, rootNode)
            BlockifyPerfTracker.onNormalizationComplete()

            if (hasActiveRuleForPkg) {
                val fullBlockRule = blockRulesManager.checkFullAppBlock(pkg)
                if (fullBlockRule != null) {
                    performCompliantNavigationAway()
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention(
                        "Blocked App: ${fullBlockRule.appName} is blocked by your protection schedule",
                        isAntiTamper = false
                    )
                    return
                }
            }

            BlockifyPerfTracker.onMatcherStarted()
            ProtectionEngine.getInstance(applicationContext).processNormalizedEvent(normalizedEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event defensively: ${e.message}", e)
        }
    }

    private fun quickScanNodeForTrigger(node: AccessibilityNodeInfo?, maxNodes: Int): String? {
        if (node == null) return null
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var count = 0
        while (!queue.isEmpty() && count < maxNodes) {
            val current = queue.poll() ?: break
            count++
            if (!current.isPassword) {
                val t = current.text?.toString()
                if (!t.isNullOrBlank()) {
                    val trigger = InstantKeywordMatcher.findInstantTrigger(t)
                    if (trigger != null) return trigger
                }
                val cd = current.contentDescription?.toString()
                if (!cd.isNullOrBlank()) {
                    val trigger = InstantKeywordMatcher.findInstantTrigger(cd)
                    if (trigger != null) return trigger
                }
            }
            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return null
    }

    private fun windowClassContainsAccessibility(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val cls = node.className?.toString()?.lowercase() ?: ""
        if (cls.contains("accessibility")) return true
        for (i in 0 until node.childCount) {
            if (windowClassContainsAccessibility(node.getChild(i))) return true
        }
        return false
    }

    private fun evaluateAllContentTriggers(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?): Boolean {
        val eventPkg = (event.packageName?.toString() ?: "").lowercase()
        // Content triggers are strictly evaluated inside web browsers
        if (!isBrowserOrSearchApp(eventPkg)) {
            return false
        }

        // 1. event.text
        val eventTexts = event.text
        if (!eventTexts.isNullOrEmpty()) {
            for (charSeq in eventTexts) {
                val textStr = charSeq?.toString() ?: continue
                val trigger = InstantKeywordMatcher.findInstantTrigger(textStr)
                if (trigger != null) {
                    BlockifyPerfTracker.onMatchConfirmed("Instant content trigger in event.text: $trigger")
                    performCompliantNavigationAway()
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                    return true
                }
            }
        }

        // 2. contentDescription
        val descStr = event.contentDescription?.toString()
        if (!descStr.isNullOrBlank()) {
            val trigger = InstantKeywordMatcher.findInstantTrigger(descStr)
            if (trigger != null) {
                BlockifyPerfTracker.onMatchConfirmed("Instant content trigger in contentDescription: $trigger")
                performCompliantNavigationAway()
                ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                return true
            }
        }

        // 3. sourceNode
        val sourceNode = try { event.source } catch (_: Exception) { null }
        if (sourceNode != null) {
            val sText = sourceNode.text?.toString()
            if (!sText.isNullOrBlank()) {
                val trigger = InstantKeywordMatcher.findInstantTrigger(sText)
                if (trigger != null) {
                    BlockifyPerfTracker.onMatchConfirmed("Instant content trigger in sourceNode.text: $trigger")
                    performCompliantNavigationAway()
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                    return true
                }
            }
            val sDesc = sourceNode.contentDescription?.toString()
            if (!sDesc.isNullOrBlank()) {
                val trigger = InstantKeywordMatcher.findInstantTrigger(sDesc)
                if (trigger != null) {
                    BlockifyPerfTracker.onMatchConfirmed("Instant content trigger in sourceNode.contentDescription: $trigger")
                    performCompliantNavigationAway()
                    ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                    return true
                }
            }
        }

        // 4. Screen nodes tree scan (quickScanNodeForTrigger)
        if (rootNode != null) {
            val focusedInput = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedInput != null) {
                val focusedText = focusedInput.text?.toString()
                if (!focusedText.isNullOrBlank()) {
                    val trigger = InstantKeywordMatcher.findInstantTrigger(focusedText)
                    if (trigger != null) {
                        BlockifyPerfTracker.onMatchConfirmed("Instant content trigger in focused input: $trigger")
                        performCompliantNavigationAway()
                        ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant trigger: $trigger")
                        return true
                    }
                }
            }

            val treeTrigger = quickScanNodeForTrigger(rootNode, maxNodes = 180)
            if (treeTrigger != null) {
                BlockifyPerfTracker.onMatchConfirmed("Instant content trigger in screen tree: $treeTrigger")
                performCompliantNavigationAway()
                ProtectionEngine.getInstance(applicationContext).triggerInstantIntervention("Instant screen trigger: $treeTrigger")
                return true
            }
        }

        return false
    }

    override fun onInterrupt() {
        Log.w("BLOCKIFY_TRIGGER", "Accessibility service interrupted.")
        systemOverlay?.dismiss()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i("BLOCKIFY_TRIGGER", "RecoveryAccessibilityService unbind.")
        com.example.recoveryshield.protection.util.BlockifyLatencyTracker.onServiceDisconnected()
        systemOverlay?.dismiss()
        systemOverlay = null
        instanceRef = null
        ProtectionEngine.getInstance(applicationContext).onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i("BLOCKIFY_TRIGGER", "RecoveryAccessibilityService destroyed.")
        try { eventWorkerThread.quitSafely() } catch (_: Exception) {}
        com.example.recoveryshield.protection.util.BlockifyLatencyTracker.onServiceDisconnected()
        systemOverlay?.dismiss()
        systemOverlay = null
        instanceRef = null
        ProtectionEngine.getInstance(applicationContext).onServiceDisconnected()
        super.onDestroy()
    }
}
