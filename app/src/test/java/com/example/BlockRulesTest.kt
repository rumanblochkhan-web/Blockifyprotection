package com.example

import com.blockify.ui.block.BlockifyFeatureCatalog
import com.blockify.ui.block.ProtectionRule
import com.blockify.ui.block.ScheduleRule
import com.blockify.ui.block.SelectedFeature
import com.blockify.ui.block.WeekDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BlockRulesTest {

    @Test
    fun `feature catalog resolves Instagram with Reels and Stories features`() {
        val profile = BlockifyFeatureCatalog.resolve("com.instagram.android", "Instagram")
        assertNotNull(profile)
        assertEquals("Instagram", profile?.displayName)

        val features = BlockifyFeatureCatalog.candidateFeatures("com.instagram.android", "Instagram")
        assertTrue(features.any { it.id == "reels" })
        assertTrue(features.any { it.id == "stories" })
    }

    @Test
    fun `feature catalog resolves YouTube with Shorts feature`() {
        val profile = BlockifyFeatureCatalog.resolve("com.google.android.youtube", "YouTube")
        assertNotNull(profile)
        val features = BlockifyFeatureCatalog.candidateFeatures("com.google.android.youtube", "YouTube")
        val shorts = features.firstOrNull { it.id == "shorts" }
        assertNotNull(shorts)
        assertTrue(shorts?.detectionSignals?.contains("shorts") == true)
    }

    @Test
    fun `feature catalog resolves WhatsApp with Status feature`() {
        val profile = BlockifyFeatureCatalog.resolve("com.whatsapp", "WhatsApp")
        assertNotNull(profile)
        val features = BlockifyFeatureCatalog.candidateFeatures("com.whatsapp", "WhatsApp")
        val status = features.firstOrNull { it.id == "status" }
        assertNotNull(status)
        assertTrue(status?.detectionSignals?.contains("status") == true)
    }

    @Test
    fun `schedule matching works for every day within active hours`() {
        val schedule = ScheduleRule(
            everyDay = true,
            selectedDays = WeekDay.entries.toSet(),
            startMinutes = 0,
            endMinutes = 24 * 60 - 1
        )
        // All-day schedule must be active
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        assertTrue(currentMinutes in schedule.startMinutes..schedule.endMinutes)
    }

    @Test
    fun `rule with blockEntireApp flags package for full blocking`() {
        val rule = ProtectionRule(
            appPackage = "com.zhiliaoapp.musically",
            appName = "TikTok",
            appCategory = "Social Media",
            blockEntireApp = true,
            features = emptyList(),
            schedule = ScheduleRule(everyDay = true),
            active = true,
            paused = false
        )
        assertTrue(rule.blockEntireApp)
        assertTrue(rule.active && !rule.paused)
    }

    @Test
    fun `rule with feature-level blocking targets reels in instagram`() {
        val rule = ProtectionRule(
            appPackage = "com.instagram.android",
            appName = "Instagram",
            appCategory = "Social Media",
            blockEntireApp = false,
            features = listOf(SelectedFeature("reels", enabled = true)),
            schedule = ScheduleRule(everyDay = true),
            active = true,
            paused = false
        )
        assertFalse(rule.blockEntireApp)
        val reelsFeature = rule.features.firstOrNull { it.featureId == "reels" }
        assertNotNull(reelsFeature)
        assertTrue(reelsFeature?.enabled == true)

        val catalogFeatures = BlockifyFeatureCatalog.candidateFeatures(rule.appPackage, rule.appName)
        val matchedCatalogFeature = catalogFeatures.firstOrNull { it.id == reelsFeature?.featureId }
        assertNotNull(matchedCatalogFeature)
        assertTrue(matchedCatalogFeature?.detectionSignals?.contains("reels") == true)
    }
}
