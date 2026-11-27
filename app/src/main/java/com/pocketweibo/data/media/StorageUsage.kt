package com.pocketweibo.data.media

import android.content.Context
import java.io.File

/** FP-P1: numbers for Settings → Storage, and the "Clear cache" action. */
object StorageUsage {

    data class Usage(val dataBytes: Long, val cacheBytes: Long)

    /** Call off the main thread: walks the app's private directories. */
    fun measure(context: Context): Usage {
        val cache = sizeOf(context.cacheDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L)
        val skip = setOf(context.cacheDir.name, context.codeCacheDir.name, "lib")
        val data = context.dataDir.listFiles()
            ?.filter { it.name !in skip }
            ?.sumOf { sizeOf(it) } ?: 0L
        return Usage(dataBytes = data, cacheBytes = cache)
    }

    /** Deletes everything in the cache dirs. User data under filesDir / databases is untouched. */
    fun clearCache(context: Context) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }

    internal fun sizeOf(f: File): Long =
        if (f.isFile) f.length() else f.listFiles()?.sumOf { sizeOf(it) } ?: 0L
}
