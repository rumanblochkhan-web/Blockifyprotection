package com.example.recoveryshield.protection.detector

/**
 * Window- and package-isolated temporal context memory.
 * Stores observations strictly partitioned by:
 * packageName + windowId + contextType + detectorType
 *
 * Expired entries (> 2500ms) are pruned automatically.
 */
class ScopedShortLivedContextMemory(private val ttlMillis: Long = 2500L) {

    data class MemoryEntry(
        val timestamp: Long,
        val text: String,
        val viewId: String,
        val className: String,
        val isIdentity: Boolean,
        val isPrimarySignal: Boolean
    )

    private val store = HashMap<String, MutableList<MemoryEntry>>()

    private fun buildKey(pkg: String, windowId: Int, contextType: SettingsContextType, detectorType: String): String {
        return "${pkg.lowercase()}:$windowId:${contextType.name}:$detectorType"
    }

    @Synchronized
    fun record(
        pkg: String,
        windowId: Int,
        contextType: SettingsContextType,
        detectorType: String,
        text: String = "",
        viewId: String = "",
        className: String = "",
        isIdentity: Boolean = false,
        isPrimarySignal: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val key = buildKey(pkg, windowId, contextType, detectorType)
        val list = store.getOrPut(key) { mutableListOf() }
        list.add(MemoryEntry(now, text, viewId, className, isIdentity, isPrimarySignal))
        prune(now)
    }

    @Synchronized
    fun hasIdentity(pkg: String, windowId: Int, contextType: SettingsContextType, detectorType: String): Boolean {
        prune(System.currentTimeMillis())
        val key = buildKey(pkg, windowId, contextType, detectorType)
        return store[key]?.any { it.isIdentity } == true
    }

    @Synchronized
    fun hasPrimarySignal(pkg: String, windowId: Int, contextType: SettingsContextType, detectorType: String): Boolean {
        prune(System.currentTimeMillis())
        val key = buildKey(pkg, windowId, contextType, detectorType)
        return store[key]?.any { it.isPrimarySignal } == true
    }

    @Synchronized
    fun clearWindow(pkg: String, windowId: Int) {
        val prefix = "${pkg.lowercase()}:$windowId:"
        store.keys.removeAll { it.startsWith(prefix) }
    }

    @Synchronized
    fun clearPackage(pkg: String) {
        val prefix = "${pkg.lowercase()}:"
        store.keys.removeAll { it.startsWith(prefix) }
    }

    @Synchronized
    fun clearAll() {
        store.clear()
    }

    private fun prune(now: Long) {
        val iterator = store.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.removeAll { (now - it.timestamp) > ttlMillis }
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
    }
}
