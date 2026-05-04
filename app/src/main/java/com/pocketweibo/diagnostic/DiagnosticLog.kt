package com.pocketweibo.diagnostic

import android.util.Log

/**
 * Writes to system logcat always; duplicates to [DiagnosticLogBuffer] when capture is enabled in settings.
 */
object DiagnosticLog {

    fun d(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.d(tag, msg, t) else Log.d(tag, msg)
        DiagnosticLogBuffer.append('D', tag, msg, t)
    }

    fun i(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.i(tag, msg, t) else Log.i(tag, msg)
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
