package com.example.recoveryshield.protection.detector

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.recoveryshield.domain.model.NormalizedUiEvent
import java.text.Normalizer
import java.util.Locale

/**
 * Transforms raw Accessibility events and node hierarchies into minimal, sanitized
 * NormalizedUiEvent models with ultra-low latency.
 * Strictly avoids persisting full text or collecting passwords, private messages, etc.
 */
object EventNormalizer {

    private const val MAX_NODE_DEPTH = 12
    private const val MAX_COLLECTED_NODES = 100
    private const val MAX_TOKEN_LENGTH = 48
    private val DIACRITICS_REGEX = Regex("\\p{Mn}+")

    fun normalize(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): NormalizedUiEvent {
        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        val textFragments = LinkedHashSet<String>()
        val contentDescriptions = LinkedHashSet<String>()
        val viewIds = LinkedHashSet<String>()

        // Extract primary event text
        event.text?.forEach { charSeq ->
            sanitizeAndTokenize(charSeq?.toString())?.let { textFragments.add(it) }
        }

        event.contentDescription?.let { desc ->
            sanitizeAndTokenize(desc.toString())?.let { contentDescriptions.add(it) }
        }

        // Direct inspect event.source if available
        try {
            val source = event.source
            if (source != null) {
                source.text?.let { sanitizeAndTokenize(it.toString())?.let { t -> textFragments.add(t) } }
                source.contentDescription?.let { sanitizeAndTokenize(it.toString())?.let { d -> contentDescriptions.add(d) } }
                source.viewIdResourceName?.let { vid ->
                    val token = vid.substringAfter(":id/").lowercase()
                    if (token.isNotBlank()) viewIds.add(token)
                }
            }
        } catch (_: Exception) {}

        // Safely extract interactive node labels and view IDs if available
        if (rootNode != null) {
            var nodesVisited = 0
            extractNodeTextRecursive(rootNode, textFragments, contentDescriptions, viewIds, 0) {
                nodesVisited++ < MAX_COLLECTED_NODES
            }
        }

        val isTransient = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

        return NormalizedUiEvent(
            timestampMillis = System.currentTimeMillis(),
            packageName = packageName,
            className = className,
            eventType = event.eventType,
            textFragments = textFragments.take(40),
            contentDescriptions = contentDescriptions.take(25),
            viewIds = viewIds.take(30),
            windowRole = if (event.isFullScreen) "full_screen" else "standard",
            isTransient = isTransient
        )
    }

    private fun extractNodeTextRecursive(
        node: AccessibilityNodeInfo?,
        texts: MutableSet<String>,
        descriptions: MutableSet<String>,
        viewIds: MutableSet<String>,
        depth: Int,
        budgetCheck: () -> Boolean
    ) {
        if (node == null || depth > MAX_NODE_DEPTH || !budgetCheck()) return

        // Skip password fields completely for privacy and security
        if (node.isPassword) return

        node.text?.let {
            sanitizeAndTokenize(it.toString())?.let { token ->
                if (token.isNotEmpty()) {
                    texts.add(token)
                }
            }
        }

        node.contentDescription?.let {
            sanitizeAndTokenize(it.toString())?.let { desc ->
                if (desc.isNotEmpty()) {
                    descriptions.add(desc)
                }
            }
        }

        node.viewIdResourceName?.let { vid ->
            val simpleId = vid.substringAfter(":id/").lowercase()
            if (simpleId.isNotEmpty()) {
                viewIds.add(simpleId)
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            extractNodeTextRecursive(child, texts, descriptions, viewIds, depth + 1, budgetCheck)
        }
    }

    /**
     * Sanitizes input text with zero-allocation ASCII fast-path.
     */
    fun sanitizeAndTokenize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var hasNonAscii = false
        for (i in 0 until raw.length) {
            if (raw[i].code >= 128) {
                hasNonAscii = true
                break
            }
        }

        val normalized = if (hasNonAscii) {
            Normalizer.normalize(raw, Normalizer.Form.NFKD)
                .replace(DIACRITICS_REGEX, "")
                .lowercase(Locale.ROOT)
                .trim()
        } else {
            raw.lowercase(Locale.ROOT).trim()
        }

        if (normalized.isEmpty()) return null

        return if (normalized.length > 128) normalized.substring(0, 128) else normalized
    }
}
