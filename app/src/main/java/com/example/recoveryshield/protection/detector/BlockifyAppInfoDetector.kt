package com.example.recoveryshield.protection.detector

import android.view.accessibility.AccessibilityNodeInfo

class BlockifyAppInfoDetector(
    private val memory: ScopedShortLivedContextMemory = ScopedShortLivedContextMemory()
) {

    companion object {
        private const val DETECTOR_TYPE = "APP_INFO"

        private val PRIMARY_ACTION_IDS = setOf(
            "button1", // Uninstall button
            "button2", // Force stop button
            "button3", // Open button
            "clear_data_button",
            "clear_cache_button",
            "button_uninstall",
            "button_force_stop",
            "entity_header",
            "app_snippet",
            "widget_button"
        )

        private val POPULAR_OTHER_APPS = setOf(
            "whatsapp", "instagram", "youtube", "chrome", "tiktok",
            "facebook", "twitter", "snapchat", "telegram", "spotify",
            "netflix", "gmail", "maps", "messaging", "camera", "gallery"
        )
    }

    fun detect(
        rootNode: AccessibilityNodeInfo?,
        windowId: Int,
        packageName: String,
        ownPackageName: String,
        appLabel: String,
        serviceLabel: String,
        isSettingsPackage: Boolean
    ): SettingsContextResult {
        if (!isSettingsPackage || rootNode == null) {
            return SettingsContextResult(
                contextType = SettingsContextType.NONE,
                packageName = packageName,
                windowId = windowId,
                rootPackage = rootNode?.packageName?.toString() ?: "",
                identityFound = false,
                primarySignalFound = false,
                confidence = 0f,
                reason = "Not in system settings or null root"
            )
        }

        val normAppLabel = appLabel.trim().lowercase()
        val normServiceLabel = serviceLabel.trim().lowercase()
        val ownPkg = ownPackageName.trim().lowercase()

        var identityFound = memory.hasIdentity(packageName, windowId, SettingsContextType.BLOCKIFY_APP_INFO, DETECTOR_TYPE)
        var primarySignalFound = memory.hasPrimarySignal(packageName, windowId, SettingsContextType.BLOCKIFY_APP_INFO, DETECTOR_TYPE)
        var explicitOtherAppFound = false
        var nodesScanned = 0

        BoundedNodeTraverser.traverse(rootNode, maxNodes = 100, maxDepth = 8, maxChildrenPerNode = 10) { node, _ ->
            nodesScanned++

            val text = node.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val rawViewId = try { node.viewIdResourceName ?: "" } catch (_: Exception) { "" }
            val viewId = rawViewId.substringAfter(":id/").lowercase()
            val nodePkg = node.packageName?.toString()?.trim()?.lowercase() ?: ""

            // Check if this screen is explicitly another app's App Info (e.g. title is another app)
            if (viewId == "entity_header_title" || viewId.contains("title")) {
                if (POPULAR_OTHER_APPS.any { text == it || desc == it }) {
                    explicitOtherAppFound = true
                }
            }

            // 1. Blockify Identity Verification
            if (!identityFound && !explicitOtherAppFound) {
                val isOwnPkg = nodePkg == ownPkg || nodePkg == "com.blockifyroman.app" || nodePkg == "com.example.recoveryshield"
                val isExactLabel = text == "blockify" || text == normAppLabel || desc == "blockify" || desc == normAppLabel
                val isServiceLabel = text == normServiceLabel || desc == normServiceLabel || text == "blockify protection"
                val isHeaderIdMatch = (viewId == "entity_header_title" || viewId.contains("title")) && (text.contains("blockify") || desc.contains("blockify"))

                if (isOwnPkg || isExactLabel || isServiceLabel || isHeaderIdMatch) {
                    identityFound = true
                    memory.record(
                        pkg = packageName,
                        windowId = windowId,
                        contextType = SettingsContextType.BLOCKIFY_APP_INFO,
                        detectorType = DETECTOR_TYPE,
                        text = text,
                        viewId = viewId,
                        isIdentity = true
                    )
                }
            }

            // 2. App Info Primary Structural Signals
            if (!primarySignalFound) {
                val isPrimaryId = PRIMARY_ACTION_IDS.contains(viewId)
                val isPrimaryText = text == "uninstall" || text == "force stop" || text == "clear data" || text == "clear storage" ||
                        text == "storage & cache" || text == "app info" || text == "storage" ||
                        desc == "uninstall" || desc == "force stop" || desc == "app info"
                val isPrimaryCompound = (text.contains("uninstall") || text.contains("force stop") || text.contains("storage") || text.contains("clear")) && !text.contains("scan")

                if (isPrimaryId || isPrimaryText || isPrimaryCompound) {
                    primarySignalFound = true
                    memory.record(
                        pkg = packageName,
                        windowId = windowId,
                        contextType = SettingsContextType.BLOCKIFY_APP_INFO,
                        detectorType = DETECTOR_TYPE,
                        text = text,
                        viewId = viewId,
                        isPrimarySignal = true
                    )
                }
            }

            // Early exit if signature is fully satisfied and not contaminated
            identityFound && primarySignalFound && !explicitOtherAppFound
        }

        if (explicitOtherAppFound) {
            return SettingsContextResult(
                contextType = SettingsContextType.OTHER_SYSTEM_SETTINGS,
                packageName = packageName,
                windowId = windowId,
                rootPackage = rootNode.packageName?.toString() ?: "",
                identityFound = false,
                primarySignalFound = primarySignalFound,
                confidence = 0f,
                reason = "Explicit other application identity confirmed"
            )
        }

        val isConfirmed = identityFound && primarySignalFound
        return SettingsContextResult(
            contextType = if (isConfirmed) SettingsContextType.BLOCKIFY_APP_INFO else SettingsContextType.NONE,
            packageName = packageName,
            windowId = windowId,
            rootPackage = rootNode.packageName?.toString() ?: "",
            identityFound = identityFound,
            primarySignalFound = primarySignalFound,
            confidence = if (isConfirmed) 1.0f else if (identityFound) 0.5f else 0f,
            reason = if (isConfirmed) "Blockify identity + App Info primary signal matched" else "Signature incomplete (id=$identityFound, action=$primarySignalFound)"
        )
    }
}
