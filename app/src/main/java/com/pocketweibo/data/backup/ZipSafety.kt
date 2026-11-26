package com.pocketweibo.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * REVIEW-3 §1.1 / §6.1: safe ZIP extraction. Entry paths must stay under the target root
 * (no Zip Slip via `../`), and entries are streamed to disk with per-entry and total size caps
 * (no zip bombs, no whole-entry byte arrays on the heap).
 */
object ZipSafety {
    const val MAX_ENTRY_BYTES: Long = 512L * 1024 * 1024
    const val MAX_TOTAL_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** Normalized entry name, or null for directories / empty names. */
    fun normalizeName(raw: String): String? =
        raw.replace('\\', '/').trimStart('/').takeIf { it.isNotEmpty() && !it.endsWith("/") }

    /** Returns the file for [name] under [root], or null if it would escape [root]. */
    fun safeChild(root: File, name: String): File? {
        val rootPath = root.canonicalPath
        val out = File(root, name).canonicalFile
        return if (out.path.startsWith(rootPath + File.separator)) out else null
    }

    /**
     * Extracts every file entry of [input] into [root]. Unsafe entries are skipped and returned in
     * the result; size caps throw [IOException].
     */
    fun extractTo(input: InputStream, root: File): List<String> {
        root.mkdirs()
        val rejected = mutableListOf<String>()
        var total = 0L
        val buf = ByteArray(64 * 1024)
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = if (entry.isDirectory) null else normalizeName(entry.name)
                if (name != null) {
                    val out = safeChild(root, name)
                    if (out == null) {
                        rejected += entry.name
                    } else {
                        out.parentFile?.mkdirs()
                        var written = 0L
                        out.outputStream().use { os ->
                            while (true) {
                                val n = zis.read(buf)
                                if (n < 0) break
                                written += n
                                total += n
                                if (written > MAX_ENTRY_BYTES) throw IOException("ZIP entry too large: $name")
                                if (total > MAX_TOTAL_BYTES) throw IOException("ZIP content too large")
                                os.write(buf, 0, n)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return rejected
    }
}
