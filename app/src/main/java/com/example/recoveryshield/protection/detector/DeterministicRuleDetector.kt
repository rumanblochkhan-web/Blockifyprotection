package com.example.recoveryshield.protection.detector

import com.example.recoveryshield.domain.model.ContextCategory
import com.example.recoveryshield.domain.model.DetectionDecision
import com.example.recoveryshield.domain.model.DetectionPolicy
import com.example.recoveryshield.domain.model.DetectionResult
import com.example.recoveryshield.domain.model.DetectionSignal
import com.example.recoveryshield.domain.model.NormalizedUiEvent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

/**
 * Deterministic, privacy-preserving multi-signal local detection engine.
 * Optimized for minimum latency and zero dynamic regex compilation in hot path.
 */
class DeterministicRuleDetector(
    private val policy: DetectionPolicy = DetectionPolicy(),
    private val allowlistManager: AllowlistManager = AllowlistManager()
) : LocalClassifier {

    companion object {
        // Core explicit word boundary patterns (compiled once for high performance)
        private val EXPLICIT_PATTERNS = listOf(
            Pattern.compile(
                "\\b(?:test[ _-]?trigger|porn|porno|pornography|hardcore|nsfw|xxx|sex|sax|xvideo|xvideos|pornhub|xnxx|xhamster|redtube|youporn|brazzers|spankbang|eporner|beeg|chaturbate|onlyfans|stripchat|erotic|hentai|sexually\\s+explicit)\\b",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "\\b(?:blowjob|handjob|orgasm|cumshot|gangbang|threesome|masturbat(?:e|ion|ing)|creampie|deepthroat|dildo|pussy|vagina|penis|boobs|tits|big\\s+ass|fat\\s+ass|sexy\\s+ass|booty|cleavage|horny|milf|erogenous|sexy\\s+hot|sex\\s+chat)\\b",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "\\b(?:naked\\s+girls|nude\\s+women|sex\\s+video|watch\\s+porn|free\\s+sex|hot\\s+sex|hardcore\\s+sex|live\\s+sex|leaked\\s+nudes|nangi\\s+ladki|nangi\\s+video|sexy\\s+video|sax\\s+video|gandi\\s+video|sexy\\s+girl|hot\\s+bhabhi|chut|gand|lund|ling|sexy\\s+dance|hot\\s+dance|romantic\\s+kiss|hot\\s+kiss|sensual\\s+touch|hot\\s+romance|romantic\\s+sex)\\b",
                Pattern.CASE_INSENSITIVE
            )
        )

        // Ambiguous single terms that require high signal density or corroboration
        private val AMBIGUOUS_PATTERNS = listOf(
            Pattern.compile("\\b(?:nude|nudity|erotica|sensual|intercourse|fetish|strip|sexy|hot\\s+romance|romantic\\s+sex|lust|dirty\\s+talk|erotic\\s+story|erotic\\s+video|intimate\\s+scenes|kissing\\s+hot|romance|kissing|lingerie|underwear|bikini|makeout|bedroom\\s+scene)\\b", Pattern.CASE_INSENSITIVE)
        )

        private val SAFE_COMPOUND_PHRASES = listOf(
            "porn protection", "porn blocker", "porn shield", "recovery shield",
            "recoveryshield", "porn addiction", "quitting porn", "porn recovery",
            "stop porn", "anti porn", "free from porn", "pornography addiction"
        )

        private val SAFE_COMPOUND_REGEXES = SAFE_COMPOUND_PHRASES.map {
            it.toRegex(RegexOption.IGNORE_CASE)
        }

        private val SINGLE_LETTER_SPACING_REGEX = Regex("\\b[a-zA-Z](?:\\s+[a-zA-Z])+\\b")
        private val PUNCTUATED_REGEX = Regex("\\b[a-zA-Z](?:[._\\-]+[a-zA-Z])+\\b")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val PUNCTUATION_STRIP_REGEX = Regex("[._\\-]+")
    }

    // Ring buffer of recent trigger timestamps for density calculation
    private val recentTriggers = CopyOnWriteArrayList<Long>()

    override fun classify(event: NormalizedUiEvent): DetectionResult {
        val now = System.currentTimeMillis()
        pruneOldTriggers(now)

        val combinedTokens = (event.textFragments + event.contentDescriptions).joinToString(" ")
        if (combinedTokens.isBlank()) {
            return DetectionResult(DetectionDecision.NO_ACTION, 0f, emptyList(), now, "Empty UI event")
        }

        // 1. Anti-Tamper Check (Settings / Package Installer / Play Store)
        if (isTamperOrDeactivationAttempt(event)) {
            com.example.recoveryshield.util.SafeLog.i("BLOCKIFY_TRIGGER", "DeterministicRuleDetector: Anti-tamper triggered in pkg=${event.packageName}")
            return DetectionResult(
                decision = DetectionDecision.HIGH_CONFIDENCE_TRIGGER,
                confidence = 0.99f,
                signals = listOf(
                    DetectionSignal.Contextual(ContextCategory.SYSTEM_SETTINGS, 1.0f)
                ),
                timestampMillis = now,
                diagnosticSafeSummary = "Anti-tamper: App uninstall or accessibility deactivation attempt detected"
            )
        }

        // Fast path: If package is in safe context (ChatGPT, WhatsApp, Camera, Gallery, etc.)
        // and it's not a settings tamper attempt, immediately suppress!
        val isSafePackage = allowlistManager.isPackageSafeContext(event.packageName)
        if (isSafePackage) {
            return DetectionResult(
                decision = DetectionDecision.SUPPRESS_FOR_CONTEXT,
                confidence = 0f,
                signals = listOf(DetectionSignal.Contextual(ContextCategory.SYSTEM_SETTINGS, 0f)),
                timestampMillis = now,
                diagnosticSafeSummary = "Suppressed: safe package context"
            )
        }

        // 2. Lexical Normalization & Matching for Explicit Pornographic Content
        val rawFragments = event.textFragments + event.contentDescriptions
        val deobfuscated = rawFragments.joinToString(" ") { deobfuscate(it) }

        // Mask out safe recovery/protection phrases using precompiled regexes
        var cleanedText = deobfuscated
        for (safeRegex in SAFE_COMPOUND_REGEXES) {
            if (cleanedText.contains("porn", ignoreCase = true) || cleanedText.contains("shield", ignoreCase = true)) {
                cleanedText = cleanedText.replace(safeRegex, "[safe_recovery_token]")
            }
        }

        var explicitMatchCount = 0
        var ambiguousMatchCount = 0

        for (pattern in EXPLICIT_PATTERNS) {
            val matcher = pattern.matcher(cleanedText)
            while (matcher.find()) {
                explicitMatchCount++
            }
            // Short-circuit: if explicit matches are already found, we can proceed directly
            if (explicitMatchCount >= 2) break
        }

        // If NO explicit keywords are found, check safe packages and educational/recovery suppressions
        if (explicitMatchCount == 0) {
            val isSafePackage = allowlistManager.isPackageSafeContext(event.packageName)
            if (isSafePackage) {
                return DetectionResult(
                    decision = DetectionDecision.SUPPRESS_FOR_CONTEXT,
                    confidence = 0f,
                    signals = listOf(DetectionSignal.Contextual(ContextCategory.SYSTEM_SETTINGS, 0f)),
                    timestampMillis = now,
                    diagnosticSafeSummary = "Suppressed: safe system interaction"
                )
            }

            val hasRecoveryPhrase = allowlistManager.isTokenSuppressed(combinedTokens)
            if (hasRecoveryPhrase) {
                val contextualSignal = DetectionSignal.Contextual(ContextCategory.RECOVERY_OR_EDUCATIONAL, policy.safeContextDiscount)
                return DetectionResult(
                    decision = DetectionDecision.SUPPRESS_FOR_CONTEXT,
                    confidence = 0f,
                    signals = listOf(contextualSignal),
                    timestampMillis = now,
                    diagnosticSafeSummary = "Suppressed: educational or recovery context"
                )
            }
        } else {
            com.example.recoveryshield.util.SafeLog.i("BLOCKIFY_TRIGGER", "DeterministicRuleDetector: Found $explicitMatchCount explicit matches in pkg=${event.packageName}")
        }

        if (explicitMatchCount == 0) {
            for (pattern in AMBIGUOUS_PATTERNS) {
                val matcher = pattern.matcher(cleanedText)
                while (matcher.find()) {
                    ambiguousMatchCount++
                }
            }
        }

        // Calculate lexical base confidence
        var baseConfidence = when {
            explicitMatchCount >= 1 -> 0.98f
            ambiguousMatchCount >= 2 -> 0.88f
            ambiguousMatchCount == 1 -> 0.60f
            else -> 0f
        }

        if (baseConfidence <= 0f) {
            return DetectionResult(
                decision = DetectionDecision.NO_ACTION,
                confidence = 0f,
                signals = emptyList(),
                timestampMillis = now,
                diagnosticSafeSummary = "Clean content"
            )
        }

        // 5. Density Signal Layer (sliding window)
        val recentCount = recentTriggers.size
        var densityBoost = 0f
        if (recentCount > 0 && baseConfidence >= 0.4f) {
            densityBoost = (recentCount * policy.densityBoostStep).coerceAtMost(0.20f)
        }

        val totalConfidence = (baseConfidence + densityBoost).coerceIn(0f, 1.0f)

        // Record hit timestamp if this is a notable signal
        if (baseConfidence >= 0.5f) {
            recentTriggers.add(now)
        }

        val signals = listOf(
            DetectionSignal.Lexical(
                confidence = baseConfidence,
                matchCount = explicitMatchCount + ambiguousMatchCount,
                matchedCategories = listOf(if (explicitMatchCount > 0) "explicit" else "ambiguous")
            ),
            DetectionSignal.Contextual(ContextCategory.GENERAL_CONTENT, 1.0f),
            DetectionSignal.RepetitionDensity(recentCount, densityBoost)
        )

        val decision = when {
            totalConfidence >= policy.highConfidenceThreshold -> DetectionDecision.HIGH_CONFIDENCE_TRIGGER
            totalConfidence >= policy.possibleTriggerThreshold -> DetectionDecision.POSSIBLE_TRIGGER
            else -> DetectionDecision.NO_ACTION
        }

        val safeSummary = "Decision: $decision (Conf: %.2f, Explicit: $explicitMatchCount, Ambiguous: $ambiguousMatchCount, DensityHits: $recentCount)".format(totalConfidence)

        return DetectionResult(
            decision = decision,
            confidence = totalConfidence,
            signals = signals,
            timestampMillis = now,
            diagnosticSafeSummary = safeSummary
        )
    }

    /**
     * Detects if the user in Settings, Package Installer, Play Store, or Device Managers
     * is actively on a screen to uninstall, force stop, clear data, or turn off accessibility
     * for Blockify.
     */
    fun isTamperOrDeactivationAttempt(event: NormalizedUiEvent): Boolean {
        val combinedLower = (event.textFragments + event.contentDescriptions + event.viewIds).joinToString(" ").lowercase()
        val pkg = event.packageName.lowercase()

        // Explicitly exclude Launchers, SystemUI (Recent Apps / Overview tabs), Keyboards, and Home screens
        if (pkg.contains("launcher") ||
            pkg.contains("systemui") ||
            pkg.contains("quickstep") ||
            pkg.contains("recents") ||
            pkg.contains("inputmethod") ||
            pkg.contains("keyboard") ||
            pkg.contains("nexuslauncher")
        ) {
            return false
        }

        // Never flag inside our own application (e.g. Emergency Stop, Home, Settings)
        if (pkg == "com.blockifyroman.app" || pkg == "com.example.recoveryshield" || pkg == "com.example") {
            return false
        }

        val isSystemSettingsOrInstaller = pkg.contains("settings") ||
                pkg.contains("packageinstaller") ||
                pkg.contains("package_installer") ||
                pkg.contains("installer") ||
                pkg.contains("vending") ||
                pkg.contains("securitycenter") ||
                pkg.contains("safecenter") ||
                pkg.contains("security") ||
                pkg.contains("permissioncontroller")

        if (!isSystemSettingsOrInstaller) return false

        // Check if the screen context references our app name or package
        val referencesOurApp = combinedLower.contains("blockify") ||
                combinedLower.contains("blockify protection") ||
                combinedLower.contains("porn protection") ||
                combinedLower.contains("recoveryshield") ||
                combinedLower.contains("recovery shield") ||
                combinedLower.contains("my application") ||
                combinedLower.contains("com.aistudio") ||
                combinedLower.contains("com.example") ||
                combinedLower.contains("com.blockifyroman.app")

        // 1. Any Package Installer uninstall prompt (e.g. "Do you want to uninstall this app?", "Uninstall Blockify?")
        val isPackageInstallerPrompt = (pkg.contains("packageinstaller") || pkg.contains("installer")) && (
                referencesOurApp ||
                combinedLower.contains("uninstall") ||
                combinedLower.contains("do you want to uninstall") ||
                combinedLower.contains("want to uninstall")
        )

        // 2. Settings App Info page for Blockify (where Uninstall, Force stop, Clear storage, Clear data, Clear cache, or Permissions are shown)
        val isOurAppInfoPage = (pkg.contains("settings") || pkg.contains("securitycenter") || pkg.contains("safecenter") || pkg.contains("security")) &&
                referencesOurApp && (
                combinedLower.contains("uninstall") ||
                combinedLower.contains("force stop") ||
                combinedLower.contains("clear data") ||
                combinedLower.contains("clear storage") ||
                combinedLower.contains("clear cache") ||
                combinedLower.contains("storage & cache") ||
                combinedLower.contains("storage and cache") ||
                combinedLower.contains("disable") ||
                combinedLower.contains("app info") ||
                combinedLower.contains("app details") ||
                combinedLower.contains("modify system settings")
        )

        // 3. Accessibility service settings screen specifically for Blockify
        val isOurAccessibilityToggleScreen = pkg.contains("settings") && referencesOurApp && (
                combinedLower.contains("use service") ||
                combinedLower.contains("stop service") ||
                combinedLower.contains("turn off") ||
                combinedLower.contains("turn off service") ||
                combinedLower.contains("disable service") ||
                combinedLower.contains("allow full control") ||
                (combinedLower.contains("accessibility") && (combinedLower.contains("shortcut") || combinedLower.contains("turn off") || combinedLower.contains("stop") || combinedLower.contains("use service"))) ||
                combinedLower.contains("allow restricted settings")
        )

        // 4. Play Store page for Blockify with Uninstall option
        val isOurPlayStoreUninstallScreen = pkg.contains("vending") &&
                referencesOurApp &&
                (combinedLower.contains("uninstall") || combinedLower.contains("open"))

        // 5. Device Admin deactivation screen for Blockify
        val isDeviceAdminDeactivation = (pkg.contains("settings") || pkg.contains("security")) &&
                referencesOurApp && (
                combinedLower.contains("device admin") ||
                combinedLower.contains("deactivate") ||
                combinedLower.contains("remove device admin")
        )

        return isPackageInstallerPrompt || isOurAppInfoPage || isOurAccessibilityToggleScreen || isOurPlayStoreUninstallScreen || isDeviceAdminDeactivation
    }

    override fun resetTemporalState() {
        recentTriggers.clear()
    }

    private fun pruneOldTriggers(currentTime: Long) {
        val cutoff = currentTime - policy.densityBoostWindowMillis
        recentTriggers.removeIf { it < cutoff }
    }

    /**
     * Replaces common leetspeak substitutions and strips spacing obfuscation
     * using static precompiled regexes (zero allocation in hot path).
     */
    fun deobfuscate(input: String): String {
        var hasLeet = false
        for (i in 0 until input.length) {
            val c = input[i]
            if (c == '@' || c == '0' || c == '1' || c == '$' || c == '3' || c == '5' || c == '+') {
                hasLeet = true
                break
            }
        }

        val leetReplaced = if (hasLeet) {
            input
                .replace('@', 'a')
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('$', 's')
                .replace('3', 'e')
                .replace('5', 's')
                .replace('+', 't')
        } else {
            input
        }

        // Compact spaced-out single letters: "p o r n" -> "porn"
        val compacted = SINGLE_LETTER_SPACING_REGEX.replace(leetReplaced) { matchResult ->
            matchResult.value.replace(WHITESPACE_REGEX, "")
        }

        // Also normalize punctuation/hyphen between single characters: "v-i-d-e-o" -> "video", "p.o.r.n" -> "porn"
        return PUNCTUATED_REGEX.replace(compacted) { matchResult ->
            matchResult.value.replace(PUNCTUATION_STRIP_REGEX, "")
        }
    }
}
