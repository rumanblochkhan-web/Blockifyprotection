package com.example.protection.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.recoveryshield.protection.detector.EventNormalizer as CoreEventNormalizer

object EventNormalizer {
    fun extractText(event: AccessibilityEvent?, rootNode: AccessibilityNodeInfo?): List<String> {
        if (event == null) return emptyList()
        val normalized = CoreEventNormalizer.normalize(event, rootNode)
        return normalized.textFragments + normalized.contentDescriptions
    }
}

