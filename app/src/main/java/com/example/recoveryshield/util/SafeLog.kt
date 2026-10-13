package com.example.recoveryshield.util

/**
 * Robust logger that outputs to android.util.Log in runtime and gracefully
 * falls back to stdout in unit test / local JVM environments.
 */
object SafeLog {
    fun i(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun d(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun w(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (_: Throwable) {
            println("[$tag] $msg: $tr")
        }
    }
}
