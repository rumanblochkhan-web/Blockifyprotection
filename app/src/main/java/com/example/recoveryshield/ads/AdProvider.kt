package com.example.recoveryshield.ads

import kotlinx.coroutines.flow.StateFlow

sealed class AdShowResult {
    data object Completed : AdShowResult()
    data class Cancelled(val reason: String) : AdShowResult()
    data class Error(val message: String) : AdShowResult()
}

/**
 * Replaceable interface for rewarded video ad networks.
 * V1 provides a safe mock simulator for developer verification.
 */
interface AdProvider {
    val isAdLoaded: StateFlow<Boolean>
    suspend fun loadAd()
    suspend fun showRewardedAd(): AdShowResult
}

/**
 * Voluntary test implementation of AdProvider for local testing without external SDK tracking.
 */
class MockAdProvider : AdProvider {
    private val _isAdLoaded = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val isAdLoaded: StateFlow<Boolean> = _isAdLoaded

    override suspend fun loadAd() {
        _isAdLoaded.value = true
    }

    override suspend fun showRewardedAd(): AdShowResult {
        // Simulates voluntary ad completion for unit tests and local diagnostics
        return AdShowResult.Completed
    }
}
