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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Persists post images under [Context.getFilesDir]/post_attachments/{postId}/...
 * [PostEntity.imageUris] stores a JSON array of paths relative to filesDir, e.g.
 * ["post_attachments/12/0.jpg","post_attachments/12/1.png"].
 *
 * Images larger than [COMPRESS_THRESHOLD_BYTES] are re-encoded with [Compressor] unless
 * [storeOriginalQuality] is true: first pass uses high JPEG quality with a resolution cap; a second
 * pass with a soft size cap runs only when the first output still exceeds [COMPRESS_TARGET_MAX_BYTES].
 */
object PostAttachmentStorage {

    const val REL_ROOT = "post_attachments"

    private const val COMPOSE_PREP_SUBDIR = "compose_prepare"

    /** Only compress when the decoded copy from the picker exceeds this size. */
    private const val COMPRESS_THRESHOLD_BYTES = 1_048_576L

    /** Target upper bound for compressed output ([Compressor] `size` constraint, bytes). */
    private const val COMPRESS_TARGET_MAX_BYTES = 1_048_576L

    private const val COMPRESS_MAX_EDGE_PX = 2048

    fun rootDir(context: Context): File = File(context.filesDir, REL_ROOT)

    private fun composePrepareDir(context: Context): File =
        File(context.cacheDir, COMPOSE_PREP_SUBDIR).apply { mkdirs() }

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

    /**
     * Reads [uri], optionally compresses, writes a finished file under [composePrepareDir].
     * Caller must delete returned files when discarding the draft or after a successful attach.
     */
    suspend fun prepareOneGalleryImage(
        context: Context,
        uri: Uri,
        storeOriginalQuality: Boolean
    ): File? = withContext(Dispatchers.IO) {
        val mimeExt = extensionForUri(context, uri)
        val temp = File.createTempFile("pw_src_", null, context.cacheDir)
        try {
            val filled = context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
                temp.isFile && temp.length() > 0L
            } ?: false
            if (!filled) return@withContext null

            val useJpegOutput = !storeOriginalQuality && temp.length() > COMPRESS_THRESHOLD_BYTES
            val suffix = if (useJpegOutput) ".jpg" else mimeExt
            val out = File(composePrepareDir(context), "pw_${System.nanoTime()}$suffix")

            val ok = if (storeOriginalQuality || temp.length() <= COMPRESS_THRESHOLD_BYTES) {
                temp.copyTo(out, overwrite = true)
                out.isFile && out.length() > 0L
            } else {
                compressWithCompressor(context, temp, out) || run {
                    temp.copyTo(out, overwrite = true)
                    out.isFile && out.length() > 0L
                }
            }
            if (ok) out else {
                if (out.exists()) out.delete()
                null
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * Moves [files] into [REL_ROOT]/[postId]/ with indices 0..n; deletes each source after success.
     */
    suspend fun movePreparedFilesIntoPost(context: Context, postId: Long, files: List<File>): String =
        withContext(Dispatchers.IO) {
            if (files.isEmpty()) return@withContext ""
            val postDir = File(rootDir(context), postId.toString())
            postDir.mkdirs()
            val relativePaths = mutableListOf<String>()
            files.take(9).forEachIndexed { index, src ->
                if (!src.isFile || src.length() == 0L) return@forEachIndexed
                val ext = extensionFromFilename(src.name)
                val dest = File(postDir, "$index$ext")
                moveOrReplaceFile(src, dest)
                if (dest.isFile && dest.length() > 0L) {
                    relativePaths += "$REL_ROOT/$postId/${dest.name}"
                }
            }
            if (relativePaths.isEmpty()) {
                if (postDir.exists() && postDir.listFiles()?.isEmpty() == true) postDir.delete()
                ""
            } else {
                serializePaths(relativePaths)
            }
        }

    private fun extensionFromFilename(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot) else ".jpg"
    }

    private fun moveOrReplaceFile(src: File, dest: File) {
        dest.parentFile?.mkdirs()
        try {
            Files.move(
                src.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
    }

    private suspend fun compressWithCompressor(context: Context, source: File, dest: File): Boolean {
        val workDir = composePrepareDir(context)
        val pass1 = File(workDir, "pw_cmp1_${System.nanoTime()}.jpg")
        var pass2: File? = null
        return try {
            dest.parentFile?.mkdirs()
            Compressor.compress(context, source, Dispatchers.IO) {
                resolution(COMPRESS_MAX_EDGE_PX, COMPRESS_MAX_EDGE_PX)
                quality(92)
                format(Bitmap.CompressFormat.JPEG)
                destination(pass1)
            }
            if (!pass1.isFile || pass1.length() == 0L) {
                if (pass1.exists()) pass1.delete()
                false
            } else {
                val fits = pass1.length() <= COMPRESS_TARGET_MAX_BYTES
                val ok = if (fits) {
                    moveCompressedToDest(pass1, dest)
                } else {
                    val p2 = File(workDir, "pw_cmp2_${System.nanoTime()}.jpg").also { pass2 = it }
                    Compressor.compress(context, pass1, Dispatchers.IO) {
                        resolution(COMPRESS_MAX_EDGE_PX, COMPRESS_MAX_EDGE_PX)
                        quality(86)
                        format(Bitmap.CompressFormat.JPEG)
                        size(maxFileSize = COMPRESS_TARGET_MAX_BYTES, stepSize = 3, maxIteration = 22)
                        destination(p2)
                    }
                    val pass2Ok = p2.isFile && p2.length() > 0L
                    if (pass1.exists()) pass1.delete()
                    if (!pass2Ok) {
                        if (p2.exists()) p2.delete()
                        false
                    } else {
                        moveCompressedToDest(p2, dest)
                    }
                }
                if (pass1.exists() && pass1.absolutePath != dest.absolutePath) pass1.delete()
                ok
            }
        } catch (_: Exception) {
            if (pass1.exists()) pass1.delete()
            pass2?.let { if (it.exists()) it.delete() }
            if (dest.exists() && dest.length() == 0L) dest.delete()
            false
        }
    }

    private fun moveCompressedToDest(from: File, dest: File): Boolean {
        return try {
            if (from.absolutePath == dest.absolutePath) return from.isFile && from.length() > 0L
            from.copyTo(dest, overwrite = true)
            from.delete()
            dest.isFile && dest.length() > 0L
        } catch (_: Exception) {
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
