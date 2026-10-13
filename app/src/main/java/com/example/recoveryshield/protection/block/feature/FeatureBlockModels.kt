package com.example.recoveryshield.protection.block.feature

/**
 * Backend-only immutable data models for universal feature identity and screen-context detection.
 */

enum class ProfileStatus {
    UNVERIFIED,
    VERIFIED,
    STALE,
    DISABLED
}

enum class SignalFamily {
    STRONG,
    MEDIUM,
    WEAK
}

enum class CandidateType {
    ENTRY,
    TARGET_SCREEN
}

data class NodeSnapshot(
    val id: Int,
    val viewId: String = "",
    val className: String = "",
    val textTokens: Set<String> = emptySet(),
    val contentDescTokens: Set<String> = emptySet(),
    val isSelected: Boolean = false,
    val isChecked: Boolean = false,
    val isFocused: Boolean = false,
    val isAccessibilityFocused: Boolean = false,
    val isEnabled: Boolean = true,
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isHeading: Boolean = false,
    val isVisibleToUser: Boolean = true,
    val childCount: Int = 0,
    val childOrder: Int = 0,
    val depth: Int = 0,
    val ancestorPath: String = "",
    val boundsTopFraction: Float = 0f,
    val boundsBottomFraction: Float = 0f,
    val boundsLeftFraction: Float = 0f,
    val boundsRightFraction: Float = 0f
)

data class WindowSnapshot(
    val windowId: Int,
    val windowType: Int = 0,
    val title: String = "",
    val isActive: Boolean = false,
    val isFocused: Boolean = false,
    val layer: Int = 0
)

data class ScreenSnapshot(
    val packageName: String,
    val className: String = "",
    val eventType: Int = 0,
    val eventTimestamp: Long = System.currentTimeMillis(),
    val eventSourceViewId: String = "",
    val eventSourceClass: String = "",
    val eventSourceTextTokens: Set<String> = emptySet(),
    val eventSourceDescTokens: Set<String> = emptySet(),
    val isClick: Boolean = false,
    val isComplete: Boolean = true,
    val totalNodeCount: Int = 0,
    val maxDepth: Int = 0,
    val nodes: List<NodeSnapshot> = emptyList(),
    val windows: List<WindowSnapshot> = emptyList(),
    val viewIdSet: Set<String> = emptySet(),
    val textTokenSet: Set<String> = emptySet(),
    val contentDescTokenSet: Set<String> = emptySet(),
    val activeTabSelectedTokens: Set<String> = emptySet()
)

data class FeatureSignal(
    val family: SignalFamily,
    val name: String,
    val weight: Float,
    val matched: Boolean,
    val description: String = ""
)

data class FeatureCandidate(
    val featureId: String,
    val score: Float,
    val candidateType: CandidateType,
    val matchedSignals: List<FeatureSignal> = emptyList(),
    val signalFamilies: Set<SignalFamily> = emptySet(),
    val targetContainerConfirmed: Boolean = false,
    val activeNavSelected: Boolean = false
)

data class FeatureConflict(
    val conflictingFeatureId: String,
    val conflictScore: Float,
    val reason: String
)

data class FeatureMatchResult(
    val isMatch: Boolean,
    val matchedFeatureId: String? = null,
    val confidence: Float = 0f,
    val signalFamilies: Set<SignalFamily> = emptySet(),
    val reason: String = "",
    val conflict: FeatureConflict? = null
)

data class FeatureProfile(
    val profileId: String,
    val packageName: String,
    val featureId: String,
    val schemaVersion: Int = 1,
    val appVersionCode: Long = 0L,
    val appVersionName: String = "",
    val primaryScreenSignature: String = "",
    val alternateScreenSignatures: List<String> = emptyList(),
    val structuralSignatures: List<String> = emptyList(),
    val stableViewIds: Set<String> = emptySet(),
    val stableTextTokens: Set<String> = emptySet(),
    val ancestorPaths: Set<String> = emptySet(),
    val negativeSignals: Set<String> = emptySet(),
    val conflictingFeatureIds: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastVerifiedAt: Long = System.currentTimeMillis(),
    val verificationCount: Int = 1,
    val status: ProfileStatus = ProfileStatus.UNVERIFIED
)

data class TransitionRecord(
    val packageName: String,
    val candidateFeatureId: String,
    val timestamp: Long,
    val entrySnapshot: ScreenSnapshot?,
    var targetSnapshot: ScreenSnapshot? = null,
    var verifiedObservations: Int = 0
)
