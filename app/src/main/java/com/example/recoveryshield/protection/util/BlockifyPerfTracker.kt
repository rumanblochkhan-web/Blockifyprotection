package com.example.recoveryshield.protection.util

import android.os.SystemClock
import android.util.Log

/**
 * Temporary development-only high-precision performance instrumentation.
 * Measures end-to-end trigger-to-overlay latency under tag BLOCKIFY_PERF.
 */
object BlockifyPerfTracker {

    private const val TAG = "BLOCKIFY_PERF"
    private const val ENABLED = true

    @Volatile
    var t0_eventReceivedNanos: Long = 0L
    @Volatile
    var t1_packageIdentifiedNanos: Long = 0L
    @Volatile
    var t2_contentObtainedNanos: Long = 0L
    @Volatile
    var t3_normalizationCompleteNanos: Long = 0L
    @Volatile
    var t4_matcherStartedNanos: Long = 0L
    @Volatile
    var t5_matchConfirmedNanos: Long = 0L
    @Volatile
    var t6_interventionRequestedNanos: Long = 0L
    @Volatile
    var t7_overlayCreationStartedNanos: Long = 0L
    @Volatile
    var t8_addViewAttemptedNanos: Long = 0L
    @Volatile
    var t9_addViewSuccessfulNanos: Long = 0L
    @Volatile
    var t10_popupRenderReachedNanos: Long = 0L

    fun onEventReceived() {
        if (!ENABLED) return
        t0_eventReceivedNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun onPackageIdentified() {
        if (!ENABLED) return
        t1_packageIdentifiedNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun onContentObtained() {
        if (!ENABLED) return
        t2_contentObtainedNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun onNormalizationComplete() {
        if (!ENABLED) return
        t3_normalizationCompleteNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun onMatcherStarted() {
        if (!ENABLED) return
        t4_matcherStartedNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun onMatchConfirmed(triggerSummary: String) {
        if (!ENABLED) return
        t5_matchConfirmedNanos = SystemClock.elapsedRealtimeNanos()
        val detectionLatencyMs = (t5_matchConfirmedNanos - t0_eventReceivedNanos) / 1_000_000.0
        Log.i(TAG, "[T5] MATCH CONFIRMED: '$triggerSummary' | Detection latency: %.2f ms".format(detectionLatencyMs))
    }

    fun onInterventionRequested() {
        if (!ENABLED) return
        t6_interventionRequestedNanos = SystemClock.elapsedRealtimeNanos()
        val interventionLatencyMs = (t6_interventionRequestedNanos - t5_matchConfirmedNanos) / 1_000_000.0
        Log.i(TAG, "[T6] INTERVENTION REQUESTED | Intervention latency: %.2f ms".format(interventionLatencyMs))
    }

    fun onOverlayCreationStarted() {
        if (!ENABLED) return
        t7_overlayCreationStartedNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun onAddViewAttempted() {
        if (!ENABLED) return
        t8_addViewAttemptedNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun onAddViewSuccessful() {
        if (!ENABLED) return
        t9_addViewSuccessfulNanos = SystemClock.elapsedRealtimeNanos()
        val attachmentLatencyMs = (t9_addViewSuccessfulNanos - t7_overlayCreationStartedNanos) / 1_000_000.0
        val totalLatencyMs = (t9_addViewSuccessfulNanos - t0_eventReceivedNanos) / 1_000_000.0
        Log.i(TAG, "[T9] OVERLAY ATTACHED! | Overlay attachment latency: %.2f ms | TOTAL trigger-to-overlay: %.2f ms".format(attachmentLatencyMs, totalLatencyMs))
    }

    fun onPopupRenderReached() {
        if (!ENABLED) return
        t10_popupRenderReachedNanos = SystemClock.elapsedRealtimeNanos()
        val totalRenderMs = (t10_popupRenderReachedNanos - t0_eventReceivedNanos) / 1_000_000.0
        Log.i(TAG, "[T10] POPUP RENDERED! | Complete event-to-render: %.2f ms".format(totalRenderMs))
    }
}
