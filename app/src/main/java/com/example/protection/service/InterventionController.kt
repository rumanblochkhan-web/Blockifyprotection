package com.example.protection.service

import android.content.Context
import com.example.recoveryshield.protection.engine.ProtectionEngine

class InterventionController(private val context: Context) {
    fun triggerIntervention(reason: String): Boolean {
        return ProtectionEngine.getInstance(context).interventionController.triggerIntervention(reason)
    }
}
