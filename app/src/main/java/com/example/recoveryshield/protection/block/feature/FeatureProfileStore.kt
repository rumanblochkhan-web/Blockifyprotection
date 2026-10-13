package com.example.recoveryshield.protection.block.feature

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists versioned feature profiles in SharedPreferences under key "feature_profiles_json" (sidecar),
 * keeping the existing ProtectionRule persistence completely untouched.
 */
class FeatureProfileStore(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("blockify_feature_profiles_prefs", Context.MODE_PRIVATE)

    private val profileCache = ConcurrentHashMap<String, FeatureProfile>()

    companion object {
        private const val TAG = "FeatureProfileStore"
        private const val KEY_PROFILES_JSON = "feature_profiles_json"
        private const val CURRENT_SCHEMA_VERSION = 1

        @Volatile
        private var INSTANCE: FeatureProfileStore? = null

        fun getInstance(context: Context): FeatureProfileStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeatureProfileStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadProfiles()
    }

    fun getProfile(packageName: String, featureId: String): FeatureProfile? {
        val key = makeKey(packageName, featureId)
        val profile = profileCache[key] ?: return null

        // Check for app version staleness
        val currentVersionCode = getAppVersionCode(packageName)
        if (currentVersionCode > 0L && profile.appVersionCode > 0L && profile.appVersionCode != currentVersionCode) {
            if (profile.status != ProfileStatus.STALE) {
                val stale = profile.copy(status = ProfileStatus.STALE)
                profileCache[key] = stale
                saveProfile(stale)
                return stale
            }
        }
        return profile
    }

    fun getAllProfilesForPackage(packageName: String): List<FeatureProfile> {
        val lowerPkg = packageName.lowercase()
        return profileCache.values.filter { it.packageName.lowercase() == lowerPkg }
    }

    @Synchronized
    fun saveProfile(profile: FeatureProfile) {
        val key = makeKey(profile.packageName, profile.featureId)
        profileCache[key] = profile
        persistAll()
    }

    @Synchronized
    fun removeProfilesForPackage(packageName: String) {
        val lowerPkg = packageName.lowercase()
        val keysToRemove = profileCache.filter { it.value.packageName.lowercase() == lowerPkg }.keys
        for (k in keysToRemove) {
            profileCache.remove(k)
        }
        persistAll()
    }

    private fun makeKey(packageName: String, featureId: String): String {
        return "${packageName.lowercase()}:${featureId.lowercase()}"
    }

    private fun getAppVersionCode(packageName: String): Long {
        return try {
            val pm = context.packageManager
            val pInfo = pm.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            0L
        } catch (_: Throwable) {
            0L
        }
    }

    fun getAppVersionName(packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    @Synchronized
    private fun persistAll() {
        try {
            val array = JSONArray()
            for (p in profileCache.values) {
                val obj = JSONObject()
                obj.put("profileId", p.profileId)
                obj.put("packageName", p.packageName)
                obj.put("featureId", p.featureId)
                obj.put("schemaVersion", p.schemaVersion)
                obj.put("appVersionCode", p.appVersionCode)
                obj.put("appVersionName", p.appVersionName)
                obj.put("primaryScreenSignature", p.primaryScreenSignature)

                val altArray = JSONArray()
                p.alternateScreenSignatures.forEach { altArray.put(it) }
                obj.put("alternateScreenSignatures", altArray)

                val structArray = JSONArray()
                p.structuralSignatures.forEach { structArray.put(it) }
                obj.put("structuralSignatures", structArray)

                val idsArray = JSONArray()
                p.stableViewIds.forEach { idsArray.put(it) }
                obj.put("stableViewIds", idsArray)

                val tokensArray = JSONArray()
                p.stableTextTokens.forEach { tokensArray.put(it) }
                obj.put("stableTextTokens", tokensArray)

                val negArray = JSONArray()
                p.negativeSignals.forEach { negArray.put(it) }
                obj.put("negativeSignals", negArray)

                val confArray = JSONArray()
                p.conflictingFeatureIds.forEach { confArray.put(it) }
                obj.put("conflictingFeatureIds", confArray)

                obj.put("createdAt", p.createdAt)
                obj.put("lastVerifiedAt", p.lastVerifiedAt)
                obj.put("verificationCount", p.verificationCount)
                obj.put("status", p.status.name)

                array.put(obj)
            }
            prefs.edit().putString(KEY_PROFILES_JSON, array.toString()).apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Error persisting feature profiles sidecar: ${e.message}")
        }
    }

    private fun loadProfiles() {
        val raw = prefs.getString(KEY_PROFILES_JSON, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val profileId = obj.optString("profileId", "prof_$i")
                val pkg = obj.getString("packageName")
                val featureId = obj.getString("featureId")
                val schemaVersion = obj.optInt("schemaVersion", CURRENT_SCHEMA_VERSION)
                val appVersionCode = obj.optLong("appVersionCode", 0L)
                val appVersionName = obj.optString("appVersionName", "")
                val primarySig = obj.optString("primaryScreenSignature", "")

                val altSigs = mutableListOf<String>()
                obj.optJSONArray("alternateScreenSignatures")?.let { a ->
                    for (j in 0 until a.length()) altSigs.add(a.getString(j))
                }

                val structSigs = mutableListOf<String>()
                obj.optJSONArray("structuralSignatures")?.let { a ->
                    for (j in 0 until a.length()) structSigs.add(a.getString(j))
                }

                val viewIds = mutableSetOf<String>()
                obj.optJSONArray("stableViewIds")?.let { a ->
                    for (j in 0 until a.length()) viewIds.add(a.getString(j))
                }

                val textTokens = mutableSetOf<String>()
                obj.optJSONArray("stableTextTokens")?.let { a ->
                    for (j in 0 until a.length()) textTokens.add(a.getString(j))
                }

                val negSignals = mutableSetOf<String>()
                obj.optJSONArray("negativeSignals")?.let { a ->
                    for (j in 0 until a.length()) negSignals.add(a.getString(j))
                }

                val confIds = mutableSetOf<String>()
                obj.optJSONArray("conflictingFeatureIds")?.let { a ->
                    for (j in 0 until a.length()) confIds.add(a.getString(j))
                }

                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val lastVerifiedAt = obj.optLong("lastVerifiedAt", System.currentTimeMillis())
                val verificationCount = obj.optInt("verificationCount", 1)
                val statusStr = obj.optString("status", ProfileStatus.UNVERIFIED.name)
                val status = runCatching { ProfileStatus.valueOf(statusStr) }.getOrDefault(ProfileStatus.UNVERIFIED)

                val profile = FeatureProfile(
                    profileId = profileId,
                    packageName = pkg,
                    featureId = featureId,
                    schemaVersion = schemaVersion,
                    appVersionCode = appVersionCode,
                    appVersionName = appVersionName,
                    primaryScreenSignature = primarySig,
                    alternateScreenSignatures = altSigs,
                    structuralSignatures = structSigs,
                    stableViewIds = viewIds,
                    stableTextTokens = textTokens,
                    negativeSignals = negSignals,
                    conflictingFeatureIds = confIds,
                    createdAt = createdAt,
                    lastVerifiedAt = lastVerifiedAt,
                    verificationCount = verificationCount,
                    status = status
                )
                profileCache[makeKey(pkg, featureId)] = profile
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Feature profiles sidecar read warning: ${e.message}")
        }
    }
}
