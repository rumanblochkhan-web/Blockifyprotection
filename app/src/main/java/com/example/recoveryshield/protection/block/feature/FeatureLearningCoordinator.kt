package com.example.recoveryshield.protection.block.feature

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Automatically learns or refreshes stable feature profiles from valid user navigation sequences
 * and multiple consistent observations without requiring any UI.
 */
class FeatureLearningCoordinator(
    private val context: Context,
    private val profileStore: FeatureProfileStore
) {

    private val activeTransitions = ConcurrentHashMap<String, TransitionRecord>()

    companion object {
        private const val TRANSITION_WINDOW_MS = 4000L
        private const val REQUIRED_VERIFIED_OBSERVATIONS = 2
    }

    /**
     * Records a candidate entry event (e.g. user clicked or navigated towards a candidate tab).
     */
    fun recordCandidateEntry(snapshot: ScreenSnapshot, candidateFeatureId: String) {
        val now = System.currentTimeMillis()
        val key = makeKey(snapshot.packageName, candidateFeatureId)
        activeTransitions[key] = TransitionRecord(
            packageName = snapshot.packageName,
            candidateFeatureId = candidateFeatureId,
            timestamp = now,
            entrySnapshot = snapshot
        )
    }

    /**
     * Validates a post-transition target screen snapshot against an active candidate entry.
     * Promotes the profile to VERIFIED if consistency requirements are satisfied.
     */
    fun validateTargetScreen(
        snapshot: ScreenSnapshot,
        candidate: FeatureCandidate,
        conflict: FeatureConflict?
    ) {
        if (conflict != null) {
            // Cancel transition if conflict vetoed
            activeTransitions.remove(makeKey(snapshot.packageName, candidate.featureId))
            return
        }

        val key = makeKey(snapshot.packageName, candidate.featureId)
        val transition = activeTransitions[key] ?: return
        val now = System.currentTimeMillis()

        if (now - transition.timestamp > TRANSITION_WINDOW_MS) {
            activeTransitions.remove(key)
            return
        }

        if (candidate.candidateType == CandidateType.TARGET_SCREEN && candidate.score >= FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD) {
            transition.verifiedObservations++
            transition.targetSnapshot = snapshot

            if (transition.verifiedObservations >= REQUIRED_VERIFIED_OBSERVATIONS) {
                // Promote to verified profile!
                val shape = FeatureFingerprintBuilder.buildStructuralSignature(snapshot)
                val existing = profileStore.getProfile(snapshot.packageName, candidate.featureId)
                val appVersionName = profileStore.getAppVersionName(snapshot.packageName)

                val updatedProfile = if (existing != null) {
                    existing.copy(
                        primaryScreenSignature = shape,
                        alternateScreenSignatures = (existing.alternateScreenSignatures + shape).distinct(),
                        stableViewIds = (existing.stableViewIds + snapshot.viewIdSet).distinct().toSet(),
                        stableTextTokens = (existing.stableTextTokens + snapshot.activeTabSelectedTokens).distinct().toSet(),
                        lastVerifiedAt = now,
                        verificationCount = existing.verificationCount + 1,
                        status = ProfileStatus.VERIFIED
                    )
                } else {
                    FeatureProfile(
                        profileId = "prof_${System.currentTimeMillis()}",
                        packageName = snapshot.packageName,
                        featureId = candidate.featureId,
                        appVersionName = appVersionName,
                        primaryScreenSignature = shape,
                        alternateScreenSignatures = listOf(shape),
                        stableViewIds = snapshot.viewIdSet,
                        stableTextTokens = snapshot.activeTabSelectedTokens,
                        createdAt = now,
                        lastVerifiedAt = now,
                        verificationCount = 1,
                        status = ProfileStatus.VERIFIED
                    )
                }

                profileStore.saveProfile(updatedProfile)
                activeTransitions.remove(key)
            }
        }
    }

    private fun makeKey(packageName: String, featureId: String): String {
        return "${packageName.lowercase()}:${featureId.lowercase()}"
    }
}
