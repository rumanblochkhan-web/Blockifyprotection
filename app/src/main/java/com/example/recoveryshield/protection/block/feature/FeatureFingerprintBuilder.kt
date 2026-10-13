package com.example.recoveryshield.protection.block.feature

import java.security.MessageDigest

/**
 * Builds normalized stable fingerprints, structural signatures, and compound view ID combinations
 * from a [ScreenSnapshot].
 */
object FeatureFingerprintBuilder {

    /**
     * Builds a deterministic structural signature representing the screen hierarchy shape.
     */
    fun buildStructuralSignature(snapshot: ScreenSnapshot): String {
        if (snapshot.nodes.isEmpty()) {
            val viewIdsHash = hashTokens(snapshot.viewIdSet)
            return "shape:${snapshot.className}:$viewIdsHash"
        }

        val shapeBuilder = StringBuilder()
        shapeBuilder.append(snapshot.className.substringAfterLast(".")).append(";")

        val structuralNodes = snapshot.nodes.filter {
            it.isScrollable || it.childCount > 2 || it.viewId.isNotBlank() || it.isHeading
        }.take(30)

        for (node in structuralNodes) {
            shapeBuilder.append(node.depth).append(":")
                .append(node.className.substringAfterLast(".")).append(":")
                .append(node.viewId.ifBlank { "_" }).append(";")
        }

        return sha256Hex(shapeBuilder.toString()).take(24)
    }

    /**
     * Extracts compound stable view ID signatures (pairs/triplets of co-occurring IDs).
     */
    fun extractCompoundViewIdSignatures(viewIds: Set<String>): Set<String> {
        val filtered = viewIds.filter { it.length >= 3 && !it.matches("\\d+".toRegex()) }.sorted()
        if (filtered.isEmpty()) return emptySet()

        val results = mutableSetOf<String>()
        for (id in filtered) {
            results.add("single:$id")
        }

        // Add 2-tuples for strong structural co-occurrence
        for (i in 0 until minOf(filtered.size, 10)) {
            for (j in (i + 1) until minOf(filtered.size, 10)) {
                results.add("pair:${filtered[i]}+${filtered[j]}")
            }
        }
        return results
    }

    /**
     * Hashes a set of tokens into a deterministic sorted hash string.
     */
    fun hashTokens(tokens: Set<String>): String {
        if (tokens.isEmpty()) return "empty"
        val sorted = tokens.sorted().joinToString(",")
        return sha256Hex(sorted).take(16)
    }

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
