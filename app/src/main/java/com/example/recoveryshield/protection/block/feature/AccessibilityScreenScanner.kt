package com.example.recoveryshield.protection.block.feature

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.text.Normalizer
import java.util.ArrayDeque

/**
 * Scans the current accessibility hierarchy and interactive windows, converting them into
 * lightweight, immutable [ScreenSnapshot] and [NodeSnapshot] DTOs with bounded traversal.
 */
object AccessibilityScreenScanner {

    private const val MAX_NODES_BUDGET = 1200
    private const val MAX_DEPTH_BUDGET = 24

    /**
     * Builds a comprehensive [ScreenSnapshot] from the raw accessibility root and event.
     */
    fun scan(
        packageName: String,
        eventType: Int,
        className: String = "",
        eventText: String = "",
        eventContentDescription: String = "",
        eventViewId: String = "",
        rootNode: AccessibilityNodeInfo? = null,
        eventSource: AccessibilityNodeInfo? = null,
        interactiveWindows: List<AccessibilityWindowInfo>? = null,
        fallbackTextFragments: List<String> = emptyList(),
        fallbackContentDescriptions: List<String> = emptyList(),
        fallbackViewIds: List<String> = emptyList()
    ): ScreenSnapshot {
        val now = System.currentTimeMillis()
        val isClick = eventType == AccessibilityEvent.TYPE_VIEW_CLICKED

        val eventSourceTokens = tokenize(eventText)
        val eventSourceDescTokens = tokenize(eventContentDescription)

        // Parse interactive windows metadata
        val windowSnapshots = mutableListOf<WindowSnapshot>()
        if (interactiveWindows != null) {
            for (w in interactiveWindows) {
                windowSnapshots.add(
                    WindowSnapshot(
                        windowId = w.id,
                        windowType = w.type,
                        title = w.title?.toString()?.trim() ?: "",
                        isActive = w.isActive,
                        isFocused = w.isFocused,
                        layer = w.layer
                    )
                )
            }
        }

        val nodes = mutableListOf<NodeSnapshot>()
        val allViewIds = mutableSetOf<String>()
        val allTextTokens = mutableSetOf<String>()
        val allContentDescTokens = mutableSetOf<String>()
        val activeTabTokens = mutableSetOf<String>()

        // Add pre-extracted fallback tokens if provided
        for (v in fallbackViewIds) {
            val normalized = normalizeViewId(v)
            if (normalized.isNotBlank()) allViewIds.add(normalized)
        }
        for (t in fallbackTextFragments) {
            allTextTokens.addAll(tokenize(t))
        }
        for (d in fallbackContentDescriptions) {
            allContentDescTokens.addAll(tokenize(d))
        }
        if (eventViewId.isNotBlank()) {
            allViewIds.add(normalizeViewId(eventViewId))
        }

        var isComplete = true
        var maxObservedDepth = 0
        var totalVisitedNodes = 0

        // Traverse raw AccessibilityNodeInfo hierarchy if available
        val primaryRoot = rootNode ?: eventSource
        if (primaryRoot != null) {
            val visitedIdentities = HashSet<Int>()
            val queue = ArrayDeque<NodeTraversalItem>()
            queue.add(NodeTraversalItem(primaryRoot, depth = 0, childOrder = 0, ancestorPath = primaryRoot.className?.toString() ?: "Root"))

            val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
            val screenWidth = displayMetrics.widthPixels.coerceAtLeast(1080)
            val screenHeight = displayMetrics.heightPixels.coerceAtLeast(1920)
            val tempRect = Rect()

            while (queue.isNotEmpty() && totalVisitedNodes < MAX_NODES_BUDGET) {
                val item = queue.poll() ?: break
                val node = item.node
                val depth = item.depth
                val childOrder = item.childOrder
                val ancestorPath = item.ancestorPath

                totalVisitedNodes++
                maxObservedDepth = maxOf(maxObservedDepth, depth)

                val nodeHash = System.identityHashCode(node)
                if (visitedIdentities.contains(nodeHash)) {
                    continue
                }
                visitedIdentities.add(nodeHash)

                // Privacy: Skip secure password nodes
                if (node.isPassword) {
                    continue
                }

                val vId = normalizeViewId(node.viewIdResourceName)
                if (vId.isNotBlank()) {
                    allViewIds.add(vId)
                }

                val nodeText = node.text?.toString()?.trim() ?: ""
                val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""
                val nodeTokens = tokenize(nodeText)
                val descTokens = tokenize(nodeDesc)

                allTextTokens.addAll(nodeTokens)
                allContentDescTokens.addAll(descTokens)

                val isSelected = node.isSelected
                val isChecked = node.isChecked
                val isFocused = node.isFocused
                val isAccessibilityFocused = node.isAccessibilityFocused
                val isClickable = node.isClickable
                val isScrollable = node.isScrollable
                val isHeading = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) node.isHeading else false
                val isVisible = node.isVisibleToUser
                val classNameStr = node.className?.toString() ?: ""

                // Track active navigation tab tokens (e.g. selected bottom tab)
                if (isSelected || isChecked) {
                    activeTabTokens.addAll(nodeTokens)
                    activeTabTokens.addAll(descTokens)
                }

                var topFraction = 0f
                var bottomFraction = 0f
                var leftFraction = 0f
                var rightFraction = 0f
                try {
                    node.getBoundsInScreen(tempRect)
                    topFraction = (tempRect.top.toFloat() / screenHeight).coerceIn(0f, 1f)
                    bottomFraction = (tempRect.bottom.toFloat() / screenHeight).coerceIn(0f, 1f)
                    leftFraction = (tempRect.left.toFloat() / screenWidth).coerceIn(0f, 1f)
                    rightFraction = (tempRect.right.toFloat() / screenWidth).coerceIn(0f, 1f)
                } catch (_: Throwable) {}

                nodes.add(
                    NodeSnapshot(
                        id = totalVisitedNodes,
                        viewId = vId,
                        className = classNameStr,
                        textTokens = nodeTokens,
                        contentDescTokens = descTokens,
                        isSelected = isSelected,
                        isChecked = isChecked,
                        isFocused = isFocused,
                        isAccessibilityFocused = isAccessibilityFocused,
                        isEnabled = node.isEnabled,
                        isClickable = isClickable,
                        isScrollable = isScrollable,
                        isHeading = isHeading,
                        isVisibleToUser = isVisible,
                        childCount = node.childCount,
                        childOrder = childOrder,
                        depth = depth,
                        ancestorPath = ancestorPath,
                        boundsTopFraction = topFraction,
                        boundsBottomFraction = bottomFraction,
                        boundsLeftFraction = leftFraction,
                        boundsRightFraction = rightFraction
                    )
                )

                if (depth < MAX_DEPTH_BUDGET) {
                    val childCount = node.childCount
                    for (i in 0 until childCount) {
                        val child = try { node.getChild(i) } catch (_: Throwable) { null }
                        if (child != null) {
                            val childClass = child.className?.toString()?.substringAfterLast(".") ?: "Node"
                            val nextPath = "$ancestorPath/$childClass"
                            queue.add(NodeTraversalItem(child, depth + 1, i, nextPath))
                        }
                    }
                } else {
                    isComplete = false
                }
            }

            if (queue.isNotEmpty()) {
                isComplete = false
            }
        }

        return ScreenSnapshot(
            packageName = packageName,
            className = className,
            eventType = eventType,
            eventTimestamp = now,
            eventSourceViewId = normalizeViewId(eventViewId),
            eventSourceClass = className,
            eventSourceTextTokens = eventSourceTokens,
            eventSourceDescTokens = eventSourceDescTokens,
            isClick = isClick,
            isComplete = isComplete,
            totalNodeCount = nodes.size,
            maxDepth = maxObservedDepth,
            nodes = nodes,
            windows = windowSnapshots,
            viewIdSet = allViewIds,
            textTokenSet = allTextTokens,
            contentDescTokenSet = allContentDescTokens,
            activeTabSelectedTokens = activeTabTokens
        )
    }

    private data class NodeTraversalItem(
        val node: AccessibilityNodeInfo,
        val depth: Int,
        val childOrder: Int,
        val ancestorPath: String
    )

    fun tokenize(input: String?): Set<String> {
        if (input.isNullOrBlank()) return emptySet()
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
        return normalized.split("[^a-z0-9_]+".toRegex())
            .filter { it.length >= 2 }
            .toSet()
    }

    fun normalizeViewId(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.substringAfter(":id/").trim().lowercase()
    }
}
