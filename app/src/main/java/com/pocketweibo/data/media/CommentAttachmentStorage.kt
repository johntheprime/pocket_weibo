package com.pocketweibo.data.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Voice clips for comments: [REL_ROOT]/[commentId]/[VOICE_FILENAME]. */
object CommentAttachmentStorage {

    const val REL_ROOT = "comment_attachments"
    const val VOICE_FILENAME = "voice.m4a"

    fun rootDir(context: Context): File = File(context.filesDir, REL_ROOT)

    fun fileForRelativePath(context: Context, relative: String): File =
        File(context.filesDir, relative)

    suspend fun movePreparedVoiceIntoComment(context: Context, commentId: Long, src: File): String =
        withContext(Dispatchers.IO) {
            if (!src.isFile || src.length() == 0L) return@withContext ""
            val dir = File(rootDir(context), commentId.toString())
            dir.mkdirs()
            val dest = File(dir, VOICE_FILENAME)
            moveOrReplaceFile(src, dest)
            if (dest.isFile && dest.length() > 0L) {
                "$REL_ROOT/$commentId/${dest.name}"
            } else {
                ""
            }
        }

    fun deleteForComment(context: Context, commentId: Long) {
        File(rootDir(context), commentId.toString()).deleteRecursively()
    }

    fun deleteEntireTree(context: Context) {
        rootDir(context).deleteRecursively()
    }

    private fun moveOrReplaceFile(src: File, dest: File) {
        dest.parentFile?.mkdirs()
        try {
            Files.move(
                src.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
    }
}
