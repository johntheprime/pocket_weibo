package com.pocketweibo.data.media

import android.content.Context
import java.io.File

/**
 * REVIEW-2 §3: the only cache subdirectory exposed through the FileProvider (see res/xml/file_paths.xml).
 * Anything handed to another app (share sheet, camera capture) must be written here, so import staging
 * (`pw_import_*`), voice drafts and other cache files are never reachable through the provider.
 */
object ShareCache {
    const val SUBDIR = "shared"

    fun dir(context: Context): File = File(context.cacheDir, SUBDIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)
}
