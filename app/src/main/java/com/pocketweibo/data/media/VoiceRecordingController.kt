package com.pocketweibo.data.media

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File

/**
 * Short AAC/M4A recordings for compose/comments. Caller shows toasts via [onTooShort] / [onMaxDuration].
 * Call [release] from [androidx.compose.runtime.DisposableEffect].
 *
 * Uses explicit AAC parameters and a short post-[stop] size settle so short clips are not discarded
 * while the container is still flushing to disk.
 */
class VoiceRecordingController(private val appContext: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val isActive: Boolean get() = mediaRecorder != null

    private fun buildRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    /**
     * Begins capture from the device microphone. Prefer calling after [RECORD_AUDIO] is granted.
     * Returns false if another session is active or configuration/start fails.
     */
    fun startRecording(): Boolean {
        if (mediaRecorder != null) return false
        val out = File(appContext.cacheDir, "pw_voice_${System.nanoTime()}.m4a")
        outputFile = out
        startedAt = SystemClock.elapsedRealtime()
        previousAudioMode = audioManager.mode
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }
        val rec = try {
            buildRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setAudioEncodingBitRate(96_000)
                    setAudioSamplingRate(44_100)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setOutputFile(out)
                } else {
                    @Suppress("DEPRECATION")
                    setOutputFile(out.absolutePath)
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            out.delete()
            outputFile = null
            restoreAudioMode()
            return false
        }
        mediaRecorder = rec
        return true
    }

    /**
     * Call from the permission callback (or right after an activity resumes) so the system audio
     * policy stack has a frame to settle before [MediaRecorder.start].
     */
    fun startRecordingOnNextMainFrame(handler: Handler = Handler(Looper.getMainLooper()), onResult: (Boolean) -> Unit) {
        handler.post {
            onResult(startRecording())
        }
    }

    /**
     * Stops recording. Returns a file to keep as a draft, or null if discarded.
     */
    fun stopRecording(
        finishedByMaxDuration: Boolean,
        onTooShort: () -> Unit,
    ): File? {
        val rec = mediaRecorder
        if (rec == null) {
            restoreAudioMode()
            return null
        }
        runCatching { rec.stop() }
        runCatching { rec.release() }
        mediaRecorder = null
        restoreAudioMode()
        val out = outputFile
        outputFile = null
        if (out == null || !out.isFile) return null
        waitForNonTrivialFile(out)
        val durationMs = SystemClock.elapsedRealtime() - startedAt
        if (durationMs < MIN_DURATION_MS || out.length() < MIN_FILE_BYTES) {
            out.delete()
            if (!finishedByMaxDuration) onTooShort()
            return null
        }
        return out
    }

    fun cancelRecording() {
        val rec = mediaRecorder
        mediaRecorder = null
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        restoreAudioMode()
        outputFile?.takeIf { it.exists() }?.delete()
        outputFile = null
    }

    fun release() {
        cancelRecording()
    }

    private fun restoreAudioMode() {
        runCatching {
            audioManager.mode = previousAudioMode
        }
    }

    /**
     * MPEG-4 muxers may report a tiny length until [stop] fully flushes; poll briefly so we do not
     * reject valid short takes.
     */
    private fun waitForNonTrivialFile(out: File) {
        var last = -1L
        repeat(FILE_SETTLE_POLLS) {
            val len = out.length()
            if (len >= MIN_FILE_BYTES && len == last) return
            last = len
            try {
                Thread.sleep(FILE_SETTLE_STEP_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    companion object {
        /** Below this, discard as accidental tap or failed capture. */
        private const val MIN_DURATION_MS = 420L

        /** AAC+M4A can be small for sub-second speech; previous 4 KiB gate dropped real clips. */
        private const val MIN_FILE_BYTES = 512L

        private const val FILE_SETTLE_STEP_MS = 15L
        private const val FILE_SETTLE_POLLS = 12
    }
}
