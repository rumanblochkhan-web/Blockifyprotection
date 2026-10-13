package com.example.recoveryshield.protection.detector

enum class SettingsContextType {
    NONE,
    BLOCKIFY_APP_INFO,
    BLOCKIFY_ACCESSIBILITY_SETTINGS,
    OTHER_SYSTEM_SETTINGS
}

data class SettingsContextResult(
    val contextType: SettingsContextType,
    val packageName: String,
    val windowId: Int,
    val rootPackage: String,
    val identityFound: Boolean,
    val primarySignalFound: Boolean,
    val confidence: Float,
    val detectedAt: Long = System.currentTimeMillis(),
    val reason: String = ""
)

enum class DetectionRuleScope {
    APP,
    FEATURE,
    SCREEN,
    CONTENT,
    URL_SITE
}

data class TriggerDecision(
    val shouldTrigger: Boolean,
    val isAntiTamper: Boolean,
    val reason: String,
    val ruleScope: DetectionRuleScope = DetectionRuleScope.CONTENT,
    val targetPackage: String = "",
    val windowId: Int = -1
)
