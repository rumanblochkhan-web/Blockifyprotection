package com.example.recoveryshield.protection.block.feature

/**
 * Detects when the current screen matches or strongly resembles another feature or the home screen,
 * vetoing false positives.
 */
object FeatureConflictResolver {

    const val AMBIGUITY_MARGIN = 0.20f

    /**
     * Resolves conflicts between evaluated candidates and known sibling features.
     * Returns a [FeatureConflict] if a veto condition is triggered, or null if the candidate is unambiguous.
     */
    fun resolveConflicts(
        targetCandidate: FeatureCandidate,
        allCandidates: List<FeatureCandidate>,
        snapshot: ScreenSnapshot
    ): FeatureConflict? {
        val targetSeed = FeatureSemanticSeedProvider.getSeed(snapshot.packageName, targetCandidate.featureId)

        // 1. Evaluate known sibling features from the seed to see if another feature is actually active
        for (siblingId in targetSeed.conflictingSiblingFeatureIds) {
            val siblingCandidate = allCandidates.firstOrNull { it.featureId == siblingId }
                ?: FeatureFingerprintMatcher.matchCandidate(snapshot, siblingId)

            // If a conflicting feature is actively selected or has a higher score, veto!
            if (siblingCandidate.activeNavSelected && !targetCandidate.activeNavSelected) {
                return FeatureConflict(
                    conflictingFeatureId = siblingId,
                    conflictScore = siblingCandidate.score,
                    reason = "Conflicting sibling feature '$siblingId' is actively selected on screen"
                )
            }

            if (siblingCandidate.score > targetCandidate.score) {
                return FeatureConflict(
                    conflictingFeatureId = siblingId,
                    conflictScore = siblingCandidate.score,
                    reason = "Conflicting feature '$siblingId' has higher confidence score (${siblingCandidate.score} vs ${targetCandidate.score})"
                )
            }

            // Ambiguity check between competing candidates
            if (siblingCandidate.score >= 0.50f && (targetCandidate.score - siblingCandidate.score) < AMBIGUITY_MARGIN) {
                return FeatureConflict(
                    conflictingFeatureId = siblingId,
                    conflictScore = siblingCandidate.score,
                    reason = "Ambiguity between '${targetCandidate.featureId}' (${targetCandidate.score}) and '$siblingId' (${siblingCandidate.score}) within margin $AMBIGUITY_MARGIN"
                )
            }
        }

        // 2. Ambiguity check against other enabled candidates in the same app
        for (other in allCandidates) {
            if (other.featureId == targetCandidate.featureId) continue
            if (other.score >= 0.50f && (targetCandidate.score - other.score) < AMBIGUITY_MARGIN) {
                return FeatureConflict(
                    conflictingFeatureId = other.featureId,
                    conflictScore = other.score,
                    reason = "Score difference between '${targetCandidate.featureId}' and '${other.featureId}' is below required ambiguity threshold"
                )
            }
        }

        return null
    }
}
