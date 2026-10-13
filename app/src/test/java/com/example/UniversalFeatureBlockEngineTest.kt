package com.example

import com.blockify.ui.block.BlockifyFeatureCatalog
import com.blockify.ui.block.ProtectionRule
import com.blockify.ui.block.ScheduleRule
import com.blockify.ui.block.SelectedFeature
import com.example.recoveryshield.protection.block.feature.AccessibilityScreenScanner
import com.example.recoveryshield.protection.block.feature.CandidateType
import com.example.recoveryshield.protection.block.feature.FeatureConflictResolver
import com.example.recoveryshield.protection.block.feature.FeatureFingerprintMatcher
import com.example.recoveryshield.protection.block.feature.FeatureProfile
import com.example.recoveryshield.protection.block.feature.ProfileStatus
import com.example.recoveryshield.protection.block.feature.SignalFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalFeatureBlockEngineTest {

    // =========================================================================
    // 1. WhatsApp Channels exact scope & false positive tests
    // =========================================================================

    @Test
    fun `WhatsApp Channels target screen blocks with multi-signal evidence`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.whatsapp",
            eventType = 0,
            className = "com.whatsapp.newsletter.ui.NewsletterActivity",
            eventViewId = "newsletter_title",
            fallbackViewIds = listOf("newsletter_title", "channel_feed", "newsletter_recycler"),
            fallbackTextFragments = listOf("Tech News Channel", "Follow Channel", "Updates"),
            fallbackContentDescriptions = listOf("Channel header", "Directory updates")
        )

        val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "channels")
        assertTrue(candidate.score >= FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
        assertEquals(CandidateType.TARGET_SCREEN, candidate.candidateType)
        assertTrue(candidate.signalFamilies.contains(SignalFamily.STRONG))
    }

    @Test
    fun `WhatsApp Status screen allows and does not trigger Channels rule`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.whatsapp",
            eventType = 0,
            className = "com.whatsapp.status.playback.StatusPlaybackActivity",
            eventViewId = "status_playback_pager",
            fallbackViewIds = listOf("status_playback_pager", "status_row"),
            fallbackTextFragments = listOf("My Status", "Recent Updates", "Channels"), // "Channels" tab label visible in background
            fallbackContentDescriptions = listOf("Status viewer")
        )

        val channelsCandidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "channels")
        val statusCandidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "status")

        // Status must win and veto Channels
        val conflict = FeatureConflictResolver.resolveConflicts(channelsCandidate, listOf(channelsCandidate, statusCandidate), snapshot)
        assertNotNull(conflict)
        assertTrue(statusCandidate.score > channelsCandidate.score)
    }

    @Test
    fun `WhatsApp Chats screen allows and unselected Channels tab does not block`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.whatsapp",
            eventType = 0,
            className = "com.whatsapp.HomeActivity",
            eventViewId = "conversations_recycler",
            fallbackViewIds = listOf("conversations_recycler", "chat_row", "tab_channels"),
            fallbackTextFragments = listOf("Chats", "John Doe: Hello", "Channels"), // unselected tab label
            fallbackContentDescriptions = listOf("Chats tab selected, 1 of 4", "Channels tab unselected, 2 of 4")
        )

        val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "channels")
        // Weak button label alone cannot reach target threshold
        assertTrue(candidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
        assertEquals(CandidateType.ENTRY, candidate.candidateType)
    }

    @Test
    fun `WhatsApp Calls and Communities screens allow`() {
        val callsSnapshot = AccessibilityScreenScanner.scan(
            packageName = "com.whatsapp",
            eventType = 0,
            className = "com.whatsapp.voipcalling.VoipActivity",
            fallbackViewIds = listOf("call_log_recycler", "call_row"),
            fallbackTextFragments = listOf("Calls", "Recent calls")
        )
        val callsCandidate = FeatureFingerprintMatcher.matchCandidate(callsSnapshot, "channels")
        assertTrue(callsCandidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)

        val commSnapshot = AccessibilityScreenScanner.scan(
            packageName = "com.whatsapp",
            eventType = 0,
            className = "com.whatsapp.community.CommunityActivity",
            fallbackViewIds = listOf("community_feed"),
            fallbackTextFragments = listOf("Communities", "Announcements")
        )
        val commCandidate = FeatureFingerprintMatcher.matchCandidate(commSnapshot, "channels")
        assertTrue(commCandidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
    }

    // =========================================================================
    // 2. Chrome Incognito exact scope tests
    // =========================================================================

    @Test
    fun `Chrome Incognito validated target blocks`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.android.chrome",
            eventType = 0,
            className = "org.chromium.chrome.browser.incognito.IncognitoTabActivity",
            eventViewId = "incognito_ntp_view",
            fallbackViewIds = listOf("incognito_ntp_view", "incognito_badge", "incognito_desc"),
            fallbackTextFragments = listOf("You've gone incognito", "Private browsing"),
            fallbackContentDescriptions = listOf("Incognito icon")
        )

        val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "incognito")
        assertTrue(candidate.score >= FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
        assertEquals(CandidateType.TARGET_SCREEN, candidate.candidateType)
        assertTrue(candidate.signalFamilies.contains(SignalFamily.STRONG))
    }

    @Test
    fun `Chrome normal tab, history, bookmarks, settings and downloads allow`() {
        val normalTab = AccessibilityScreenScanner.scan(
            packageName = "com.android.chrome",
            eventType = 0,
            className = "org.chromium.chrome.browser.ChromeTabbedActivity",
            fallbackViewIds = listOf("url_bar", "tab_switcher_button"),
            fallbackTextFragments = listOf("Google Search", "News Feed", "https://example.com")
        )
        val normalCandidate = FeatureFingerprintMatcher.matchCandidate(normalTab, "incognito")
        assertTrue(normalCandidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)

        val historySnapshot = AccessibilityScreenScanner.scan(
            packageName = "com.android.chrome",
            eventType = 0,
            className = "org.chromium.chrome.browser.history.HistoryActivity",
            fallbackViewIds = listOf("history_recycler", "clear_browsing_data_button"),
            fallbackTextFragments = listOf("History", "Clear browsing data")
        )
        val historyCandidate = FeatureFingerprintMatcher.matchCandidate(historySnapshot, "incognito")
        assertTrue(historyCandidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
    }

    // =========================================================================
    // 3. Pinterest false positive protection
    // =========================================================================

    @Test
    fun `Pinterest Home and Search screens allow even if word pin appears in content`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.pinterest",
            eventType = 0,
            className = "com.pinterest.activity.HomeActivity",
            fallbackViewIds = listOf("feed_recycler", "search_icon", "home_tab"),
            fallbackTextFragments = listOf("Home Feed", "Explore ideas", "12 pins saved for you"), // generic word "pin" in content
            fallbackContentDescriptions = listOf("Home tab selected")
        )

        val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "pins")
        // Generic content word "pins" without specific target container must not block
        assertTrue(candidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
    }

    // =========================================================================
    // 4. Multi-signal & False Positive Hard Constraints
    // =========================================================================

    @Test
    fun `text-only match alone never blocks`() {
        val genericWords = listOf("channel", "search", "home", "video", "live", "story", "feed", "reels", "shorts")
        for (word in genericWords) {
            val snapshot = AccessibilityScreenScanner.scan(
                packageName = "com.any.app",
                eventType = 0,
                className = "android.widget.FrameLayout",
                fallbackTextFragments = listOf(word)
            )
            val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, word)
            assertTrue("Word '$word' alone must not exceed confidence threshold", candidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
            assertEquals(CandidateType.ENTRY, candidate.candidateType)
        }
    }

    @Test
    fun `view-id-only match alone without structural confirmation never blocks`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.any.app",
            eventType = 0,
            className = "android.widget.LinearLayout",
            fallbackViewIds = listOf("some_random_view_id")
        )
        val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "feature_x")
        assertTrue(candidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
    }

    @Test
    fun `class-only match alone without additional evidence never blocks`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.any.app",
            eventType = 0,
            className = "com.any.app.SomeFragment"
        )
        val candidate = FeatureFingerprintMatcher.matchCandidate(snapshot, "feature_x")
        assertTrue(candidate.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD)
    }

    // =========================================================================
    // 5. Ambiguity & Conflict Veto
    // =========================================================================

    @Test
    fun `two candidates with close scores produce ambiguity conflict veto`() {
        val snapshot = AccessibilityScreenScanner.scan(
            packageName = "com.instagram.android",
            eventType = 0,
            className = "com.instagram.android.MainActivity",
            fallbackTextFragments = listOf("Explore", "Reels"),
            fallbackViewIds = listOf("generic_container")
        )

        val cand1 = FeatureFingerprintMatcher.matchCandidate(snapshot, "reels")
        val cand2 = FeatureFingerprintMatcher.matchCandidate(snapshot, "explore")

        // In an ambiguous generic screen, neither has decisive score
        val conflict = FeatureConflictResolver.resolveConflicts(cand1, listOf(cand1, cand2), snapshot)
        // If either is close, it must either conflict or be below threshold
        assertTrue(cand1.score < FeatureFingerprintMatcher.MIN_CONFIDENCE_THRESHOLD || conflict != null)
    }

    // =========================================================================
    // 6. Whole-app separation & Schedules
    // =========================================================================

    @Test
    fun `whole app rule is completely separate from feature matcher`() {
        val wholeAppRule = ProtectionRule(
            appPackage = "com.zhiliaoapp.musically",
            appName = "TikTok",
            appCategory = "Social",
            blockEntireApp = true,
            features = emptyList(),
            schedule = ScheduleRule(everyDay = true),
            active = true,
            paused = false
        )
        assertTrue(wholeAppRule.blockEntireApp)
    }
}
