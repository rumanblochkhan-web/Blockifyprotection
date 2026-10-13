package com.example.protection.service

data class DiagnosticLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String
)
