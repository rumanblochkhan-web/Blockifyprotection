package com.example.recoveryshield.protection.block.feature

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.blockify.ui.block.BlockFeature
import com.blockify.ui.block.BlockifyFeatureCatalog
import com.blockify.ui.block.ProtectionRule

/**
 * Universal feature identity and screen-context engine.
 * Coordinates rule lookup, screen snapshot creation, candidate identification, profile matching,
 * conflict validation, confidence decisions, and returns authoritative enforcement results.
 */
class FeatureBlockEngine private constructor(private val context: Context) {

    private val profileStore = FeatureProfileStore.getInstance(context)
    private val learningCoordinator = FeatureLearningCoordinator(context, profileStore)

    companion object {
        private const val TAG = "FeatureBlockEngine"

        @Volatile
        private var INSTANCE: FeatureBlockEngine? = null

        fun getInstance(context: Context): FeatureBlockEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeatureBlockEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Authoritative evaluation method called from the AccessibilityService event pipeline.
     */
    fun evaluate(
        rule: ProtectionRule,
        packageName: String,
        textFragments: List<String>,
        contentDescriptions: List<String>,
        className: String,
        viewIds: List<String>,
        eventType: Int,
        eventText: String,
        eventContentDescription: String,
        eventViewId: String,
        rootNode: AccessibilityNodeInfo?,
        eventSource: AccessibilityNodeInfo?
    ): Pair<ProtectionRule, BlockFeature>? {
        // 1. EXACT PACKAGE SCOPE VALIDATION
        if (rule.appPackage != packageName) {
            return null
        }

        // 2. FEATURE SCOPE VALIDATION (A whole-app rule is handled separately)
        if (rule.blockEntireApp) {
            return null
        }

        val enabledFeatures = rule.features.filter { it.enabled }
        if (enabledFeatures.isEmpty()) {
            return null
        }

        val enabledFeatureIds = enabledFeatures.map { it.featureId }.toSet()

        // 2b. WHATSAPP IN-CHAT CAMERA & MEDIA EXEMPTION (Never block taking photos in chats)
        if (packageName.contains("whatsapp")) {
            val lowerClass = className.lowercase()
            val isWhatsAppChatOrCamera = lowerClass.contains("camera") ||
                    lowerClass.contains("mediacomposer") ||
                    lowerClass.contains("conversation") ||
                    lowerClass.contains("chat") ||
                    lowerClass.contains("picker") ||
                    lowerClass.contains("gallery") ||
                    viewIds.any {
                        it.contains("camera", ignoreCase = true) ||
                        it.contains("shutter", ignoreCase = true) ||
                        it.contains("capture", ignoreCase = true) ||
                        it.contains("viewfinder", ignoreCase = true) ||
                        it.contains("flash", ignoreCase = true)
                    } ||
                    eventText.contains("camera", ignoreCase = true) ||
                    eventContentDescription.contains("camera", ignoreCase = true)
            
            if (isWhatsAppChatOrCamera) {
                val isStatusViewer = lowerClass.contains("statusplaybackactivity") || lowerClass.contains("statuslistactivity")
                if (!isStatusViewer) {
                    return null
                }
            }
        }

        // 3. CAPTURE IMMUTABLE SCREEN SNAPSHOT
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = packageName,
            eventType = eventType,
            className = className,
            eventText = eventText,
            eventContentDescription = eventContentDescription,
            eventViewId = eventViewId,
            rootNode = rootNode,
            eventSource = eventSource,
            fallbackTextFragments = textFragments,
            fallbackContentDescriptions = contentDescriptions,
            fallbackViewIds = viewIds
        )

        // 4. RESOLVE CATALOG METADATA FOR DISPLAY MAPPING
        val catalogFeatures = BlockifyFeatureCatalog.candidateFeatures(rule.appPackage, rule.appName)

        // 5. EVALUATE ALL CANDIDATES
        val evaluatedCandidates = mutableListOf<FeatureCandidate>()
        for (fId in enabledFeatureIds) {
            val profile = profileStore.getProfile(packageName, fId)
            val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, fId, profile)
            evaluatedCandidates.add(candidate)
        }

        // 6. RECORD ENTRY EVENTS FOR LEARNING IF USER CLICKED A CANDIDATE TAB
        if (snapshot.isClick) {
            for (cand in evaluatedCandidates) {
                if (cand.score >= 0.20f) {
                    learningCoordinator.recordCandidateEntry(snapshot, cand.featureId)
                }
            }
        }

        // 7. FIND WINNING CANDIDATE
        val topCandidate = evaluatedCandidates.maxByOrNull { it.score } ?: return null

        // 8. RESOLVE POTENTIAL CONFLICTS WITH SIBLING OR HOME SURFACES
        val conflict = FeatureConflictResolver.resolveConflicts(topCandidate, evaluatedCandidates, snapshot)

        // Process learning validation
        learningCoordinator.validateTargetScreen(snapshot, topCandidate, conflict)

        if (conflict != null) {
            // Hard conflict veto: ALLOW
            return null
        }

        // 9. ENFORCE STRICT MULTI-SIGNAL THRESHOLDS
        if (topCandidate.candidateType != CandidateType.TARGET_SCREEN) {
            // Entry-only or unselected button: ALLOW
            return null
        }

        if (topCandidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD) {
            // Insufficient confidence: ALLOW
            return null
        }

        if (topCandidate.signalFamilies.size < FeatureFingerprintMatcher.MIN_REQUIRED_SIGNAL_FAMILIES) {
            // Insufficient independent signal families: ALLOW
            return null
        }

        // 10. RESOLVE CORRESPONDING BlockFeature FOR INTERVENTION DISPLAY
        val matchedCatalogFeature = catalogFeatures.firstOrNull { it.id.equals(topCandidate.featureId, ignoreCase = true) }
            ?: BlockFeature(
                id = topCandidate.featureId,
                title = topCandidate.featureId.replaceFirstChar { it.uppercase() },
                description = "Block feature",
                detectionSignals = listOf(topCandidate.featureId)
            )

        return Pair(rule, matchedCatalogFeature)
    }

    /**
     * Cleans up stored profiles when a rule is deleted.
     */
    fun onRuleDeleted(rule: ProtectionRule) {
        profileStore.removeProfilesForPackage(rule.appPackage)
    }
}
