package com.example.myapplication.utils

import android.util.Log

object UXEventLogger {
    private const val TAG = "UXEventLogger"

    fun logError(category: String, message: String, exception: Throwable? = null) {
        val fullMessage = "[$category] $message"
        if (exception != null) {
            Log.e(TAG, fullMessage, exception)
        } else {
            Log.e(TAG, fullMessage)
        }
        // In a real app, you would send this to Crashlytics, Sentry, or Firebase Analytics
        // e.g. FirebaseAnalytics.getInstance(context).logEvent("ux_error", bundle)
    }

    fun logLongLoading(screen: String, durationMs: Long) {
        if (durationMs > 2000) {
            Log.w(TAG, "[$screen] Loading took too long: ${durationMs}ms")
        }
    }

    fun logSaveFailure(entity: String, reason: String) {
        Log.e(TAG, "Failed to save [$entity]. Reason: $reason")
    }
}
