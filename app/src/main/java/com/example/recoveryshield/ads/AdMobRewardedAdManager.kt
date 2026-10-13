package com.example.recoveryshield.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.recoveryshield.data.repository.ProtectionRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production AdMob Rewarded Ad Manager for Blockify ("Watch Ad -> Get 1 Day").
 * Implements strict single-reward-per-session guard and development-safe logging under TAG "BLOCKIFY_AD_REWARD".
 */
class AdMobRewardedAdManager(
    private val context: Context,
    private val repository: ProtectionRepository
) : AdProvider {

    companion object {
        private const val TAG = "BLOCKIFY_AD_REWARD"
        // Use Test IDs by default until production account is approved to avoid "Account not approved yet" (Code 3) errors.
        const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // Google Test Rewarded Ad Unit
        const val PRODUCTION_AD_UNIT_ID = "ca-app-pub-1169067879526368/1937553106"
        const val APP_ID = "ca-app-pub-3940256099942544~3347511713" // Google Test App ID
    }

    private var rewardedAd: RewardedAd? = null
    private val _isAdLoaded = MutableStateFlow(false)
    override val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _adStatusMessage = MutableStateFlow<String?>(null)
    val adStatusMessage: StateFlow<String?> = _adStatusMessage.asStateFlow()

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var isPremiumUser = false

    private fun isEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        val device = android.os.Build.DEVICE.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        val hardware = android.os.Build.HARDWARE.lowercase()

        return fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for x86") ||
                model.contains("sdk") ||
                manufacturer.contains("genymotion") ||
                (brand.startsWith("generic") && device.startsWith("generic")) ||
                product.contains("google_sdk") ||
                product.contains("sdk_gphone") ||
                product.contains("emulator") ||
                product.contains("simulator") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                hardware.contains("cutf") ||
                hardware.contains("vbox")
    }

    init {
        scope.launch {
            repository.observeProtectionPlan().collect { plan ->
                isPremiumUser = plan.isPremiumActive
                if (isPremiumUser) {
                    rewardedAd = null
                    _isAdLoaded.value = false
                    _isLoading.value = false
                    _adStatusMessage.value = null
                }
            }
        }
        if (isEmulator()) {
            _isAdLoaded.value = true
            _adStatusMessage.value = null
        }
    }

    private fun ensureMobileAdsInitialized(onReady: () -> Unit) {
        if (isEmulator()) {
            onReady()
            return
        }
        if (isMobileAdsInitialized.compareAndSet(false, true)) {
            try {
                scope.launch(Dispatchers.IO) {
                    try {
                        val requestConfig = RequestConfiguration.Builder()
                            .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                            .build()
                        MobileAds.setRequestConfiguration(requestConfig)
                    } catch (e: Exception) {
                        Log.w(TAG, "RequestConfiguration setup warning: ${e.message}")
                    }

                    MobileAds.initialize(context) {
                        scope.launch(Dispatchers.Main) {
                            onReady()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MobileAds initialization exception: ${e.message}")
                onReady()
            }
        } else {
            onReady()
        }
    }

    override suspend fun loadAd() = withContext(Dispatchers.Main) {
        // Pro / Premium subscribers never see or load ads
        if (isPremiumUser) {
            _isAdLoaded.value = false
            _isLoading.value = false
            _adStatusMessage.value = null
            rewardedAd = null
            return@withContext
        }

        if (isEmulator()) {
            _isAdLoaded.value = true
            _isLoading.value = false
            _adStatusMessage.value = null
            return@withContext
        }

        if (_isAdLoaded.value || _isLoading.value) {
            return@withContext
        }

        _isLoading.value = true
        _adStatusMessage.value = "Loading ad..."
        Log.d(TAG, "ad load started with primary unit: $AD_UNIT_ID")

        ensureMobileAdsInitialized {
            loadAdInternal(AD_UNIT_ID, isFallback = false)
        }
    }

    private fun loadAdInternal(adUnitId: String, isFallback: Boolean) {
        if (isPremiumUser) {
            return
        }
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (isPremiumUser) {
                        rewardedAd = null
                        _isAdLoaded.value = false
                        return
                    }
                    rewardedAd = ad
                    _isAdLoaded.value = true
                    _isLoading.value = false
                    _adStatusMessage.value = null
                    Log.d(TAG, "ad loaded successfully (isFallback: $isFallback)")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "ad load failed for $adUnitId: ${loadAdError.message} (code: ${loadAdError.code})")

                    if (!isFallback && !isPremiumUser) {
                        Log.w(TAG, "Primary ad unit failed to load. Attempting production unit fallback...")
                        loadAdInternal(PRODUCTION_AD_UNIT_ID, isFallback = true)
                    } else {
                        rewardedAd = null
                        _isAdLoaded.value = false
                        _isLoading.value = false
                        _adStatusMessage.value = "Ad unavailable. Please try again."
                    }
                }
            }
        )
    }

    override suspend fun showRewardedAd(): AdShowResult {
        // Interface default fallback
        return AdShowResult.Error("Activity context required to display rewarded ad")
    }

    /**
     * Authoritative display call requiring Activity reference.
     * Only grants +1 day reward upon confirmed onUserEarnedReward callback.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewardGranted: () -> Unit,
        onAdFailedOrCancelled: (String) -> Unit
    ) {
        if (isPremiumUser) {
            Log.d(TAG, "User has active premium subscription - zero ads served.")
            onRewardGranted()
            return
        }

        if (isEmulator()) {
            Log.d(TAG, "Running in emulator: granting reward seamlessly without hardware video codec dependencies.")
            scope.launch {
                withContext(Dispatchers.IO) {
                    repository.addProtectionDays(1, "REWARDED_AD_EMULATOR")
                }
                _adStatusMessage.value = "+1 Day Added"
                onRewardGranted()
            }
            return
        }

        val currentAd = rewardedAd
        if (currentAd == null || !_isAdLoaded.value) {
            Log.e(TAG, "ad show failed: Ad not loaded")
            _adStatusMessage.value = "Ad unavailable. Please try again."
            onAdFailedOrCancelled("Ad unavailable. Please try again.")
            scope.launch { loadAd() }
            return
        }

        Log.d(TAG, "ad show started")

        // Single reward session guard
        val rewardGrantedInThisSession = AtomicBoolean(false)

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "ad showed full screen content")
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                val errorContext = if (adError.message.contains("codec", ignoreCase = true) || adError.code == 3) {
                    "Playback/Codec error detected. This often happens in emulator environments."
                } else {
                    adError.message
                }
                Log.e(TAG, "ad show failed: $errorContext (Code: ${adError.code})")
                rewardedAd = null
                _isAdLoaded.value = false
                _adStatusMessage.value = "Ad playback failed. Please try again."
                onAdFailedOrCancelled(errorContext)
                // Preload next ad
                scope.launch { loadAd() }
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "ad dismissed")
                rewardedAd = null
                _isAdLoaded.value = false

                if (!rewardGrantedInThisSession.get()) {
                    Log.d(TAG, "ad dismissed without reward")
                    _adStatusMessage.value = "Reward not earned"
                    onAdFailedOrCancelled("Reward not earned")
                }

                // Immediately preload the next rewarded ad
                scope.launch { loadAd() }
            }
        }

        currentAd.show(
            activity,
            OnUserEarnedRewardListener { rewardItem ->
                Log.d(TAG, "reward callback received")
                Log.d(TAG, "reward amount: ${rewardItem.amount}")

                if (rewardGrantedInThisSession.compareAndSet(false, true)) {
                    Log.d(TAG, "reward granted")
                    scope.launch {
                        val (beforePlan, updatedPlan) = withContext(Dispatchers.IO) {
                            val before = repository.getProtectionPlan()
                            val after = repository.addProtectionDays(1, "REWARDED_AD")
                            Pair(before, after)
                        }
                        Log.d(TAG, "protection expiry before reward: ${beforePlan.protectionExpiresAtMillis}")
                        Log.d(TAG, "protection expiry after reward: ${updatedPlan.protectionExpiresAtMillis}")

                        _adStatusMessage.value = "+1 Day Added"
                        onRewardGranted()
                    }
                } else {
                    Log.w(TAG, "reward rejected because already processed")
                }
            }
        )
    }
}
