package com.example.recoveryshield.protection.disable

import com.example.recoveryshield.data.repository.ProtectionRepository
import com.example.recoveryshield.domain.model.CooldownState
import com.example.recoveryshield.domain.model.DisableRequest
import com.example.recoveryshield.domain.model.DisableRequestStatus
import com.example.recoveryshield.domain.model.ProtectionState
import com.example.recoveryshield.domain.model.SafeEventCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class DisableProtectionManager(
    private val repository: ProtectionRepository,
    private val onStateChanged: suspend (ProtectionState) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _cooldownState = MutableStateFlow(CooldownState())
    val cooldownState: StateFlow<CooldownState> = _cooldownState.asStateFlow()

    companion object {
        const val DEFAULT_COOLDOWN_DURATION_MS = 2 * 60 * 60 * 1000L // 2 hours
    }

    init {
        // Observe active request and run ticker
        scope.launch {
            repository.observeActiveDisableRequest().collect { activeRequest ->
                if (activeRequest != null && activeRequest.status == DisableRequestStatus.PENDING_COOLDOWN) {
                    reconcileCooldown(activeRequest)
                } else if (activeRequest != null && activeRequest.status == DisableRequestStatus.EXPIRED_AWAITING_CONFIRMATION) {
                    _cooldownState.value = CooldownState(
                        hasActiveRequest = true,
                        requestId = activeRequest.requestId,
                        requestedAtMillis = activeRequest.createdAtMillis,
                        expiresAtMillis = activeRequest.expiresAtMillis,
                        remainingMillis = 0L,
                        isExpiredAwaitingConfirmation = true,
                        status = DisableRequestStatus.EXPIRED_AWAITING_CONFIRMATION
                    )
                } else {
                    _cooldownState.value = CooldownState(hasActiveRequest = false)
                }
            }
        }

        // Periodic ticker to decrement remaining time and detect expiry
        scope.launch {
            while (isActive) {
                delay(1000L)
                updateTicker()
            }
        }
    }

    private fun updateTicker() {
        val current = _cooldownState.value
        if (!current.hasActiveRequest || current.status != DisableRequestStatus.PENDING_COOLDOWN) return

        val now = System.currentTimeMillis()
        val remaining = (current.expiresAtMillis - now).coerceAtLeast(0L)

        if (remaining <= 0L) {
            scope.launch {
                current.requestId?.let { id ->
                    repository.updateDisableRequestStatus(id, DisableRequestStatus.EXPIRED_AWAITING_CONFIRMATION)
                    repository.recordAuditEvent(SafeEventCode.COOLDOWN_COMPLETED, "RequestId: $id")
                }
                onStateChanged(ProtectionState.DISABLE_CONFIRMATION)
            }
            _cooldownState.value = current.copy(
                remainingMillis = 0L,
                isExpiredAwaitingConfirmation = true,
                status = DisableRequestStatus.EXPIRED_AWAITING_CONFIRMATION
            )
        } else {
            _cooldownState.value = current.copy(remainingMillis = remaining)
        }
    }

    suspend fun requestDisable(cooldownDurationMs: Long = DEFAULT_COOLDOWN_DURATION_MS): DisableRequest {
        val now = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()
        val request = DisableRequest(
            requestId = requestId,
            createdAtMillis = now,
            cooldownDurationMillis = cooldownDurationMs,
            expiresAtMillis = now + cooldownDurationMs,
            status = DisableRequestStatus.PENDING_COOLDOWN
        )

        repository.saveDisableRequest(request)
        repository.recordAuditEvent(SafeEventCode.DISABLE_REQUESTED, "DurationMs: $cooldownDurationMs")
        repository.recordAuditEvent(SafeEventCode.COOLDOWN_STARTED, "RequestId: $requestId")

        _cooldownState.value = CooldownState(
            hasActiveRequest = true,
            requestId = requestId,
            requestedAtMillis = now,
            expiresAtMillis = now + cooldownDurationMs,
            remainingMillis = cooldownDurationMs,
            isExpiredAwaitingConfirmation = false,
            status = DisableRequestStatus.PENDING_COOLDOWN
        )

        onStateChanged(ProtectionState.COOLDOWN_ACTIVE)
        return request
    }

    suspend fun cancelDisableRequest() {
        val currentId = _cooldownState.value.requestId
        if (currentId != null) {
            repository.updateDisableRequestStatus(currentId, DisableRequestStatus.CANCELLED)
        }
        _cooldownState.value = CooldownState(hasActiveRequest = false)
        repository.recordAuditEvent(SafeEventCode.COOLDOWN_COMPLETED, "Cancelled by user")
        onStateChanged(ProtectionState.ACTIVE)
    }

    suspend fun confirmDisableAfterCooldown(): Boolean {
        val current = _cooldownState.value
        if (!current.hasActiveRequest || !current.isExpiredAwaitingConfirmation) {
            return false // Reject if cooldown is not finished
        }

        val currentId = current.requestId ?: return false
        repository.updateDisableRequestStatus(currentId, DisableRequestStatus.CONFIRMED_DISABLED)
        repository.recordAuditEvent(SafeEventCode.PROTECTION_DISABLED, "Confirmed after full cooldown")

        _cooldownState.value = CooldownState(hasActiveRequest = false)
        onStateChanged(ProtectionState.DISABLED)
        return true
    }

    private fun reconcileCooldown(request: DisableRequest) {
        val now = System.currentTimeMillis()
        val remaining = (request.expiresAtMillis - now).coerceAtLeast(0L)
        val isExpired = remaining <= 0L

        _cooldownState.value = CooldownState(
            hasActiveRequest = true,
            requestId = request.requestId,
            requestedAtMillis = request.createdAtMillis,
            expiresAtMillis = request.expiresAtMillis,
            remainingMillis = remaining,
            isExpiredAwaitingConfirmation = isExpired,
            status = if (isExpired) DisableRequestStatus.EXPIRED_AWAITING_CONFIRMATION else DisableRequestStatus.PENDING_COOLDOWN
        )
    }
}
