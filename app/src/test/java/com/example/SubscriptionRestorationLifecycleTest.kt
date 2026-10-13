package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.recoveryshield.data.local.RecoveryDatabase
import com.example.recoveryshield.data.repository.CloudSubscriptionResult
import com.example.recoveryshield.data.repository.CloudSubscriptionSyncManager
import com.example.recoveryshield.data.repository.LocalProtectionRepository
import com.example.recoveryshield.domain.model.ProtectionPlanInfo
import com.example.recoveryshield.domain.model.ProtectionPlanType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubscriptionRestorationLifecycleTest {

    private lateinit var context: Context
    private lateinit var database: RecoveryDatabase
    private lateinit var repository: LocalProtectionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RecoveryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LocalProtectionRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `existing free trial with 66 hours remaining is restored exactly without restarting 3-day trial`() {
        val now = System.currentTimeMillis()
        val originalStarted = now - (6 * 3600 * 1000L) // 6 hours ago
        val originalExpiry = now + (66 * 3600 * 1000L) // 66 hours remaining

        val data = mapOf<String, Any?>(
            "planType" to "FREE_TRIAL",
            "trialStartedAt" to originalStarted,
            "trialExpiresAt" to originalExpiry,
            "protectionExpiresAt" to originalExpiry,
            "isPremiumActive" to false
        )

        val restored = CloudSubscriptionSyncManager.parsePlanFromMap(data)

        assertEquals(ProtectionPlanType.FREE_TRIAL, restored.planType)
        assertFalse(restored.isPremiumActive)
        assertEquals(originalStarted, restored.trialStartedAtMillis)
        assertEquals(originalExpiry, restored.trialExpiresAtMillis)
        assertEquals(originalExpiry, restored.protectionExpiresAtMillis)
        assertTrue(restored.isProtectionDayActive)
        // Verify remaining hours is around 66h, NOT 72h
        assertEquals(66L, restored.remainingHours)
    }

    @Test
    fun `premium subscription is restored exactly from cloud state`() {
        val now = System.currentTimeMillis()
        val premiumExpiry = now + (30L * 24 * 3600 * 1000L)

        val data = mapOf<String, Any?>(
            "planType" to "PREMIUM",
            "isPremiumActive" to true,
            "premiumExpiresAt" to premiumExpiry,
            "protectionExpiresAt" to premiumExpiry
        )

        val restored = CloudSubscriptionSyncManager.parsePlanFromMap(data)

        assertEquals(ProtectionPlanType.PREMIUM, restored.planType)
        assertTrue(restored.isPremiumActive)
        assertEquals(premiumExpiry, restored.premiumExpiresAtMillis)
        assertTrue(restored.isProtectionDayActive)
    }

    @Test
    fun `parsePlanFromMap handles polymorphic number types like Double or String without error`() {
        val data = mapOf<String, Any?>(
            "planType" to "FREE_TRIAL",
            // Double representation from web or JSON
            "trialStartedAt" to 1790699470223.0,
            "trialExpiresAt" to 1790958670223.0,
            "isPremiumActive" to false
        )

        val restored = CloudSubscriptionSyncManager.parsePlanFromMap(data)

        assertEquals(1790699470223L, restored.trialStartedAtMillis)
        assertEquals(1790958670223L, restored.trialExpiresAtMillis)
    }

    @Test
    fun `logout detaches local account subscription cache completely`() = runBlocking {
        val plan = ProtectionPlanInfo(
            planType = ProtectionPlanType.PREMIUM,
            isPremiumActive = true,
            premiumExpiresAtMillis = System.currentTimeMillis() + 86400000L
        )

        // Save plan for User A
        repository.saveProtectionPlan(plan)

        // User logs out
        repository.onUserLoggedOut()

        // Room database must be cleared
        val local = database.userProtectionPlanDao().getPlan()
        assertEquals(null, local)

        // observeProtectionPlan should emit empty unauthenticated plan
        val observed = repository.observeProtectionPlan().first()
        assertFalse(observed.isPremiumActive)
        assertEquals(0L, observed.trialExpiresAtMillis)
    }

    @Test
    fun `firestore read error does not overwrite state or create trial`() = runBlocking {
        val repoWithError = LocalProtectionRepository(
            database = database,
            context = context,
            cloudFetcher = { _, _ -> CloudSubscriptionResult.Error(RuntimeException("Network timeout")) }
        )

        // When a read error occurs, an empty unresolved plan is returned, NO trial is created in Room
        val unresolved = repoWithError.restoreOrResolveSubscriptionForAccount("unknown_user_uid", "test@gmail.com")
        // trialExpiresAtMillis must NOT be set to 3 days in the future
        assertEquals(0L, unresolved.trialExpiresAtMillis)
        assertFalse(unresolved.isPremiumActive)
        assertEquals(null, database.userProtectionPlanDao().getPlan())
    }

    @Test
    fun `restoreOrResolveSubscription restores existing trial and does not recreate trial`() = runBlocking {
        val now = System.currentTimeMillis()
        val originalStarted = now - (6 * 3600 * 1000L) // 6 hours ago
        val originalExpiry = now + (66 * 3600 * 1000L) // 66 hours remaining
        val existingPlan = ProtectionPlanInfo(
            planType = ProtectionPlanType.FREE_TRIAL,
            trialStartedAtMillis = originalStarted,
            trialExpiresAtMillis = originalExpiry,
            protectionExpiresAtMillis = originalExpiry,
            isPremiumActive = false
        )

        val repoWithExisting = LocalProtectionRepository(
            database = database,
            context = context,
            cloudFetcher = { _, _ -> CloudSubscriptionResult.Exists(existingPlan, emptyMap()) }
        )

        val restored = repoWithExisting.restoreOrResolveSubscriptionForAccount("user_trial_uid", "test@gmail.com")
        assertEquals(ProtectionPlanType.FREE_TRIAL, restored.planType)
        assertEquals(originalStarted, restored.trialStartedAtMillis)
        assertEquals(originalExpiry, restored.trialExpiresAtMillis)
        assertTrue(restored.remainingHours in 65L..66L)

        // Room database must be updated with the restored plan
        val saved = database.userProtectionPlanDao().getPlan()
        assertEquals(originalExpiry, saved?.trialExpiresAtMillis)
        assertEquals("user_trial_uid", saved?.accountUid)
    }

    @Test
    fun `restoreOrResolveSubscription restores premium and does not downgrade to trial`() = runBlocking {
        val premiumExpiry = System.currentTimeMillis() + (30L * 86400000L)
        val existingPremium = ProtectionPlanInfo(
            planType = ProtectionPlanType.PREMIUM,
            trialStartedAtMillis = 0L,
            trialExpiresAtMillis = 0L,
            protectionExpiresAtMillis = premiumExpiry,
            isPremiumActive = true,
            premiumExpiresAtMillis = premiumExpiry
        )

        val repoWithPremium = LocalProtectionRepository(
            database = database,
            context = context,
            cloudFetcher = { _, _ -> CloudSubscriptionResult.Exists(existingPremium, emptyMap()) }
        )

        val restored = repoWithPremium.restoreOrResolveSubscriptionForAccount("user_prem_uid", "test@gmail.com")
        assertEquals(ProtectionPlanType.PREMIUM, restored.planType)
        assertTrue(restored.isPremiumActive)
        assertEquals(premiumExpiry, restored.premiumExpiresAtMillis)

        val saved = database.userProtectionPlanDao().getPlan()
        assertEquals(true, saved?.isPremiumActive)
        assertEquals("user_prem_uid", saved?.accountUid)
    }
}
