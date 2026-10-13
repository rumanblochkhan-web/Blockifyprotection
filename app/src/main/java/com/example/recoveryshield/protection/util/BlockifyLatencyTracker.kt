package com.example.recoveryshield.protection.util

import android.os.SystemClock
import android.util.Log

/**
 * Diagnostic performance tracker under TAG "BLOCKIFY_LATENCY"
 * for forensic root-cause analysis of AccessibilityService event delivery & intervention latency.
 */
object BlockifyLatencyTracker {

    const val TAG = "BLOCKIFY_LATENCY"

    val PROCESS_STARTED_AT: Long = SystemClock.elapsedRealtime()

    @Volatile
    var SERVICE_CONNECTED_TIME: Long = 0L
    @Volatile
    var SERVICE_DISCONNECTED_TIME: Long = 0L
    @Volatile
    var SERVICE_CONNECTED_AT: Long = 0L

    @Volatile
    var serviceReady: Boolean = false
    @Volatile
    var serviceHealth: String = "DISCONNECTED" // CONNECTED, DISCONNECTED, RECONNECTING, READY

    @Volatile
    var lastTriggerTime: Long = 0L
    @Volatile
    var lastHandledEventTime: Long = 0L

    fun onServiceConnected() {
        val now = SystemClock.elapsedRealtime()
        SERVICE_CONNECTED_TIME = now
        SERVICE_CONNECTED_AT = now
        serviceReady = true
        serviceHealth = "READY"

        val age = now - PROCESS_STARTED_AT
        Log.i(TAG, """
            |=== ACCESSIBILITY SERVICE CONNECTED ===
            |PROCESS_STARTED_AT: $PROCESS_STARTED_AT ms
            |SERVICE_CONNECTED_TIME: $SERVICE_CONNECTED_TIME ms
            |serviceConnectionAge: $age ms
            |serviceHealth: $serviceHealth
            |serviceReady: $serviceReady
            |======================================
        """.trimMargin())
    }

    fun onServiceDisconnected() {
        val now = SystemClock.elapsedRealtime()
        SERVICE_DISCONNECTED_TIME = now
        serviceReady = false
        serviceHealth = "DISCONNECTED"

        Log.w(TAG, """
            |=== ACCESSIBILITY SERVICE DISCONNECTED ===
            |SERVICE_DISCONNECTED_TIME: $SERVICE_DISCONNECTED_TIME ms
            |serviceHealth: $serviceHealth
            |serviceReady: $serviceReady
            |========================================
        """.trimMargin())
    }

    class EventDiagnosticContext(
        val EVENT_RECEIVED_TIME: Long = SystemClock.elapsedRealtime(),
        val EVENT_TYPE: String,
        val EVENT_PACKAGE: String,
        val EVENT_WINDOW: String,
        val EVENT_CLASS: String,
        val eventTimeMillis: Long = 0L
    ) {
        var TEXT_EXTRACTION_START: Long = 0L
        var TEXT_EXTRACTION_END: Long = 0L
        var MATCH_START: Long = 0L
        var MATCH_END: Long = 0L
        var MATCH_RESULT: String = "NONE"
        var INTERVENTION_REQUESTED_TIME: Long = 0L
        var OVERLAY_CREATE_START: Long = 0L
        var OVERLAY_CREATE_END: Long = 0L
        var ADD_VIEW_START: Long = 0L
        var ADD_VIEW_END: Long = 0L
        var POPUP_VISIBLE_TIME: Long = 0L

        fun markTextExtractionStart() { TEXT_EXTRACTION_START = SystemClock.elapsedRealtime() }
        fun markTextExtractionEnd() { TEXT_EXTRACTION_END = SystemClock.elapsedRealtime() }
        fun markMatchStart() { MATCH_START = SystemClock.elapsedRealtime() }
        fun markMatchEnd(result: String) {
            MATCH_END = SystemClock.elapsedRealtime()
            MATCH_RESULT = result
        }
        fun markInterventionRequested() { INTERVENTION_REQUESTED_TIME = SystemClock.elapsedRealtime() }
        fun markOverlayCreateStart() { OVERLAY_CREATE_START = SystemClock.elapsedRealtime() }
        fun markOverlayCreateEnd() { OVERLAY_CREATE_END = SystemClock.elapsedRealtime() }
        fun markAddViewStart() { ADD_VIEW_START = SystemClock.elapsedRealtime() }
        fun markAddViewEnd() { ADD_VIEW_END = SystemClock.elapsedRealtime() }
        fun markPopupVisible() { POPUP_VISIBLE_TIME = SystemClock.elapsedRealtime() }

        fun logDiagnosticSummary(
            isOverlayShowing: Boolean,
            isInterventionActive: Boolean,
            isCountingDown: Boolean
        ) {
            val now = SystemClock.elapsedRealtime()
            lastHandledEventTime = now
            if (POPUP_VISIBLE_TIME > 0L) {
                lastTriggerTime = now
            }

            val serviceConnectionAge = if (SERVICE_CONNECTED_TIME > 0L) now - SERVICE_CONNECTED_TIME else 0L
            val eventDeliveryLatency = if (eventTimeMillis > 0L) (System.currentTimeMillis() - eventTimeMillis).coerceAtLeast(0L) else 0L
            val textExtractionLatency = if (TEXT_EXTRACTION_END >= TEXT_EXTRACTION_START && TEXT_EXTRACTION_START > 0L) TEXT_EXTRACTION_END - TEXT_EXTRACTION_START else 0L
            val matchingLatency = if (MATCH_END >= MATCH_START && MATCH_START > 0L) MATCH_END - MATCH_START else 0L
            val interventionLatency = if (INTERVENTION_REQUESTED_TIME >= MATCH_END && MATCH_END > 0L) INTERVENTION_REQUESTED_TIME - MATCH_END else 0L
            val overlayLatency = if (POPUP_VISIBLE_TIME >= OVERLAY_CREATE_START && OVERLAY_CREATE_START > 0L) POPUP_VISIBLE_TIME - OVERLAY_CREATE_START else 0L
            val totalTriggerLatency = if (POPUP_VISIBLE_TIME >= EVENT_RECEIVED_TIME) POPUP_VISIBLE_TIME - EVENT_RECEIVED_TIME else 0L

            Log.i(TAG, """
                |--- BLOCKIFY_LATENCY EVENT SUMMARY ---
                |EVENT_RECEIVED_TIME: $EVENT_RECEIVED_TIME ms
                |EVENT_TYPE: $EVENT_TYPE
                |EVENT_PACKAGE: $EVENT_PACKAGE
                |EVENT_WINDOW: $EVENT_WINDOW
                |EVENT_CLASS: $EVENT_CLASS
                |serviceReady: ${serviceReady}
                |serviceHealth: ${serviceHealth}
                |serviceConnectionAge: ${serviceConnectionAge} ms
                |TEXT_EXTRACTION_START: $TEXT_EXTRACTION_START ms
                |TEXT_EXTRACTION_END: $TEXT_EXTRACTION_END ms
                |MATCH_START: $MATCH_START ms
                |MATCH_END: $MATCH_END ms
                |MATCH_RESULT: $MATCH_RESULT
                |INTERVENTION_REQUESTED_TIME: $INTERVENTION_REQUESTED_TIME ms
                |OVERLAY_CREATE_START: $OVERLAY_CREATE_START ms
                |OVERLAY_CREATE_END: $OVERLAY_CREATE_END ms
                |ADD_VIEW_START: $ADD_VIEW_START ms
                |ADD_VIEW_END: $ADD_VIEW_END ms
                |POPUP_VISIBLE_TIME: $POPUP_VISIBLE_TIME ms
                |
                |LATENCY METRICS:
                |  eventDeliveryLatency: ${eventDeliveryLatency} ms
                |  textExtractionLatency: ${textExtractionLatency} ms
                |  matchingLatency: ${matchingLatency} ms
                |  interventionLatency: ${interventionLatency} ms
                |  overlayLatency: ${overlayLatency} ms
                |  totalTriggerLatency: ${totalTriggerLatency} ms
                |
                |SYSTEM STATES:
                |  isOverlayShowing: $isOverlayShowing
                |  isInterventionActive: $isInterventionActive
                |  isCountingDown: $isCountingDown
                |  lastTriggerTime: ${lastTriggerTime} ms
                |  lastHandledEventTime: ${lastHandledEventTime} ms
                |-------------------------------------
            """.trimMargin())
        }
    }
}
