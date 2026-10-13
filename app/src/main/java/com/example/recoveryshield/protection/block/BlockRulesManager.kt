package com.example.recoveryshield.protection.block

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.blockify.ui.block.BlockFeature
import com.blockify.ui.block.BlockifyFeatureCatalog
import com.blockify.ui.block.InstalledAppItem
import com.blockify.ui.block.ProtectionRule
import com.blockify.ui.block.ScheduleRule
import com.blockify.ui.block.SelectedFeature
import com.blockify.ui.block.WeekDay
import com.example.recoveryshield.protection.block.feature.FeatureBlockEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * High-performance, backward-compatible facade for Block section rules management,
 * persistence, schedule evaluation, full-app blocking, and universal feature-level enforcement.
 */
class BlockRulesManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("blockify_protection_rules_prefs", Context.MODE_PRIVATE)

    private val _rulesFlow = MutableStateFlow<List<ProtectionRule>>(loadRules())
    val rulesFlow: StateFlow<List<ProtectionRule>> = _rulesFlow.asStateFlow()

    private val featureEngine: FeatureBlockEngine by lazy {
        FeatureBlockEngine.getInstance(context)
    }

    @Volatile
    private var cachedInstalledApps: List<InstalledAppItem>? = null

    companion object {
        private const val KEY_RULES_JSON = "saved_rules_json"

        @Volatile
        private var INSTANCE: BlockRulesManager? = null

        fun getInstance(context: Context): BlockRulesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BlockRulesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getRules(): List<ProtectionRule> = _rulesFlow.value

    @Synchronized
    fun saveRule(rule: ProtectionRule) {
        val current = _rulesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.appPackage == rule.appPackage }
        if (index >= 0) {
            val existing = current[index]
            val validatedRule = if (existing.active && !existing.paused) {
                enforceRuleImmutability(existing, rule)
            } else {
                rule
            }
            current[index] = validatedRule
        } else {
            current.add(rule)
        }
        persistRules(current)
    }

    /**
     * Enforces the critical edit-bypass prevention rule:
     * Once a rule is committed as active, Edit CANNOT weaken or remove existing protection.
     */
    private fun enforceRuleImmutability(existing: ProtectionRule, attempted: ProtectionRule): ProtectionRule {
        val finalActive = true
        val finalPaused = false
        val finalSchedule = enforceScheduleImmutability(existing.schedule, attempted.schedule)
        
        // If it was full app, it must stay full app.
        // If it was feature-only, it must stay feature-only (with features appended).
        val finalBlockEntireApp = existing.blockEntireApp
        val finalFeatures = if (finalBlockEntireApp) emptyList() else (existing.features + attempted.features).distinctBy { it.featureId }

        return existing.copy(
            blockEntireApp = finalBlockEntireApp,
            features = finalFeatures,
            schedule = finalSchedule,
            active = finalActive,
            paused = finalPaused
        )
    }

    private fun enforceScheduleImmutability(existing: ScheduleRule, attempted: ScheduleRule): ScheduleRule {
        // If existing schedule was full-time (all day / every day), attempted cannot weaken it.
        val finalEveryDay = existing.everyDay || attempted.everyDay
        val finalAllDay = existing.allDay || attempted.allDay

        val finalSelectedDays = if (existing.everyDay) {
            WeekDay.entries.toSet()
        } else {
            existing.selectedDays + attempted.selectedDays
        }

        // For time range, preserve or expand time window
        val finalStartMinutes = if (existing.allDay) 0 else minOf(existing.startMinutes, attempted.startMinutes)
        val finalEndMinutes = if (existing.allDay) 24 * 60 - 1 else maxOf(existing.endMinutes, attempted.endMinutes)

        return ScheduleRule(
            everyDay = finalEveryDay,
            selectedDays = finalSelectedDays,
            allDay = finalAllDay,
            startMinutes = finalStartMinutes,
            endMinutes = finalEndMinutes
        )
    }

    @Synchronized
    fun deleteRule(rule: ProtectionRule) {
        val current = _rulesFlow.value.toMutableList()
        current.removeAll { it.appPackage == rule.appPackage }
        persistRules(current)
        featureEngine.onRuleDeleted(rule)
    }

    @Synchronized
    fun pauseRule(rule: ProtectionRule) {
        val current = _rulesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.appPackage == rule.appPackage }
        if (index >= 0) {
            current[index] = current[index].copy(paused = true)
            persistRules(current)
        }
    }

    @Synchronized
    fun resumeRule(rule: ProtectionRule) {
        val current = _rulesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.appPackage == rule.appPackage }
        if (index >= 0) {
            current[index] = current[index].copy(paused = false, active = true)
            persistRules(current)
        }
    }

    /**
     * Determines whether a given schedule is currently active right now.
     */
    fun isScheduleActive(schedule: ScheduleRule): Boolean {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val currentWeekDay = when (dayOfWeek) {
            Calendar.MONDAY -> WeekDay.MON
            Calendar.TUESDAY -> WeekDay.TUE
            Calendar.WEDNESDAY -> WeekDay.WED
            Calendar.THURSDAY -> WeekDay.THU
            Calendar.FRIDAY -> WeekDay.FRI
            Calendar.SATURDAY -> WeekDay.SAT
            Calendar.SUNDAY -> WeekDay.SUN
            else -> WeekDay.MON
        }

        if (!schedule.everyDay && currentWeekDay !in schedule.selectedDays) {
            return false
        }

        if (schedule.allDay || (schedule.startMinutes == 0 && schedule.endMinutes >= 24 * 60 - 1)) {
            return true
        }

        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = schedule.startMinutes
        val end = schedule.endMinutes

        return if (start <= end) {
            currentMinutes in start..end
        } else {
            // Spans midnight (e.g. 21:00 to 07:00)
            currentMinutes >= start || currentMinutes <= end
        }
    }

    /**
     * Checks if the foreground package is completely blocked right now.
     * Operates purely on whole-app rules and schedule state.
     */
    fun checkFullAppBlock(packageName: String): ProtectionRule? {
        val rules = _rulesFlow.value
        val lowerPkg = packageName.lowercase()
        return rules.firstOrNull { rule ->
            val rulePkg = rule.appPackage.lowercase()
            val matchesPkg = rulePkg == lowerPkg ||
                    (rulePkg == "com.whatsapp" && lowerPkg == "com.whatsapp.w4b") ||
                    (rulePkg == "com.whatsapp.w4b" && lowerPkg == "com.whatsapp")
            matchesPkg &&
                    rule.active &&
                    !rule.paused &&
                    rule.blockEntireApp &&
                    isScheduleActive(rule.schedule)
        }
    }

    /**
     * Checks if a specific feature (Reels, Shorts, Status, Incognito, etc.) is blocked on the current screen.
     * Delegates to [FeatureBlockEngine] for deterministic, multi-signal, conflict-safe validation.
     */
    fun checkFeatureBlock(
        packageName: String,
        textFragments: List<String>,
        contentDescriptions: List<String>,
        className: String = "",
        viewIds: List<String> = emptyList(),
        eventType: Int = 0,
        eventText: String = "",
        eventContentDescription: String = "",
        eventViewId: String = "",
        rootNode: AccessibilityNodeInfo? = null,
        eventSource: AccessibilityNodeInfo? = null
    ): Pair<ProtectionRule, BlockFeature>? {
        val rules = _rulesFlow.value
        val applicableRules = rules.filter { 
            it.appPackage == packageName && it.active && !it.paused && !it.blockEntireApp && isScheduleActive(it.schedule) 
        }
        
        for (rule in applicableRules) {
            val result = featureEngine.evaluate(
                rule = rule,
                packageName = packageName,
                textFragments = textFragments,
                contentDescriptions = contentDescriptions,
                className = className,
                viewIds = viewIds,
                eventType = eventType,
                eventText = eventText,
                eventContentDescription = eventContentDescription,
                eventViewId = eventViewId,
                rootNode = rootNode,
                eventSource = eventSource
            )
            if (result != null) return result
        }
        
        return null
    }

    /**
     * Discovers installed applications for the Block UI catalog.
     */
    fun getInstalledApps(): List<InstalledAppItem> {
        cachedInstalledApps?.let { return it }

        val appMap = LinkedHashMap<String, InstalledAppItem>()
        val pm = context.packageManager

        // 1. Discover locally installed launchable apps
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) continue // Skip ourselves

                val name = info.loadLabel(pm)?.toString()?.trim() ?: pkg
                if (name.isBlank()) continue

                if (!appMap.containsKey(pkg)) {
                    val matchedProfile = BlockifyFeatureCatalog.resolve(pkg, name)
                    val category = matchedProfile?.category ?: getAppCategoryLabel(info.activityInfo.applicationInfo)

                    appMap[pkg] = InstalledAppItem(
                        packageName = pkg,
                        appName = name,
                        category = category,
                        iconText = name.take(1).uppercase()
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BlockRulesManager", "Failed to query launchable activities", e)
        }

        // 2. Fallback: If launcher intent returned nothing, query installed applications directly
        if (appMap.isEmpty()) {
            try {
                val installedApplications = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (appInfo in installedApplications) {
                    val pkg = appInfo.packageName
                    if (pkg == context.packageName) continue
                    val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: continue
                    val name = appInfo.loadLabel(pm)?.toString()?.trim() ?: pkg
                    if (!appMap.containsKey(pkg)) {
                        val matchedProfile = BlockifyFeatureCatalog.resolve(pkg, name)
                        val category = matchedProfile?.category ?: getAppCategoryLabel(appInfo)

                        appMap[pkg] = InstalledAppItem(
                            packageName = pkg,
                            appName = name,
                            category = category,
                            iconText = name.take(1).uppercase()
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BlockRulesManager", "Failed to query installed applications", e)
            }
        }

        val result = appMap.values.sortedBy { it.appName.lowercase() }
        cachedInstalledApps = result
        return result
    }

    private fun getAppCategoryLabel(appInfo: ApplicationInfo?): String {
        if (appInfo == null) return "App"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> "Games"
                ApplicationInfo.CATEGORY_AUDIO -> "Audio & Music"
                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                ApplicationInfo.CATEGORY_IMAGE -> "Photos & Video"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_NEWS -> "News"
                ApplicationInfo.CATEGORY_MAPS -> "Maps & Navigation"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
                else -> "App"
            }
        }
        return "App"
    }

    private fun persistRules(rules: List<ProtectionRule>) {
        try {
            val jsonArray = JSONArray()
            for (rule in rules) {
                val obj = JSONObject()
                obj.put("appPackage", rule.appPackage)
                obj.put("appName", rule.appName)
                obj.put("appCategory", rule.appCategory)
                obj.put("blockEntireApp", rule.blockEntireApp)
                obj.put("active", rule.active)
                obj.put("paused", rule.paused)

                val featuresArray = JSONArray()
                for (feat in rule.features) {
                    val fObj = JSONObject()
                    fObj.put("featureId", feat.featureId)
                    fObj.put("enabled", feat.enabled)
                    featuresArray.put(fObj)
                }
                obj.put("features", featuresArray)

                val schedObj = JSONObject()
                schedObj.put("everyDay", rule.schedule.everyDay)
                schedObj.put("allDay", rule.schedule.allDay)
                schedObj.put("startMinutes", rule.schedule.startMinutes)
                schedObj.put("endMinutes", rule.schedule.endMinutes)
                val daysArray = JSONArray()
                for (d in rule.schedule.selectedDays) {
                    daysArray.put(d.name)
                }
                schedObj.put("selectedDays", daysArray)
                obj.put("schedule", schedObj)

                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_RULES_JSON, jsonArray.toString()).apply()
            _rulesFlow.value = rules
        } catch (e: Exception) {
            android.util.Log.e("BlockRulesManager", "Failed to persist rules", e)
        }
    }

    private fun loadRules(): List<ProtectionRule> {
        val jsonStr = prefs.getString(KEY_RULES_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            return emptyList()
        }
        val list = mutableListOf<ProtectionRule>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val appPackage = obj.getString("appPackage")
                val appName = obj.optString("appName", appPackage)
                val appCategory = obj.optString("appCategory", "App")
                val blockEntireApp = obj.optBoolean("blockEntireApp", false)
                val active = obj.optBoolean("active", true)
                val paused = obj.optBoolean("paused", false)

                val features = mutableListOf<SelectedFeature>()
                val featuresArray = obj.optJSONArray("features")
                if (featuresArray != null) {
                    for (j in 0 until featuresArray.length()) {
                        val fObj = featuresArray.getJSONObject(j)
                        features.add(
                            SelectedFeature(
                                featureId = fObj.getString("featureId"),
                                enabled = fObj.getBoolean("enabled")
                            )
                        )
                    }
                }

                val schedObj = obj.optJSONObject("schedule")
                val schedule = if (schedObj != null) {
                    val everyDay = schedObj.optBoolean("everyDay", true)
                    val allDay = schedObj.optBoolean("allDay", true)
                    val startMin = schedObj.optInt("startMinutes", 0)
                    val endMin = schedObj.optInt("endMinutes", 24 * 60 - 1)
                    val days = mutableSetOf<WeekDay>()
                    val daysArray = schedObj.optJSONArray("selectedDays")
                    if (daysArray != null) {
                        for (d in 0 until daysArray.length()) {
                            try {
                                days.add(WeekDay.valueOf(daysArray.getString(d)))
                            } catch (_: Exception) {}
                        }
                    }
                    if (days.isEmpty()) {
                        days.addAll(WeekDay.entries)
                    }
                    ScheduleRule(
                        everyDay = everyDay,
                        selectedDays = days,
                        allDay = allDay,
                        startMinutes = startMin,
                        endMinutes = endMin
                    )
                } else {
                    ScheduleRule()
                }

                list.add(
                    ProtectionRule(
                        appPackage = appPackage,
                        appName = appName,
                        appCategory = appCategory,
                        blockEntireApp = blockEntireApp,
                        features = features,
                        schedule = schedule,
                        active = active,
                        paused = paused
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BlockRulesManager", "Failed to load rules", e)
        }
        return list
    }
}
