package com.example.recoveryshield.data.repository

import android.content.Context
import android.util.Log
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionPlanType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

sealed class CloudSubscriptionResult {
    data class Exists(val plan: ProtectionPlanInfo, val rawData: Map<String, Any>?) : CloudSubscriptionResult()
    data class NotFound(val uid: String) : CloudSubscriptionResult()
    data class Error(val cause: Throwable) : CloudSubscriptionResult()
    object NotAuthenticated : CloudSubscriptionResult()
}

/**
 * Authoritative cloud subscription manager communicating with Cloud Firestore.
 * Strictly adheres to account identity using Firebase Authentication UID as the primary key.
 * Document Path: subscriptions/{firebaseUID}
 */
object CloudSubscriptionSyncManager {
    private const val TAG = "CloudSubscriptionSync"
    const val COLLECTION_SUBSCRIPTIONS = "subscriptions"

    /**
     * Retrieves the authenticated Firebase User ID (UID).
     * Never derives document identity from email addresses.
     */
    fun getAuthenticatedUid(): String? {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && user.uid.isNotBlank()) user.uid else null
        } catch (e: Throwable) {
            Log.w(TAG, "Error resolving authenticated Firebase user: ${e.message}")
            null
        }
    }

    private fun sanitizeEmailToKey(email: String): String {
        return email.lowercase().trim()
            .replace(".", "_")
            .replace("@", "_at_")
    }

    fun getFirestore(context: Context? = null): FirebaseFirestore {
        val ctx = context ?: try { com.example.BlockifyApplication.instance } catch (_: Throwable) { null }
        val dbId = if (ctx != null) {
            try { ctx.getString(com.example.R.string.firestore_database_id) } catch (_: Throwable) { null }
        } else null
        return if (!dbId.isNullOrBlank() && dbId != "(default)" && !dbId.startsWith("ai-studio-android-blokify-")) {
            try {
                FirebaseFirestore.getInstance(dbId)
            } catch (_: Throwable) {
                FirebaseFirestore.getInstance()
            }
        } else {
            FirebaseFirestore.getInstance()
        }
    }

    /**
     * Parses a Firestore document snapshot into an authoritative ProtectionPlanInfo model.
     * Accurately supports both camelCase and legacy field definitions, as well as polymorphic number types.
     */
    fun parsePlanFromSnapshot(doc: DocumentSnapshot): ProtectionPlanInfo {
        return parsePlanFromMap(doc.data ?: emptyMap())
    }

    /**
     * Parses a map of subscription attributes into an authoritative ProtectionPlanInfo model.
     */
    fun parsePlanFromMap(data: Map<String, Any?>): ProtectionPlanInfo {
        fun getFieldAsLong(vararg fieldNames: String, defaultVal: Long = 0L): Long {
            for (name in fieldNames) {
                val v = data[name]
                when (v) {
                    is Number -> return v.toLong()
                    is com.google.firebase.Timestamp -> return v.toDate().time
                    is String -> {
                        val p = v.toLongOrNull()
                        if (p != null) return p
                    }
                }
            }
            return defaultVal
        }

        fun getFieldAsBoolean(vararg fieldNames: String, defaultVal: Boolean = false): Boolean {
            for (name in fieldNames) {
                val v = data[name]
                if (v is Boolean) return v
                val str = v?.toString()
                if (str != null) {
                    val parsed = str.toBooleanStrictOrNull()
                    if (parsed != null) return parsed
                }
            }
            return defaultVal
        }

        val rawPlanTypeStr = (data["planType"] ?: data["basePlanId"] ?: data["productId"] ?: ProtectionPlanType.FREE_TRIAL.name).toString()
        val parsedPlanType = runCatching { ProtectionPlanType.valueOf(rawPlanTypeStr) }.getOrDefault(ProtectionPlanType.FREE_TRIAL)

        val trialStartedAt = getFieldAsLong("trialStartedAt", "trialStartedAtMillis", "trial_started", defaultVal = 0L)
        val trialExpiresAt = getFieldAsLong("trialExpiresAt", "trialExpiresAtMillis", "trial_expires", defaultVal = 0L)
        val protectionExpiresAt = getFieldAsLong("protectionExpiresAt", "protectionExpiresAtMillis", "protection_expires", defaultVal = trialExpiresAt)
        val premiumExpiresAt = getFieldAsLong("premiumExpiresAt", "premiumExpiresAtMillis", "premium_expires", defaultVal = 0L)
        val lastRewardedAdAt = getFieldAsLong("lastRewardedAdAtMillis", "lastRewardedAdAt", "last_rewarded_ad", defaultVal = 0L)

        val rawIsPremium = getFieldAsBoolean("isPremiumActive", "is_premium", defaultVal = false)
        val now = System.currentTimeMillis()
        val isPremium = rawIsPremium || parsedPlanType == ProtectionPlanType.PREMIUM || (premiumExpiresAt > now)

        val finalPlanType = when {
            isPremium -> ProtectionPlanType.PREMIUM
            parsedPlanType == ProtectionPlanType.REWARD_EXTENDED -> ProtectionPlanType.REWARD_EXTENDED
            else -> ProtectionPlanType.FREE_TRIAL
        }

        val finalProtectionExpires = when {
            isPremium && premiumExpiresAt > 0L -> maxOf(protectionExpiresAt, premiumExpiresAt)
            trialExpiresAt > 0L -> maxOf(protectionExpiresAt, trialExpiresAt)
            else -> protectionExpiresAt
        }

        return ProtectionPlanInfo(
            planType = finalPlanType,
            trialStartedAtMillis = trialStartedAt,
            trialExpiresAtMillis = trialExpiresAt,
            protectionExpiresAtMillis = finalProtectionExpires,
            isPremiumActive = isPremium,
            premiumExpiresAtMillis = if (premiumExpiresAt > 0L) premiumExpiresAt else null,
            lastRewardedAdAtMillis = if (lastRewardedAdAt > 0L) lastRewardedAdAt else null
        )
    }

    /**
     * Reads subscriptions/{uid} strictly from Cloud Firestore.
     * If document is missing under UID, checks legacy email document for migration to preserve user purchases.
     * Distinguishes explicitly between document exists, document missing, and read error.
     */
    suspend fun fetchCloudSubscription(
        uid: String,
        email: String? = null,
        context: Context? = null
    ): CloudSubscriptionResult {
        if (uid.isBlank()) {
            return CloudSubscriptionResult.NotAuthenticated
        }

        val db = getFirestore(context)
        return try {
            val docRef = db.collection(COLLECTION_SUBSCRIPTIONS).document(uid)
            val doc = docRef.get().await()

            if (doc.exists()) {
                val plan = parsePlanFromSnapshot(doc)
                Log.i(TAG, "Authoritative subscription restored for UID $uid: plan=${plan.planType}, isPremium=${plan.isPremiumActive}, trialExpires=${plan.trialExpiresAtMillis}")
                CloudSubscriptionResult.Exists(plan, doc.data)
            } else {
                // Backward-compatibility migration check:
                // Check if user had a previous subscription under sanitized email key or query by email
                val currentEmail = email ?: FirebaseAuth.getInstance().currentUser?.email
                if (!currentEmail.isNullOrBlank()) {
                    val legacyKey = sanitizeEmailToKey(currentEmail)
                    try {
                        val legacyDoc = db.collection(COLLECTION_SUBSCRIPTIONS).document(legacyKey).get().await()
                        if (legacyDoc.exists()) {
                            Log.i(TAG, "Migrating legacy email subscription ($legacyKey) to authoritative UID path ($uid)")
                            val migratedPlan = parsePlanFromSnapshot(legacyDoc)
                            // Write to UID document to migrate permanently
                            syncPlanToCloud(uid, migratedPlan, currentEmail, context)
                            return CloudSubscriptionResult.Exists(migratedPlan, legacyDoc.data)
                        }
                    } catch (e: Throwable) {
                        Log.d(TAG, "Legacy migration check bypassed: ${e.message}")
                    }

                    // Check by email field query
                    try {
                        val querySnap = db.collection(COLLECTION_SUBSCRIPTIONS)
                            .whereEqualTo("email", currentEmail.trim().lowercase())
                            .limit(1)
                            .get()
                            .await()
                        if (!querySnap.isEmpty) {
                            val matchedDoc = querySnap.documents[0]
                            Log.i(TAG, "Found subscription via email query (${matchedDoc.id}) for UID $uid")
                            val migratedPlan = parsePlanFromSnapshot(matchedDoc)
                            syncPlanToCloud(uid, migratedPlan, currentEmail, context)
                            return CloudSubscriptionResult.Exists(migratedPlan, matchedDoc.data)
                        }
                    } catch (e: Throwable) {
                        Log.d(TAG, "Email query check bypassed: ${e.message}")
                    }
                }

                // Explicit confirmation that document does not exist in Firestore
                Log.i(TAG, "Confirmed: subscriptions/$uid does not exist in Firestore")
                CloudSubscriptionResult.NotFound(uid)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore read failure for subscriptions/$uid: ${e.message}")
            CloudSubscriptionResult.Error(e)
        }
    }

    /**
     * Atomically creates the initial free trial for a verified first-time account.
     * Uses a Firestore transaction to ensure that if a document appears concurrently,
     * it will NEVER overwrite existing state.
     */
    suspend fun createInitialTrialAtomically(
        uid: String,
        email: String,
        context: Context? = null
    ): ProtectionPlanInfo {
        require(uid.isNotBlank()) { "Cannot create initial trial without valid authenticated UID" }
        val db = getFirestore(context)
        val docRef = db.collection(COLLECTION_SUBSCRIPTIONS).document(uid)

        return db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (snapshot.exists()) {
                Log.i(TAG, "Document already exists during transaction for $uid; restoring existing state instead of overwriting.")
                parsePlanFromSnapshot(snapshot)
            } else {
                val now = System.currentTimeMillis()
                val twentyDaysMillis = 20L * 24L * 60L * 60L * 1000L
                val trialExpires = now + twentyDaysMillis

                val data = hashMapOf<String, Any>(
                    "uid" to uid,
                    "email" to email,
                    "planType" to ProtectionPlanType.FREE_TRIAL.name,
                    "trialClaimed" to true,
                    "trialStartedAt" to now,
                    "trialStartedAtMillis" to now,
                    "trialExpiresAt" to trialExpires,
                    "trialExpiresAtMillis" to trialExpires,
                    "protectionExpiresAt" to trialExpires,
                    "protectionExpiresAtMillis" to trialExpires,
                    "isPremiumActive" to false,
                    "premiumExpiresAt" to 0L,
                    "premiumExpiresAtMillis" to 0L,
                    "productId" to "free_trial_3d",
                    "basePlanId" to "free_trial",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "updatedAtMillis" to now
                )

                transaction.set(docRef, data)
                Log.i(TAG, "Atomic initial trial created in Firestore for UID $uid. Expires: $trialExpires")
                ProtectionPlanInfo(
                    planType = ProtectionPlanType.FREE_TRIAL,
                    trialStartedAtMillis = now,
                    trialExpiresAtMillis = trialExpires,
                    protectionExpiresAtMillis = trialExpires,
                    isPremiumActive = false
                )
            }
        }.await()
    }

    /**
     * Updates subscription state in Cloud Firestore using SetOptions.merge()
     * to prevent blind replacement or data loss of unmapped fields.
     */
    suspend fun syncPlanToCloud(
        uid: String,
        plan: ProtectionPlanInfo,
        email: String? = null,
        context: Context? = null
    ) {
        if (uid.isBlank()) {
            Log.w(TAG, "Cannot sync plan to cloud: UID is empty")
            return
        }

        // Prevent local default state from being saved to firestore before cloud restore
        if (plan.planType == ProtectionPlanType.FREE_TRIAL && plan.trialExpiresAtMillis == 0L) {
            Log.d(TAG, "Skipping cloud sync: uninitialized default plan state will not overwrite cloud")
            return
        }

        try {
            val db = getFirestore(context)
            val docRef = db.collection(COLLECTION_SUBSCRIPTIONS).document(uid)
            val finalEmail = email ?: FirebaseAuth.getInstance().currentUser?.email ?: ""

            val data = hashMapOf<String, Any>(
                "uid" to uid,
                "email" to finalEmail,
                "planType" to plan.planType.name,
                "trialStartedAt" to plan.trialStartedAtMillis,
                "trialStartedAtMillis" to plan.trialStartedAtMillis,
                "trialExpiresAt" to plan.trialExpiresAtMillis,
                "trialExpiresAtMillis" to plan.trialExpiresAtMillis,
                "protectionExpiresAt" to plan.protectionExpiresAtMillis,
                "protectionExpiresAtMillis" to plan.protectionExpiresAtMillis,
                "isPremiumActive" to plan.isPremiumActive,
                "productId" to if (plan.isPremiumActive) "premium_monthly" else "free_trial_3d",
                "basePlanId" to if (plan.isPremiumActive) "premium" else "free_trial",
                "premiumExpiresAt" to (plan.premiumExpiresAtMillis ?: 0L),
                "premiumExpiresAtMillis" to (plan.premiumExpiresAtMillis ?: 0L),
                "lastRewardedAdAtMillis" to (plan.lastRewardedAdAtMillis ?: 0L),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedAtMillis" to System.currentTimeMillis()
            )

            docRef.set(data, SetOptions.merge()).await()
            Log.i(TAG, "Successfully synced authoritative subscription to subscriptions/$uid: isPremium=${plan.isPremiumActive}")
        } catch (e: Throwable) {
            Log.w(TAG, "Sync to Firestore deferred (network/offline): ${e.message}")
        }
    }
}
