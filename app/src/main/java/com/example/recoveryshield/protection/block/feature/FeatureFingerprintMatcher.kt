package com.example.recoveryshield.protection.block.feature

/**
 * Compares runtime [ScreenSnapshot] data against persisted [FeatureProfile] or candidate seeds.
 * Evaluates multi-signal weighted evidence with strict hard constraints and false-positive vetoes.
 */
object FeatureFingerprintMatcher {

    const val MIN_CONFIDENCE_THRESHOLD = 0.70f
    const val MIN_REQUIRED_SIGNAL_FAMILIES = 2

    /**
     * Evaluates a single candidate feature against the current screen snapshot.
     */
    fun matchCandidate(
        snapshot: ScreenSnapshot,
        featureId: String,
        profile: FeatureProfile? = null
    ): FeatureCandidate {
        val seed = FeatureSemanticSeedProvider.getSeed(snapshot.packageName, featureId)
        val matchedSignals = mutableListOf<FeatureSignal>()
        val signalFamilies = mutableSetOf<SignalFamily>()

        val lowerClass = snapshot.className.lowercase()
        val allViewIds = snapshot.viewIdSet
        val allTextTokens = snapshot.textTokenSet
        val allDescTokens = snapshot.contentDescTokenSet
        val activeTabTokens = snapshot.activeTabSelectedTokens

        var targetContainerConfirmed = false
        var activeNavSelected = false

        // -------------------------------------------------------------
        // 1. STRONG SIGNAL FAMILY: Compound view IDs & Structural Container
        // -------------------------------------------------------------
        val hasStrongViewId = allViewIds.any { vId ->
            seed.targetViewIdKeywords.any { kw -> vId.contains(kw) } ||
                    (profile != null && profile.stableViewIds.contains(vId))
        }

        val hasStrongStructuralClass = seed.targetClassKeywords.any { kw -> lowerClass.contains(kw) }

        // Check if navigation node is actively selected/checked on screen
        val hasSelectedNavTab = activeTabTokens.any { token ->
            seed.navigationTokens.contains(token) || (profile != null && profile.stableTextTokens.contains(token))
        }
        if (hasSelectedNavTab) {
            activeNavSelected = true
        }

        // Active target container confirmation
        if (hasStrongViewId && (hasStrongStructuralClass || hasSelectedNavTab || snapshot.nodes.any { it.isScrollable && it.depth <= 8 })) {
            targetContainerConfirmed = true
            matchedSignals.add(
                FeatureSignal(
                    family = SignalFamily.STRONG,
                    name = "TARGET_CONTAINER_STRUCTURE",
                    weight = 0.45f,
                    matched = true,
                    description = "Confirmed structural target container for $featureId"
                )
            )
            signalFamilies.add(SignalFamily.STRONG)
        } else if (hasStrongViewId) {
            matchedSignals.add(
                FeatureSignal(
                    family = SignalFamily.STRONG,
                    name = "COMPOUND_VIEW_ID",
                    weight = 0.35f,
                    matched = true,
                    description = "Matched specific view ID combination"
                )
            )
            signalFamilies.add(SignalFamily.STRONG)
        }

        if (activeNavSelected) {
            matchedSignals.add(
                FeatureSignal(
                    family = SignalFamily.STRONG,
                    name = "ACTIVE_NAV_SELECTED",
                    weight = 0.30f,
                    matched = true,
                    description = "Matched active selected tab/navigation state"
                )
            )
            signalFamilies.add(SignalFamily.STRONG)
        }

        // -------------------------------------------------------------
        // 2. MEDIUM SIGNAL FAMILY: Class names, Geometry & Content Descriptions
        // -------------------------------------------------------------
        if (hasStrongStructuralClass) {
            matchedSignals.add(
                FeatureSignal(
                    family = SignalFamily.MEDIUM,
                    name = "CONTAINER_CLASS_PATTERN",
                    weight = 0.25f,
                    matched = true,
                    description = "Matched target screen class name: ${snapshot.className}"
                )
            )
            signalFamilies.add(SignalFamily.MEDIUM)
        }

        val matchingDescTokens = allDescTokens.filter { seed.targetContainerKeywords.contains(it) }
        if (matchingDescTokens.size >= 2) {
            matchedSignals.add(
                FeatureSignal(
                    family = SignalFamily.MEDIUM,
                    name = "MULTI_CONTENT_DESCRIPTION",
                    weight = 0.20f,
                    matched = true,
                    description = "Matched multiple contextual descriptions: $matchingDescTokens"
                )
            )
            signalFamilies.add(SignalFamily.MEDIUM)
        }

        // -------------------------------------------------------------
        // 3. WEAK SIGNAL FAMILY: Generic Text Tokens / Unselected Buttons
        // -------------------------------------------------------------
        val matchingTextTokens = allTextTokens.filter { seed.navigationTokens.contains(it) || seed.targetContainerKeywords.contains(it) }
        if (matchingTextTokens.isNotEmpty()) {
            matchedSignals.add(
                FeatureSignal(
                    family = SignalFamily.WEAK,
                    name = "WEAK_TEXT_OR_BUTTON",
                    weight = 0.05f,
                    matched = true,
                    description = "Observed text/button token: $matchingTextTokens"
                )
            )
            signalFamilies.add(SignalFamily.WEAK)
        }

        // -------------------------------------------------------------
        // 4. VERIFIED PROFILE MATCHING (If existing profile present)
        // -------------------------------------------------------------
        if (profile != null && profile.status == ProfileStatus.VERIFIED) {
            val currentShape = FeatureFingerprintBuilder.buildStructuralSignature(snapshot)
            if (profile.primaryScreenSignature == currentShape || profile.alternateScreenSignatures.contains(currentShape)) {
                matchedSignals.add(
                    FeatureSignal(
                        family = SignalFamily.STRONG,
                        name = "VERIFIED_STRUCTURAL_FINGERPRINT",
                        weight = 0.35f,
                        matched = true,
                        description = "Exact match with verified profile structural fingerprint"
                    )
                )
                signalFamilies.add(SignalFamily.STRONG)
            }
        }

        // Calculate total score
        var totalScore = matchedSignals.sumOf { it.weight.toDouble() }.toFloat().coerceIn(0f, 1f)

        // -------------------------------------------------------------
        // HARD CONSTRAINTS & FALSE-POSITIVE VETOES:
        // -------------------------------------------------------------
        // Rule 1: A visible navigation label or button alone is NOT proof of the target screen.
        // If only WEAK signals matched, cap score to 0.10f (ALLOW).
        if (signalFamilies.size == 1 && signalFamilies.contains(SignalFamily.WEAK)) {
            totalScore = 0.10f
        }

        // Rule 2: Single class-name match or single description match without structural/nav confirmation cannot block.
        if (signalFamilies.none { it == SignalFamily.STRONG }) {
            totalScore = totalScore.coerceAtMost(0.40f)
        }

        val candidateType = if (targetContainerConfirmed || (activeNavSelected && totalScore >= 0.55f)) {
            CandidateType.TARGET_SCREEN
        } else {
            CandidateType.ENTRY
        }

        return FeatureCandidate(
            featureId = featureId,
            score = totalScore,
            candidateType = candidateType,
            matchedSignals = matchedSignals,
            signalFamilies = signalFamilies,
            targetContainerConfirmed = targetContainerConfirmed,
            activeNavSelected = activeNavSelected
        )
    }
}
