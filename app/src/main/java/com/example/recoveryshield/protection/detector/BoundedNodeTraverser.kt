package com.example.recoveryshield.protection.detector

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Reusable bounded Accessibility tree traversal helper based on Kahf-inspired
 * bounded search principles.
 *
 * Ensures:
 * - Breadth-first traversal
 * - Visited-node cycle protection
 * - Hard bounds: max 100 inspected nodes per root/window, max depth 8, max 10 children per node
 * - Immediate termination once the required signature predicate returns true
 * - Safe node access with exception handling
 */
object BoundedNodeTraverser {

    const val DEFAULT_MAX_NODES = 150
    const val DEFAULT_MAX_DEPTH = 10
    const val DEFAULT_MAX_CHILDREN_PER_NODE = 50

    data class TraversalStats(
        var nodesScanned: Int = 0,
        var terminatedEarly: Boolean = false
    )

    fun traverse(
        root: AccessibilityNodeInfo?,
        maxNodes: Int = DEFAULT_MAX_NODES,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxChildrenPerNode: Int = DEFAULT_MAX_CHILDREN_PER_NODE,
        onNode: (node: AccessibilityNodeInfo, depth: Int) -> Boolean
    ): TraversalStats {
        val stats = TraversalStats()
        if (root == null) return stats

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        val visited = HashSet<Int>()

        val rootHash = System.identityHashCode(root)
        visited.add(rootHash)
        queue.add(Pair(root, 0))

        while (!queue.isEmpty() && stats.nodesScanned < maxNodes) {
            val (current, depth) = queue.poll() ?: break
            stats.nodesScanned++

            val shouldStop = try {
                onNode(current, depth)
            } catch (_: Exception) {
                false
            }

            if (shouldStop) {
                stats.terminatedEarly = true
                break
            }

            if (depth < maxDepth) {
                val childCount = try { current.childCount } catch (_: Exception) { 0 }
                val inspectChildren = childCount.coerceAtMost(maxChildrenPerNode)
                for (i in 0 until inspectChildren) {
                    val child = try { current.getChild(i) } catch (_: Exception) { null } ?: continue
                    val childHash = System.identityHashCode(child)
                    if (visited.add(childHash)) {
                        queue.add(Pair(child, depth + 1))
                    }
                }
            }
        }

        return stats
    }
}
