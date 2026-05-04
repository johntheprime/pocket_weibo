package com.pocketweibo.data.media

import android.content.Context
import java.io.File

/**
 * Stores custom identity avatars under [Context.getFilesDir]/identity_avatars/{id}.jpg.
 * [com.pocketweibo.data.local.entity.IdentityEntity.customAvatarUri] holds the path relative to filesDir.
 */
object IdentityAvatarStorage {

    const val REL_ROOT = "identity_avatars"

    fun relativePath(identityId: Long): String = "$REL_ROOT/${identityId}.jpg"

    fun fileForRelativePath(context: Context, relative: String): File =
        File(context.filesDir, relative)

    fun deleteForIdentity(context: Context, identityId: Long) {
        val f = File(context.filesDir, relativePath(identityId))
        if (f.exists()) f.delete()
    }

    fun deleteEntireTree(context: Context) {
        File(context.filesDir, REL_ROOT).takeIf { it.exists() }?.deleteRecursively()
    }

    fun deleteStoredFile(context: Context, relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        if (relativePath.contains("://")) return
        val f = fileForRelativePath(context, relativePath)
        if (f.exists()) f.delete()
    }
}
