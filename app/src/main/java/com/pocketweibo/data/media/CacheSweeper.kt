package com.pocketweibo.data.media

import android.content.Context
import java.io.File

/**
 * REVIEW-3 §5.1 / ROADMAP Phase 2: deletes stale temporary files (shared exports, decrypted backups,
 * import staging, voice/camera drafts) left in cache by earlier sessions. Runs on IO at startup.
 * Files younger than [MIN_AGE_MS] are kept so an in-flight share or capture is never removed.
 */
object CacheSweeper {
    private const val MIN_AGE_MS = 24L * 60 * 60 * 1000

    private val prefixes = listOf(
        "pocket_weibo_backup", "pocket_weibo_diag", "pw_decrypted", "pw_import_",
        "pw_src_", "pw_voice_", "compose_capture_"
    )

    fun sweep(context: Context, now: Long = System.currentTimeMillis()) {
        val roots = listOf(context.cacheDir, ShareCache.dir(context))
        for (root in roots) {
            root.listFiles()?.forEach { f ->
                if (prefixes.any { f.name.startsWith(it) } && now - f.lastModified() > MIN_AGE_MS) {
                    f.deleteRecursively()
                }
            }
        }
        // Replace-import staging lives in filesDir (REVIEW-3 §1.2); remove leftovers from a killed import.
        context.filesDir.listFiles()?.forEach { f: File ->
            if (f.name.startsWith("pw_import_staging_") && now - f.lastModified() > MIN_AGE_MS) {
                f.deleteRecursively()
            }
        }
    }
}
