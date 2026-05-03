package com.pocketweibo.data.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.destination
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Persists post images under [Context.getFilesDir]/post_attachments/{postId}/...
 * [PostEntity.imageUris] stores a JSON array of paths relative to filesDir, e.g.
 * ["post_attachments/12/0.jpg","post_attachments/12/1.png"].
 *
 * Images larger than [COMPRESS_THRESHOLD_BYTES] are re-encoded with [Compressor] to stay near
 * [COMPRESS_TARGET_MAX_BYTES] unless [storeOriginalQuality] is true.
 */
object PostAttachmentStorage {

    const val REL_ROOT = "post_attachments"

    /** Only compress when the decoded copy from the picker exceeds this size. */
    private const val COMPRESS_THRESHOLD_BYTES = 1_048_576L

    /** Target upper bound for compressed output ([Compressor] `size` constraint, bytes). */
    private const val COMPRESS_TARGET_MAX_BYTES = 1_048_576L

    private const val COMPRESS_MAX_EDGE_PX = 2048

    fun rootDir(context: Context): File = File(context.filesDir, REL_ROOT)

    fun parseStoredPaths(imageUris: String): List<String> {
        val t = imageUris.trim()
        if (t.isEmpty()) return emptyList()
        if (t.startsWith("[")) {
            return try {
                val arr = JSONArray(t)
                buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i, "").trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        return t.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun serializePaths(paths: List<String>): String {
        val arr = JSONArray()
        paths.forEach { arr.put(it) }
        return arr.toString()
    }

    fun fileForRelativePath(context: Context, relative: String): File =
        File(context.filesDir, relative)

    suspend fun copyGalleryUrisIntoPost(
        context: Context,
        postId: Long,
        uris: List<Uri>,
        storeOriginalQuality: Boolean
    ): String = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext ""
        val postDir = File(rootDir(context), postId.toString())
        postDir.mkdirs()
        val relativePaths = mutableListOf<String>()
        uris.take(9).forEachIndexed { index, uri ->
            val mimeExt = extensionForUri(context, uri)
            val temp = File.createTempFile("pw_src_", null, context.cacheDir)
            try {
                val filled = context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                    temp.isFile && temp.length() > 0L
                } ?: false
                if (!filled) return@forEachIndexed

                val useJpegOutput = !storeOriginalQuality && temp.length() > COMPRESS_THRESHOLD_BYTES
                val fileName = if (useJpegOutput) "$index.jpg" else "$index$mimeExt"
                val outFile = File(postDir, fileName)

                val ok = if (storeOriginalQuality || temp.length() <= COMPRESS_THRESHOLD_BYTES) {
                    temp.copyTo(outFile, overwrite = true)
                    outFile.isFile && outFile.length() > 0L
                } else {
                    compressWithCompressor(context, temp, outFile) || run {
                        temp.copyTo(outFile, overwrite = true)
                        outFile.isFile && outFile.length() > 0L
                    }
                }
                if (ok) {
                    relativePaths += "$REL_ROOT/$postId/$fileName"
                } else if (outFile.exists()) {
                    outFile.delete()
                }
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
        if (relativePaths.isEmpty()) {
            if (postDir.exists() && postDir.listFiles()?.isEmpty() == true) postDir.delete()
            ""
        } else {
            serializePaths(relativePaths)
        }
    }

    private suspend fun compressWithCompressor(context: Context, source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            val compressed = Compressor.compress(context, source, Dispatchers.IO) {
                resolution(COMPRESS_MAX_EDGE_PX, COMPRESS_MAX_EDGE_PX)
                quality(86)
                format(Bitmap.CompressFormat.JPEG)
                size(maxFileSize = COMPRESS_TARGET_MAX_BYTES, stepSize = 8, maxIteration = 18)
                destination(dest)
            }
            compressed.isFile && compressed.length() > 0L
        } catch (_: Exception) {
            if (dest.exists() && dest.length() == 0L) dest.delete()
            false
        }
    }

    fun deleteAllForPost(context: Context, postId: Long) {
        File(rootDir(context), postId.toString()).deleteRecursively()
    }

    fun deleteEntireAttachmentTree(context: Context) {
        rootDir(context).deleteRecursively()
    }

    private fun extensionForUri(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return when (mime?.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/jpeg", "image/jpg" -> ".jpg"
            else -> {
                val fromName = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
                if (fromName.isNotBlank()) {
                    val dot = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fromName)
                    if (dot != null) return extensionForMime(dot)
                }
                ".jpg"
            }
        }
    }

    private fun extensionForMime(mime: String): String = when (mime.lowercase()) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}
