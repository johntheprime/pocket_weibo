package com.pocketweibo.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Short AAC/M4A recordings for compose/comments. Caller shows toasts via [onTooShort] / [onMaxDuration].
 * Call [release] from [androidx.compose.runtime.DisposableEffect].
 */
class VoiceRecordingController(private val appContext: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    val isActive: Boolean get() = mediaRecorder != null

    private fun buildRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    fun startRecording(): Boolean {
        if (mediaRecorder != null) return false
        val out = File(appContext.cacheDir, "pw_voice_${System.nanoTime()}.m4a")
        outputFile = out
        startedAt = SystemClock.elapsedRealtime()
        val rec = try {
            buildRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(out.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            out.delete()
            outputFile = null
            return false
        }
        mediaRecorder = rec
        return true
    }

    /**
     * Stops recording. Returns a file to keep as a draft, or null if discarded.
     */
    fun stopRecording(
        finishedByMaxDuration: Boolean,
        onTooShort: () -> Unit,
    ): File? {
        val rec = mediaRecorder ?: return null
        runCatching { rec.stop() }
        runCatching { rec.release() }
        mediaRecorder = null
        val out = outputFile
        outputFile = null
        if (out == null || !out.isFile) return null
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        if (durationMs < 700L || out.length() < 4_000L) {
            out.delete()
            if (!finishedByMaxDuration) onTooShort()
            return null
        }
        return out
    }

    fun cancelRecording() {
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        outputFile?.takeIf { it.exists() }?.delete()
        outputFile = null
    }

    fun release() {
        cancelRecording()
    }
}
