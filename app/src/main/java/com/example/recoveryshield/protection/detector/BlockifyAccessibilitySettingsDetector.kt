package com.example.recoveryshield.protection.detector

import android.view.accessibility.AccessibilityNodeInfo

class BlockifyAccessibilitySettingsDetector(
    private val memory: ScopedShortLivedContextMemory = ScopedShortLivedContextMemory()
) {

    companion object {
        private const val DETECTOR_TYPE = "ACCESSIBILITY_SETTINGS"

        private val ACCESSIBILITY_KEYWORDS = setOf(
            "accessibility",
            "accessibility services",
            "installed services",
            "downloaded apps",
            "downloaded services",
            "installed apps"
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

        var identityFound = memory.hasIdentity(packageName, windowId, SettingsContextType.BLOCKIFY_ACCESSIBILITY_SETTINGS, DETECTOR_TYPE)
        var accessibilityContextFound = memory.hasPrimarySignal(packageName, windowId, SettingsContextType.BLOCKIFY_ACCESSIBILITY_SETTINGS, DETECTOR_TYPE)
        var nodesScanned = 0

        BoundedNodeTraverser.traverse(rootNode, maxNodes = 100, maxDepth = 8, maxChildrenPerNode = 10) { node, _ ->
            nodesScanned++

            val text = node.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val rawViewId = try { node.viewIdResourceName ?: "" } catch (_: Exception) { "" }
            val viewId = rawViewId.substringAfter(":id/").lowercase()
            val cls = node.className?.toString()?.trim()?.lowercase() ?: ""
            val nodePkg = node.packageName?.toString()?.trim()?.lowercase() ?: ""

            // 1. Accessibility Structural Signals
            if (!accessibilityContextFound) {
                val isAccKeyword = ACCESSIBILITY_KEYWORDS.any { text.contains(it) || desc.contains(it) }
                val isAccViewId = viewId == "switch_widget" || viewId == "switch_text" || viewId.contains("accessibility")
                val isSwitchWidget = cls.contains("switch") || cls.contains("toggle") || cls.contains("checkbox")
                val isToggleLabel = text.contains("use ") || text.contains("shortcut") || text.contains("service")

                if (isAccKeyword || isAccViewId || (isSwitchWidget && isToggleLabel)) {
                    accessibilityContextFound = true
                    memory.record(
                        pkg = packageName,
                        windowId = windowId,
                        contextType = SettingsContextType.BLOCKIFY_ACCESSIBILITY_SETTINGS,
                        detectorType = DETECTOR_TYPE,
                        text = text,
                        viewId = viewId,
                        isPrimarySignal = true
                    )
                }
            }

            // 2. Blockify Identity in Accessibility Settings
            if (!identityFound) {
                val isOwnPkg = nodePkg == ownPkg || nodePkg == "com.blockifyroman.app" || nodePkg == "com.example.recoveryshield"
                val isExactLabel = text == "blockify" || text == normAppLabel || desc == "blockify" || desc == normAppLabel
                val isServiceLabel = text == normServiceLabel || desc == normServiceLabel || text == "blockify protection" || text.contains("use blockify")
                val isBlockifyMention = (text.contains("blockify") || desc.contains("blockify")) &&
                        (viewId.contains("title") || viewId.contains("summary") || viewId.contains("switch") || viewId.isEmpty())

                if (isOwnPkg || isExactLabel || isServiceLabel || isBlockifyMention) {
                    identityFound = true
                    memory.record(
                        pkg = packageName,
                        windowId = windowId,
                        contextType = SettingsContextType.BLOCKIFY_ACCESSIBILITY_SETTINGS,
                        detectorType = DETECTOR_TYPE,
                        text = text,
                        viewId = viewId,
                        isIdentity = true
                    )
                }
            }

            // Early exit if both are found
            identityFound && accessibilityContextFound
        }

        val isConfirmed = identityFound && accessibilityContextFound
        return SettingsContextResult(
            contextType = if (isConfirmed) SettingsContextType.BLOCKIFY_ACCESSIBILITY_SETTINGS else SettingsContextType.NONE,
            packageName = packageName,
            windowId = windowId,
            rootPackage = rootNode.packageName?.toString() ?: "",
            identityFound = identityFound,
            primarySignalFound = accessibilityContextFound,
            confidence = if (isConfirmed) 1.0f else if (identityFound) 0.5f else 0f,
            reason = if (isConfirmed) "Blockify identity + Accessibility settings context matched" else "Signature incomplete (id=$identityFound, acc=$accessibilityContextFound)"
        )
    }
}
