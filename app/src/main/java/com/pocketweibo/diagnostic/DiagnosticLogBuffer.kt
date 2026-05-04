package com.pocketweibo.diagnostic

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer for on-device diagnostics (settings → export).
 * Not persisted; cleared on process death or user "Clear".
 */
object DiagnosticLogBuffer {

    private const val MAX_LINES = 4000

    private val lines = Collections.synchronizedList(mutableListOf<String>())
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    var captureEnabled: Boolean = false

    fun append(level: Char, tag: String, message: String, throwable: Throwable? = null) {
        if (!captureEnabled) return
        val ts = timeFmt.format(Date())
        val sb = StringBuilder(64 + message.length)
        sb.append(ts).append(' ').append(level).append('/').append(tag).append(": ").append(message)
        if (throwable != null) {
            sb.append('\n').append(Log.getStackTraceString(throwable))
        }
        val line = sb.toString()
        synchronized(lines) {
            lines.add(line)
            while (lines.size > MAX_LINES) {
                lines.removeAt(0)
            }
        }
    }

    fun lineCount(): Int = synchronized(lines) { lines.size }

    fun clear() {
        synchronized(lines) { lines.clear() }
    }

    fun dumpLines(): List<String> = synchronized(lines) { lines.toList() }
}
