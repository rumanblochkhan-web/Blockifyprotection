package com.example.recoveryshield.protection.detector

import com.example.recoveryshield.domain.model.DetectionResult
import com.example.recoveryshield.domain.model.NormalizedUiEvent

/**
 * Extensible interface for on-device detection pipelines.
 * Future on-device ML models (e.g. TFLite, LiteRT) can implement this interface
 * without changing any upstream protection services or intervention flows.
 */
interface LocalClassifier {
    fun classify(event: NormalizedUiEvent): DetectionResult
    fun resetTemporalState()
}
