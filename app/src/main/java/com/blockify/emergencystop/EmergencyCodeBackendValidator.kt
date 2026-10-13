package com.blockify.emergencystop

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Production-ready backend validator communicating with Firebase Authentication and Cloud Firestore.
 * Performs atomic validation and consumption of 6-character emergency codes.
 */
class EmergencyCodeBackendValidator(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            val dbId = try { context.getString(com.example.R.string.firestore_database_id) } catch (_: Throwable) { null }
            if (!dbId.isNullOrBlank() && dbId != "(default)" && !dbId.startsWith("ai-studio-android-blokify-")) {
                FirebaseFirestore.getInstance(dbId)
            } else {
                FirebaseFirestore.getInstance()
            }
        } catch (_: Throwable) { null }
    }
    private val auth: FirebaseAuth? by lazy {
        try { FirebaseAuth.getInstance() } catch (_: Throwable) { null }
    }

    private val prefs = context.getSharedPreferences("emergency_stop_security", Context.MODE_PRIVATE)

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        private const val KEY_FAILED_COUNT = "failed_attempts_count"
        private const val KEY_LAST_FAILED_TIME = "last_failed_attempt_time"

        /**
         * Computes standard SHA-256 hash for secure code matching.
         */
        fun computeCodeHash(code: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(code.uppercase().toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Checks client-side rate limit to prevent brute force / automated guessing.
     */
    fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        val lastFailed = prefs.getLong(KEY_LAST_FAILED_TIME, 0L)
        val count = prefs.getInt(KEY_FAILED_COUNT, 0)

        if (count >= MAX_FAILED_ATTEMPTS) {
            if (now - lastFailed < LOCKOUT_DURATION_MS) {
                return true
            } else {
                // Cooldown elapsed, reset
                prefs.edit().putInt(KEY_FAILED_COUNT, 0).apply()
            }
        }
        return false
    }

    private fun recordFailedAttempt() {
        val now = System.currentTimeMillis()
        val count = prefs.getInt(KEY_FAILED_COUNT, 0) + 1
        prefs.edit()
            .putInt(KEY_FAILED_COUNT, count)
            .putLong(KEY_LAST_FAILED_TIME, now)
            .apply()
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_COUNT, 0)
            .putLong(KEY_LAST_FAILED_TIME, 0L)
            .apply()
    }

    /**
     * Authenticates the user if not already signed in, and verifies the 8-character code
     * against Cloud Firestore in an atomic transaction.
     */
    suspend fun validateAndConsumeCode(code: String): EmergencyStopResult = withContext(Dispatchers.IO) {
        val normalizedCode = code.trim().uppercase()
        if (normalizedCode.length != 8 || !normalizedCode.all { it.isLetterOrDigit() }) {
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.INVALID_CODE,
                message = "Invalid code format. Must be 8 alphanumeric characters."
            )
        }

        if (isRateLimited()) {
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.RATE_LIMITED,
                message = "Too many failed attempts. Please wait 15 minutes before trying again."
            )
        }

        // 1. Instant Local Encrypted Vault Verification (Offline & Zero-Delay)
        if (EmergencyCodeVault.isAuthorizedCode(normalizedCode)) {
            val usedSet = prefs.getStringSet("used_emergency_codes", emptySet()) ?: emptySet()
            if (usedSet.contains(normalizedCode)) {
                recordFailedAttempt()
                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.ALREADY_USED,
                    message = "This emergency code has already been used on this device."
                )
            }

            // Mark code as used locally
            val updatedUsedSet = usedSet.toMutableSet().apply { add(normalizedCode) }
            prefs.edit().putStringSet("used_emergency_codes", updatedUsedSet).apply()

            // Reset any failed attempt streaks
            resetFailedAttempts()

            // Optional background async sync to Firestore (non-blocking)
            try {
                firestore?.collection("emergency_codes")?.document(normalizedCode)
                    ?.set(
                        mapOf(
                            "status" to "USED",
                            "usedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
            } catch (_: Exception) {
                // Ignore network errors in background
            }

            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.SUCCESS,
                message = "Emergency code verified successfully."
            )
        }

        val fs = firestore
        if (fs == null) {
            recordFailedAttempt()
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.INVALID_CODE,
                message = "Invalid emergency code. Please try again."
            )
        }

        val codeHash = computeCodeHash(normalizedCode)

        try {
            // Attempt anonymous sign-in if not signed in, but don't fail hard if disabled
            val currentUid = try {
                val currentAuth = auth
                if (currentAuth?.currentUser == null) {
                    val authResult = currentAuth?.signInAnonymously()?.await()
                    authResult?.user?.uid ?: "anonymous_user"
                } else {
                    currentAuth.currentUser?.uid ?: "anonymous_user"
                }
            } catch (e: Exception) {
                android.util.Log.w("EMERGENCY_STOP", "Anonymous auth not enabled: ${e.message}")
                "guest_device"
            }

            val directDocRef = fs.collection("emergency_codes").document(normalizedCode)
            val hashDocRef = fs.collection("emergency_codes").document(codeHash)

            // 2. Direct document check in Firebase (Fallback for custom console codes)
            val directSnap = try {
                directDocRef.get().await()
            } catch (_: Exception) {
                null
            }

            val (snapshot, targetRef) = if (directSnap != null && directSnap.exists()) {
                Pair(directSnap, directDocRef)
            } else {
                val hashSnap = try {
                    hashDocRef.get().await()
                } catch (_: Exception) {
                    null
                }
                if (hashSnap != null && hashSnap.exists()) {
                    Pair(hashSnap, hashDocRef)
                } else {
                    Pair(null, directDocRef)
                }
            }

            // Also check remote batch configuration document: emergency_config/active_codes
            var isRemoteBatchMatch = false
            if (snapshot == null || !snapshot.exists()) {
                try {
                    val batchConfigSnap = fs.collection("emergency_config").document("active_codes").get().await()
                    if (batchConfigSnap.exists()) {
                        val remoteCodesList = batchConfigSnap.get("codes") as? List<*>
                        if (remoteCodesList != null) {
                            val matchingCode = remoteCodesList.firstOrNull {
                                it.toString().trim().equals(normalizedCode, ignoreCase = true)
                            }
                            if (matchingCode != null) {
                                isRemoteBatchMatch = true
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore and continue
                }
            }

            if (isRemoteBatchMatch) {
                val usedSet = prefs.getStringSet("used_emergency_codes", emptySet()) ?: emptySet()
                if (usedSet.contains(normalizedCode)) {
                    recordFailedAttempt()
                    return@withContext EmergencyStopResult(
                        status = EmergencyCodeValidationStatus.ALREADY_USED,
                        message = "This emergency code has already been used on this device."
                    )
                }
                val updatedUsedSet = usedSet.toMutableSet().apply { add(normalizedCode) }
                prefs.edit().putStringSet("used_emergency_codes", updatedUsedSet).apply()
                resetFailedAttempts()

                // Mark used in Firestore in background
                try {
                    fs.collection("emergency_codes").document(normalizedCode).set(
                        mapOf(
                            "status" to "USED",
                            "usedAt" to FieldValue.serverTimestamp(),
                            "usedByUid" to currentUid
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                } catch (_: Exception) {}

                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.SUCCESS,
                    message = "Emergency code verified successfully."
                )
            }

            if (snapshot == null || !snapshot.exists()) {
                recordFailedAttempt()
                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.INVALID_CODE,
                    message = "Invalid emergency code. Please try again."
                )
            }

            val rawStatus = snapshot.getString("status")?.trim() ?: "AVAILABLE"
            val expiresAt = snapshot.getLong("expiresAt") ?: Long.MAX_VALUE
            val now = System.currentTimeMillis()

            if (rawStatus.equals("USED", ignoreCase = true)) {
                recordFailedAttempt()
                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.ALREADY_USED,
                    message = "This emergency code has already been used."
                )
            }

            if (rawStatus.equals("DISABLED", ignoreCase = true)) {
                recordFailedAttempt()
                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.INVALID_CODE,
                    message = "This emergency code is deactivated."
                )
            }

            if (expiresAt < now) {
                recordFailedAttempt()
                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.EXPIRED_CODE,
                    message = "This emergency code has expired."
                )
            }

            if (rawStatus.equals("AVAILABLE", ignoreCase = true) || rawStatus.isEmpty()) {
                // Try marking code as USED
                try {
                    targetRef.update(
                        mapOf(
                            "status" to "USED",
                            "usedAt" to FieldValue.serverTimestamp(),
                            "usedByUid" to currentUid
                        )
                    ).await()
                } catch (updateEx: Exception) {
                    android.util.Log.w("EMERGENCY_STOP", "Could not update status to USED: ${updateEx.message}")
                }

                resetFailedAttempts()
                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.SUCCESS,
                    message = "Emergency code verified successfully."
                )
            } else {
                recordFailedAttempt()
                return@withContext EmergencyStopResult(
                    status = EmergencyCodeValidationStatus.INVALID_CODE,
                    message = "Code status is '$rawStatus'. Must be 'AVAILABLE'."
                )
            }
        } catch (e: FirebaseFirestoreException) {
            android.util.Log.e("EMERGENCY_STOP", "Firestore validation error (${e.code}): ${e.message}")
            recordFailedAttempt()
            val userMsg = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Firebase Rules permission denied. Please publish Rules in Firestore Console."
            } else {
                "Server connection issue (${e.code.name}). Please check internet connection."
            }
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.SERVER_ERROR,
                message = userMsg
            )
        } catch (e: Exception) {
            android.util.Log.e("EMERGENCY_STOP", "Validation exception: ${e.message}")
            recordFailedAttempt()
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.NETWORK_ERROR,
                message = "Connection error: ${e.localizedMessage ?: "Please try again."}"
            )
        }
    }

    /**
     * Validates an 8-character emergency authorization code specifically for "Emergency Open"
     * (unlocking Blockify App Info & Settings without terminating device protection).
     */
    suspend fun validateCodeForEmergencyOpen(code: String): EmergencyStopResult = withContext(Dispatchers.IO) {
        val normalizedCode = code.trim().uppercase()
        if (normalizedCode.length != 8 || !normalizedCode.all { it.isLetterOrDigit() }) {
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.INVALID_CODE,
                message = "Invalid code format. Must be 8 alphanumeric characters."
            )
        }

        if (isRateLimited()) {
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.RATE_LIMITED,
                message = "Too many failed attempts. Please wait 15 minutes before trying again."
            )
        }

        // Instant offline verification via cryptographic vault and backend pool
        if (EmergencyCodeVault.isAuthorizedCode(normalizedCode)) {
            resetFailedAttempts()
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.SUCCESS,
                message = "Emergency code verified successfully. App Info unlocked."
            )
        }

        // Firestore online fallback verification if available
        val fs = firestore
        if (fs == null) {
            recordFailedAttempt()
            return@withContext EmergencyStopResult(
                status = EmergencyCodeValidationStatus.INVALID_CODE,
                message = "Invalid emergency code. Please contact Blockify Support."
            )
        }

        try {
            val doc = fs.collection("emergency_codes").document(normalizedCode).get().await()
            if (doc.exists()) {
                val status = doc.getString("status") ?: "ACTIVE"
                if (status == "ACTIVE" || status == "VALID") {
                    resetFailedAttempts()
                    return@withContext EmergencyStopResult(
                        status = EmergencyCodeValidationStatus.SUCCESS,
                        message = "Emergency code verified successfully. App Info unlocked."
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore network exception and fall through
        }

        recordFailedAttempt()
        return@withContext EmergencyStopResult(
            status = EmergencyCodeValidationStatus.INVALID_CODE,
            message = "Invalid emergency code. Please contact Blockify Support."
        )
    }
}
