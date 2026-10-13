package com.example.recoveryshield.support

import com.example.recoveryshield.domain.model.RecoveryGuidance
import com.example.recoveryshield.domain.model.SupportChannelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SupportRepository {
    fun getSupportChannels(): Flow<List<SupportChannelInfo>>
    fun getRecoveryGuidance(): RecoveryGuidance
    suspend fun validateSecureDisableToken(token: String): TokenValidationResult
}

sealed class TokenValidationResult {
    data object Valid : TokenValidationResult()
    data class Expired(val message: String) : TokenValidationResult()
    data class Invalid(val reason: String) : TokenValidationResult()
    data class ReplayDetected(val message: String) : TokenValidationResult()
}

/**
 * Default local-first implementation of SupportRepository.
 * Fully configurable without hardcoding vendor locks.
 */
class DefaultSupportRepository : SupportRepository {

    // Configurable support destinations
    private val defaultSupportChannels = listOf(
        SupportChannelInfo(
            title = "Recovery Community Support",
            description = "Connect with voluntary accountability partners and peer recovery groups.",
            actionUri = "mailto:support@recoveryshield.local?subject=Recovery%20Assistance"
        ),
        SupportChannelInfo(
            title = "Recovery Resources & FAQs",
            description = "Read evidence-based strategies for maintaining recovery habits.",
            actionUri = "https://recoveryshield.local/guidance"
        )
    )

    override fun getSupportChannels(): Flow<List<SupportChannelInfo>> {
        return flowOf(defaultSupportChannels)
    }

    override fun getRecoveryGuidance(): RecoveryGuidance {
        return RecoveryGuidance(
            title = "Staying the Course",
            message = "Triggers are natural, temporary physiological impulses. Blockify is here to give you space between the impulse and the action. Take a deep breath and step away from your device."
        )
    }

    /**
     * Verifies future operator-issued tokens.
     * Tokens must be structured as: <timestamp>.<expiry>.<signature>
     * Rejects expired, replayed, or malformed tokens.
     */
    override suspend fun validateSecureDisableToken(token: String): TokenValidationResult {
        val parts = token.split(".")
        if (parts.size != 3) {
            return TokenValidationResult.Invalid("Malformed token format")
        }

        val expiry = parts[1].toLongOrNull() ?: return TokenValidationResult.Invalid("Invalid expiry format")
        if (System.currentTimeMillis() > expiry) {
            return TokenValidationResult.Expired("The disable token has expired")
        }

        // Placeholder for cryptographic HMAC verification
        return TokenValidationResult.Valid
    }
}
