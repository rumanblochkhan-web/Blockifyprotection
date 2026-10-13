package com.example.recoveryshield.protection.block.feature

import com.blockify.ui.block.BlockifyFeatureCatalog

/**
 * Provides semantic candidate seeds from featureId and static catalog metadata.
 * NOTE: These seeds are used strictly for initial candidate nomination and negative conflict detection;
 * they NEVER produce a BLOCK decision on their own without structural/multi-signal confirmation.
 */
object FeatureSemanticSeedProvider {

    data class SemanticSeed(
        val featureId: String,
        val targetContainerKeywords: Set<String>,
        val targetViewIdKeywords: Set<String>,
        val targetClassKeywords: Set<String>,
        val navigationTokens: Set<String>,
        val conflictingSiblingFeatureIds: Set<String>
    )

    /**
     * Resolves the semantic seed for a given package and featureId.
     */
    fun getSeed(packageName: String, featureId: String): SemanticSeed {
        val lowerPkg = packageName.lowercase()
        val lowerId = featureId.lowercase()

        // 1. Specialized contextual seeds for top communication and social apps
        when {
            lowerPkg.contains("whatsapp") -> {
                when (lowerId) {
                    "channels" -> return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("channel", "directory", "updates_tab", "newsletter", "follow_channel"),
                        targetViewIdKeywords = setOf("channel_feed", "newsletter_title", "directory_recycler", "channels_pager", "newsletter_view"),
                        targetClassKeywords = setOf("newsletteractivity", "channeldirectoryactivity", "updatesfragment"),
                        navigationTokens = setOf("channels", "updates"),
                        conflictingSiblingFeatureIds = setOf("status", "chats", "calls", "communities")
                    )
                    "status" -> return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("status", "story", "stories", "my_status", "status_playback"),
                        targetViewIdKeywords = setOf("status_playback", "status_feed", "status_row", "status_pager"),
                        targetClassKeywords = setOf("statusplaybackactivity", "statuslistactivity"),
                        navigationTokens = setOf("status", "updates"),
                        conflictingSiblingFeatureIds = setOf("channels", "chats", "calls", "communities", "camera", "media_composer", "conversation")
                    )
                    "calls" -> return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("call", "calls", "dialer", "call_log"),
                        targetViewIdKeywords = setOf("call_log", "calls_pager", "call_row"),
                        targetClassKeywords = setOf("voipactivity", "callsfragment"),
                        navigationTokens = setOf("calls"),
                        conflictingSiblingFeatureIds = setOf("chats", "status", "channels")
                    )
                    "communities" -> return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("community", "communities", "subgroups"),
                        targetViewIdKeywords = setOf("community_feed", "community_pager"),
                        targetClassKeywords = setOf("communityactivity", "communitiesfragment"),
                        navigationTokens = setOf("communities"),
                        conflictingSiblingFeatureIds = setOf("chats", "status", "channels")
                    )
                }
            }
            lowerPkg.contains("chrome") -> {
                if (lowerId == "incognito") {
                    return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("incognito", "private_browsing", "incognito_tab", "secret"),
                        targetViewIdKeywords = setOf("incognito_badge", "incognito_desc", "incognito_toggle", "incognito_ntp"),
                        targetClassKeywords = setOf("incognitotabactivity", "incognitontp", "incognitoview"),
                        navigationTokens = setOf("incognito", "private"),
                        conflictingSiblingFeatureIds = setOf("normal_tab", "history", "bookmarks", "downloads", "settings")
                    )
                }
            }
            lowerPkg.contains("instagram") -> {
                when (lowerId) {
                    "reels" -> return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("reel", "reels", "clip", "clips", "clipsviewer"),
                        targetViewIdKeywords = setOf("clips_video_container", "reel_viewer_layout", "clips_media_component", "clips_author_container"),
                        targetClassKeywords = setOf("clipsvieweractivity", "reelviewerfragment", "clipstimeline"),
                        navigationTokens = setOf("reels", "clips"),
                        conflictingSiblingFeatureIds = setOf("feed", "explore", "direct", "profile", "stories")
                    )
                    "explore", "search" -> return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("explore", "discover", "search_grid"),
                        targetViewIdKeywords = setOf("explore_recycler", "search_bar", "grid_layout"),
                        targetClassKeywords = setOf("explorefragment", "searchactivity"),
                        navigationTokens = setOf("explore", "search"),
                        conflictingSiblingFeatureIds = setOf("feed", "reels", "direct", "profile")
                    )
                }
            }
            lowerPkg.contains("youtube") -> {
                if (lowerId == "shorts") {
                    return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf("shorts", "reel", "short_player", "reel_watch"),
                        targetViewIdKeywords = setOf("reel_player_view", "shorts_container", "reel_watch_fragment", "shorts_recycler"),
                        targetClassKeywords = setOf("reelwatchfragment", "shortsactivity"),
                        navigationTokens = setOf("shorts"),
                        conflictingSiblingFeatureIds = setOf("home", "subscriptions", "library", "search")
                    )
                }
            }
            lowerPkg.contains("pinterest") -> {
                if (lowerId == "pins" || lowerId == "create" || lowerId == "watch") {
                    return SemanticSeed(
                        featureId = featureId,
                        targetContainerKeywords = setOf(lowerId, "board", "pin_view"),
                        targetViewIdKeywords = setOf("pin_detail", "pin_view_pager", "pin_creation"),
                        targetClassKeywords = setOf("pindetailfragment", "pinactivity"),
                        navigationTokens = setOf(lowerId),
                        conflictingSiblingFeatureIds = setOf("home", "search", "notifications", "profile")
                    )
                }
            }
        }

        // 2. Generic fallback semantic seed derived from Catalog detectionSignals if available
        val catalogFeatures = BlockifyFeatureCatalog.candidateFeatures(packageName, "")
        val catalogMatch = catalogFeatures.firstOrNull { it.id.equals(featureId, ignoreCase = true) }
        val signals = catalogMatch?.detectionSignals?.map { it.lowercase() }?.toSet() ?: setOf(lowerId)

        return SemanticSeed(
            featureId = featureId,
            targetContainerKeywords = signals,
            targetViewIdKeywords = signals.map { "${it}_container" }.toSet() + signals.map { "${it}_view" },
            targetClassKeywords = signals.map { "${it}activity" }.toSet() + signals.map { "${it}fragment" },
            navigationTokens = signals,
            conflictingSiblingFeatureIds = catalogFeatures.filter { it.id != featureId }.map { it.id }.toSet()
        )
    }
}
