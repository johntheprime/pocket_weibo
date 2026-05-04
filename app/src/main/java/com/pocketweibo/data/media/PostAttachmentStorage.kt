package com.pocketweibo.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Persists post images under [Context.getFilesDir]/post_attachments/{postId}/...
 * [PostEntity.imageUris] stores a JSON array of paths relative to filesDir, e.g.
 * ["post_attachments/12/0.jpg","post_attachments/12/1.png"].
 *
 * Images larger than [COMPRESS_THRESHOLD_BYTES] are re-encoded to **high-quality JPEG**
 * (longest side up to [COMPRESS_MAX_LONG_EDGE], quality [COMPRESS_JPEG_QUALITY]) unless
 * [storeOriginalQuality] is true. **GIF** is copied without re-encoding. If the encoded
 * file would be larger than the source, the copy falls back to the original bytes.
 */
object PostAttachmentStorage {

    const val REL_ROOT = "post_attachments"

    private const val COMPOSE_PREP_SUBDIR = "compose_prepare"

    /** Only compress when the copy from the picker exceeds this size (keeps modest photos untouched). */
    private const val COMPRESS_THRESHOLD_BYTES = 1_572_864L // 1.5 MiB

    private const val COMPRESS_MAX_LONG_EDGE = 2560

    private const val COMPRESS_JPEG_QUALITY = 92

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

            val isGif = mimeExt.equals(".gif", ignoreCase = true)
            val useCompressedOutput = !storeOriginalQuality &&
                !isGif &&
                temp.length() > COMPRESS_THRESHOLD_BYTES
            val suffix = if (useCompressedOutput) ".jpg" else mimeExt
            val out = File(composePrepareDir(context), "pw_${System.nanoTime()}$suffix")

            val ok = if (storeOriginalQuality || temp.length() <= COMPRESS_THRESHOLD_BYTES || isGif) {
                temp.copyTo(out, overwrite = true)
                out.isFile && out.length() > 0L
            } else {
                compressWithHighQualityJpeg(temp, out) || run {
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

    /**
     * Decodes [source], scales so the longest side is at most [COMPRESS_MAX_LONG_EDGE], writes JPEG.
     * Returns false if decoding fails or the output would be larger than the source (caller copies instead).
     */
    private fun compressWithHighQualityJpeg(source: File, dest: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val sample = inSampleSizeForLongEdge(bounds.outWidth, bounds.outHeight, COMPRESS_MAX_LONG_EDGE)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeFile(source.absolutePath, opts) ?: return false
        try {
            val w = bitmap.width
            val h = bitmap.height
            val longEdge = max(w, h)
            if (longEdge > COMPRESS_MAX_LONG_EDGE) {
                val scale = COMPRESS_MAX_LONG_EDGE.toFloat() / longEdge
                val nw = max(1, (w * scale).roundToInt())
                val nh = max(1, (h * scale).roundToInt())
                val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            dest.parentFile?.mkdirs()
            dest.outputStream().use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_JPEG_QUALITY, os)) {
                    return false
                }
            }
            if (!dest.isFile || dest.length() == 0L) return false
            // Avoid making the file larger than the picker copy (common with noisy high-res shots).
            if (dest.length() >= source.length()) {
                dest.delete()
                return false
            }
            return true
        } catch (_: OutOfMemoryError) {
            if (dest.exists()) dest.delete()
            return false
        } finally {
            bitmap.recycle()
        }
    }

    private fun inSampleSizeForLongEdge(width: Int, height: Int, maxLongEdge: Int): Int {
        var sample = 1
        val longDim = max(width, height)
        while (longDim / sample > maxLongEdge) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
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
