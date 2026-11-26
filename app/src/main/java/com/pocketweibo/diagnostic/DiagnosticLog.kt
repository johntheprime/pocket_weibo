package com.pocketweibo.diagnostic

import android.util.Log
import com.pocketweibo.BuildConfig

/**
 * Duplicates to [DiagnosticLogBuffer] when capture is enabled in settings.
 * REVIEW-3 §5.4: release builds only send W/E to logcat; D/I go to logcat in debug builds or while
 * the in-app capture is on, so routine reminder details are not readable from logcat in release.
 */
object DiagnosticLog {

    private fun verbose(): Boolean = BuildConfig.DEBUG || DiagnosticLogBuffer.captureEnabled

    fun d(tag: String, msg: String, t: Throwable? = null) {
        if (verbose()) { if (t != null) Log.d(tag, msg, t) else Log.d(tag, msg) }
        DiagnosticLogBuffer.append('D', tag, msg, t)
    }

    fun i(tag: String, msg: String, t: Throwable? = null) {
        if (verbose()) { if (t != null) Log.i(tag, msg, t) else Log.i(tag, msg) }
        DiagnosticLogBuffer.append('I', tag, msg, t)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
        DiagnosticLogBuffer.append('W', tag, msg, t)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        DiagnosticLogBuffer.append('E', tag, msg, t)
    }
}
