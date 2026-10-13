package com.example.recoveryshield.domain.model

data class SupportChannelInfo(
    val title: String,
    val description: String,
    val actionUri: String,
    val isExternalLink: Boolean = true
)

data class RecoveryGuidance(
    val title: String,
    val message: String
)
