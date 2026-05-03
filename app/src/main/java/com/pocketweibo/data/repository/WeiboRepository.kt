package com.pocketweibo.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketweibo.data.local.dao.CommentDao
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.local.dao.IdentityDao
import com.pocketweibo.data.local.dao.PostDao
import com.pocketweibo.data.local.dao.PostReminderDao
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.Gender
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import com.pocketweibo.data.local.entity.PostReminderEntity
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.reminder.PostReminderAlarmScheduler
import com.pocketweibo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val Context.draftDataStore by preferencesDataStore(name = "draft")

class WeiboRepository(
    private val identityDao: IdentityDao,
    private val postDao: PostDao,
    private val commentDao: CommentDao,
    private val postReminderDao: PostReminderDao,
    private val context: Context
) {
    val allIdentities: Flow<List<IdentityEntity>> = identityDao.getAllIdentities()
    val activeIdentity: Flow<IdentityEntity?> = identityDao.getActiveIdentity()
    val allPosts: Flow<List<PostWithIdentity>> = postDao.getAllPosts()

    suspend fun getIdentityById(id: Long): IdentityEntity? = identityDao.getIdentityById(id)

    suspend fun insertIdentity(identity: IdentityEntity): Long = identityDao.insert(identity)

    suspend fun updateIdentity(identity: IdentityEntity) = identityDao.update(identity)

    suspend fun clearCustomAvatar(identityId: Long) = identityDao.clearCustomAvatar(identityId)

    suspend fun deleteIdentity(identity: IdentityEntity) = identityDao.delete(identity)

    suspend fun setActiveIdentity(id: Long) {
        identityDao.deactivateAll()
        identityDao.activate(id)
    }

    suspend fun insertPost(post: PostEntity): Long = postDao.insert(post)

    /**
     * Creates a post and copies [galleryUris] into app-private storage under [PostAttachmentStorage.REL_ROOT].
     * [content] may be blank when only images are attached.
     * Prefer [insertPostWithPreparedGallery] from compose when images are already prepared (faster send).
     */
    suspend fun insertPostWithGallery(
        identityId: Long,
        content: String,
        galleryUris: List<Uri>,
        storeOriginalQuality: Boolean = false
    ): Long {
        if (galleryUris.isEmpty()) {
            return insertPostWithPreparedGallery(identityId, content, emptyList())
        }
        val prepared = withContext(Dispatchers.IO) {
            galleryUris.take(9).mapNotNull { uri ->
                PostAttachmentStorage.prepareOneGalleryImage(context, uri, storeOriginalQuality)
            }
        }
        return insertPostWithPreparedGallery(identityId, content, prepared)
    }

    /**
     * Inserts a post and moves [preparedFiles] (from [PostAttachmentStorage.prepareOneGalleryImage]) into
     * [PostAttachmentStorage.REL_ROOT] for that post. Deletes each prepared file after a successful move.
     */
    suspend fun insertPostWithPreparedGallery(
        identityId: Long,
        content: String,
        preparedFiles: List<File>,
        locationLabel: String? = null
    ): Long {
        val extrasJson = if (locationLabel.isNullOrBlank()) {
            "{}"
        } else {
            JSONObject().apply { put("location", locationLabel.trim()) }.toString()
        }
        val base = PostEntity(
            identityId = identityId,
            content = content.trim(),
            imageUris = "",
            extrasJson = extrasJson
        )
        val newId = postDao.insert(base)
        if (preparedFiles.isNotEmpty()) {
            val json = PostAttachmentStorage.movePreparedFilesIntoPost(context, newId, preparedFiles)
            if (json.isNotEmpty()) {
                postDao.update(base.copy(id = newId, imageUris = json))
            }
        }
        return newId
    }

    suspend fun getPostEntityById(id: Long): PostEntity? = postDao.getPostEntityById(id)

    suspend fun deletePost(post: PostEntity) {
        cancelPostRemindersInternal(post.id)
        PostAttachmentStorage.deleteAllForPost(context, post.id)
        postDao.delete(post)
    }

    suspend fun schedulePostReminder(postId: Long, fireAtMillis: Long) {
        withContext(Dispatchers.IO) {
            cancelPostRemindersInternal(postId)
            val rowId = postReminderDao.insert(
                PostReminderEntity(postId = postId, fireAtMillis = fireAtMillis)
            )
            PostReminderAlarmScheduler.schedule(context, rowId, postId, fireAtMillis)
        }
    }

    private suspend fun cancelPostRemindersInternal(postId: Long) {
        val rows = postReminderDao.listForPost(postId)
        for (r in rows) {
            PostReminderAlarmScheduler.cancel(context, r.id, r.postId)
        }
        postReminderDao.deleteByPostId(postId)
    }

    suspend fun togglePostLike(postId: Long) = postDao.toggleLike(postId)

    fun getCommentsByPost(postId: Long): Flow<List<CommentWithIdentity>> =
        commentDao.getCommentsByPost(postId)

    suspend fun insertComment(comment: CommentEntity): Long {
        val newId = commentDao.insert(comment)
        postDao.incrementCommentCount(comment.postId)
        return newId
    }

    suspend fun deleteComment(comment: CommentEntity) {
        commentDao.delete(comment)
        postDao.decrementCommentCount(comment.postId)
    }

    suspend fun likeComment(commentId: Long, identityId: Long) {
        commentDao.likeCommentById(commentId, identityId.toString())
    }

    suspend fun unlikeComment(commentId: Long, identityId: Long) {
        commentDao.unlikeCommentById(commentId, identityId.toString())
    }

    suspend fun updateComment(comment: CommentEntity) {
        commentDao.updateCommentContent(comment.id, comment.content)
    }

    fun getPostsByIdentity(identityId: Long): Flow<List<PostWithIdentity>> =
        postDao.getPostsByIdentity(identityId)

    companion object {
        private val DRAFT_CONTENT = stringPreferencesKey("draft_content")
        private val DRAFT_IDENTITY_ID = longPreferencesKey("draft_identity_id")
    }

    suspend fun saveDraft(content: String, identityId: Long) {
        context.draftDataStore.edit { prefs ->
            prefs[DRAFT_CONTENT] = content
            prefs[DRAFT_IDENTITY_ID] = identityId
        }
    }

    suspend fun loadDraft(): Pair<String, Long>? {
        val prefs = context.draftDataStore.data.first()
        val content = prefs[DRAFT_CONTENT]
        val identityId = prefs[DRAFT_IDENTITY_ID]
        return if (content.isNullOrEmpty() || identityId == null) null else content to identityId
    }

    suspend fun clearDraft() {
        context.draftDataStore.edit { it.clear() }
    }

    suspend fun exportAllData(): String {
        val identities = identityDao.getAllIdentities().first()
        val posts = postDao.getAllPosts().first()
        val comments = mutableListOf<CommentWithIdentity>()
        
        posts.forEach { post ->
            comments.addAll(commentDao.getCommentsByPost(post.id).first())
        }

        val json = JSONObject()
        
        val identitiesArray = JSONArray()
        identities.forEach { identity ->
            val identityJson = JSONObject().apply {
                put("id", identity.id)
                put("name", identity.name)
                put("avatarResName", identity.avatarResName)
                put("nationality", identity.nationality)
                put("gender", identity.gender)
                put("birthYear", identity.birthYear)
                put("deathYear", identity.deathYear)
                put("occupation", identity.occupation)
                put("motto", identity.motto)
                put("famousWork", identity.famousWork)
                put("bio", identity.bio)
                put("isActive", identity.isActive)
            }
            identitiesArray.put(identityJson)
        }
        json.put("identities", identitiesArray)

        val postsArray = JSONArray()
        posts.forEach { post ->
            val postJson = JSONObject().apply {
                put("id", post.id)
                put("identityId", post.identityId)
                put("content", post.content)
                put("imageUris", post.imageUris)
                put("extrasJson", post.extrasJson)
                put("createdAt", post.createdAt)
                put("likeCount", post.likeCount)
                put("commentCount", post.commentCount)
                put("isLiked", post.isLiked)
            }
            postsArray.put(postJson)
        }
        json.put("posts", postsArray)

        val commentsArray = JSONArray()
        comments.forEach { comment ->
            val commentJson = JSONObject().apply {
                put("id", comment.id)
                put("postId", comment.postId)
                put("identityId", comment.identityId)
                put("content", comment.content)
                put("createdAt", comment.createdAt)
            }
            commentsArray.put(commentJson)
        }
        json.put("comments", commentsArray)

        json.put("exportedAt", System.currentTimeMillis())
        json.put("version", 2)

        return json.toString(2)
    }

    /**
     * Writes `data.json` plus files under [PostAttachmentStorage.REL_ROOT] into a ZIP under cache.
     * Re-import via Settings → Import (merge or replace).
     */
    suspend fun exportAllDataZip(): File = withContext(Dispatchers.IO) {
        val json = exportAllData()
        val outFile = File(context.cacheDir, "pocket_weibo_backup_${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(outFile).buffered()).use { zos ->
            val jsonBytes = json.toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(jsonBytes)
            zos.closeEntry()
            val posts = postDao.getAllPosts().first()
            for (post in posts) {
                val paths = PostAttachmentStorage.parseStoredPaths(post.imageUris)
                for (rel in paths) {
                    val f = PostAttachmentStorage.fileForRelativePath(context, rel)
                    if (f.isFile) {
                        zos.putNextEntry(ZipEntry(rel.replace(File.separatorChar, '/')))
                        f.inputStream().use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        outFile
    }

    suspend fun exportAllDataToMarkdown(): String {
        val identities = identityDao.getAllIdentities().first()
        val posts = postDao.getAllPosts().first()
        val r = context.resources

        val sb = StringBuilder()
        sb.appendLine("# ${r.getString(R.string.md_backup_title)}")
        sb.appendLine()
        sb.appendLine("## ${r.getString(R.string.md_section_identities)}")
        sb.appendLine()

        identities.forEach { identity ->
            sb.appendLine("### ${identity.name}")
            sb.appendLine("- ${r.getString(R.string.md_field_nationality)}: ${identity.nationality}")
            sb.appendLine("- ${r.getString(R.string.md_field_gender)}: ${identity.gender}")
            sb.appendLine("- ${r.getString(R.string.md_field_occupation)}: ${identity.occupation}")
            sb.appendLine("- ${r.getString(R.string.md_field_motto)}: ${identity.motto}")
            sb.appendLine("- ${r.getString(R.string.md_field_work)}: ${identity.famousWork}")
            sb.appendLine("- ${r.getString(R.string.md_field_bio)}: ${identity.bio}")
            sb.appendLine()
        }

        sb.appendLine("## ${r.getString(R.string.md_section_posts)}")
        sb.appendLine()

        posts.forEach { post ->
            val identity = identities.find { it.id == post.identityId }
            val author = identity?.name ?: r.getString(R.string.md_unknown_author)
            sb.appendLine("### ${r.getString(R.string.md_post_heading, author)}")
            sb.appendLine()
            sb.appendLine(post.content)
            sb.appendLine()
            val attachmentCount = PostAttachmentStorage.parseStoredPaths(post.imageUris).size
            if (attachmentCount > 0) {
                sb.appendLine("- ${r.getString(R.string.md_field_attachments)}: $attachmentCount")
            }
            sb.appendLine("- ${r.getString(R.string.md_field_likes)}: ${post.likeCount}")
            sb.appendLine("- ${r.getString(R.string.md_field_comments)}: ${post.commentCount}")
            sb.appendLine(
                "- ${r.getString(R.string.md_field_posted_at)}: ${
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(post.createdAt))
                }"
            )
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        return sb.toString()
    }

    suspend fun importData(jsonString: String): Boolean =
        importData(jsonString, override = false, mergeAttachmentStaging = null, skipClearOnOverride = false)

    suspend fun importData(jsonString: String, override: Boolean): Boolean =
        importData(jsonString, override, mergeAttachmentStaging = null, skipClearOnOverride = false)

    /**
     * Detects ZIP (PK…) vs UTF-8 JSON text. ZIP must contain [data.json] and optional [PostAttachmentStorage.REL_ROOT]/.
     */
    suspend fun importBackupFromUri(uri: Uri, override: Boolean): Boolean {
        val isZip = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val buffered = BufferedInputStream(raw)
                buffered.mark(8)
                val sig = ByteArray(4)
                if (buffered.read(sig) != 4) return@use false
                sig[0] == 0x50.toByte() && sig[1] == 0x4b.toByte()
            } ?: false
        }
        return try {
            if (isZip) {
                importZipBackup(uri, override)
            } else {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }
                importData(text, override, mergeAttachmentStaging = null, skipClearOnOverride = false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun importZipBackup(uri: Uri, override: Boolean): Boolean {
        return try {
            if (override) {
                clearAllData()
                readZip(uri) { name, content ->
                    when {
                        name == "data.json" -> Unit
                        name.startsWith("${PostAttachmentStorage.REL_ROOT}/") -> {
                            val out = PostAttachmentStorage.fileForRelativePath(context, name)
                            out.parentFile?.mkdirs()
                            out.writeBytes(content)
                        }
                    }
                }
                val jsonText = readZipEntryBytes(uri, "data.json")?.toString(Charsets.UTF_8)
                if (jsonText == null) {
                    false
                } else {
                    importData(
                        jsonText,
                        override = true,
                        mergeAttachmentStaging = null,
                        skipClearOnOverride = true
                    )
                }
            } else {
                val staging = File(context.cacheDir, "pw_import_${System.currentTimeMillis()}")
                staging.mkdirs()
                try {
                    readZip(uri) { name, content ->
                        if (name.endsWith("/")) return@readZip
                        val out = File(staging, name.replace('\\', '/'))
                        out.parentFile?.mkdirs()
                        out.writeBytes(content)
                    }
                    val jsonFile = File(staging, "data.json")
                    if (!jsonFile.exists()) {
                        false
                    } else {
                        importData(
                            jsonFile.readText(),
                            override = false,
                            mergeAttachmentStaging = staging,
                            skipClearOnOverride = false
                        )
                    }
                } finally {
                    staging.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun readZipEntryBytes(uri: Uri, entryName: String): ByteArray? {
        var found: ByteArray? = null
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (name == entryName) {
                        found = zis.readBytes()
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return found
    }

    private fun readZip(uri: Uri, onEntry: (name: String, bytes: ByteArray) -> Unit) {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (!entry.isDirectory && name.isNotEmpty()) {
                        onEntry(name, zis.readBytes())
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    suspend fun importData(
        jsonString: String,
        override: Boolean,
        mergeAttachmentStaging: File?,
        skipClearOnOverride: Boolean = false
    ): Boolean {
        return try {
            val json = JSONObject(jsonString.trim().trimStart('\uFEFF'))

            if (override) {
                if (!skipClearOnOverride) clearAllData()
                if (json.has("identities")) {
                    val identitiesArray = json.getJSONArray("identities")
                    for (i in 0 until identitiesArray.length()) {
                        val identityJson = identitiesArray.getJSONObject(i)
                        identityDao.insert(identityFromJson(identityJson, identityJson.getLong("id")))
                    }
                }
                if (json.has("posts")) {
                    val postsArray = json.getJSONArray("posts")
                    for (i in 0 until postsArray.length()) {
                        val postJson = postsArray.getJSONObject(i)
                        postDao.insert(postFromJson(postJson, postJson.getLong("id")))
                    }
                }
                if (json.has("comments")) {
                    val commentsArray = json.getJSONArray("comments")
                    for (i in 0 until commentsArray.length()) {
                        val commentJson = commentsArray.getJSONObject(i)
                        commentDao.insert(commentFromJson(commentJson, commentJson.getLong("id")))
                    }
                }
            } else {
                val identityOldToNew = mutableMapOf<Long, Long>()
                if (json.has("identities")) {
                    val identitiesArray = json.getJSONArray("identities")
                    for (i in 0 until identitiesArray.length()) {
                        val identityJson = identitiesArray.getJSONObject(i)
                        val oldId = identityJson.getLong("id")
                        val newId = identityDao.insert(identityFromJson(identityJson, 0L))
                        identityOldToNew[oldId] = newId
                    }
                }
                val postOldToNew = mutableMapOf<Long, Long>()
                if (json.has("posts")) {
                    val postsArray = json.getJSONArray("posts")
                    for (i in 0 until postsArray.length()) {
                        val postJson = postsArray.getJSONObject(i)
                        val oldPostId = postJson.getLong("id")
                        val oldIdentityId = postJson.getLong("identityId")
                        val newIdentityId = identityOldToNew[oldIdentityId] ?: continue
                        val stripImages = mergeAttachmentStaging != null
                        val newPostId = postDao.insert(
                            postFromJson(
                                postJson,
                                id = 0L,
                                identityId = newIdentityId,
                                imageUrisOverride = if (stripImages) "" else null
                            )
                        )
                        postOldToNew[oldPostId] = newPostId
                        if (mergeAttachmentStaging != null) {
                            val merged = mergeStagingAttachments(
                                mergeAttachmentStaging,
                                oldPostId,
                                newPostId,
                                postJson.optString("imageUris", "")
                            )
                            if (merged.isNotEmpty()) {
                                val current = postFromJson(postJson, newPostId, newIdentityId)
                                postDao.update(current.copy(imageUris = merged))
                            }
                        }
                    }
                }
                if (json.has("comments")) {
                    val commentsArray = json.getJSONArray("comments")
                    for (i in 0 until commentsArray.length()) {
                        val commentJson = commentsArray.getJSONObject(i)
                        val oldPostId = commentJson.getLong("postId")
                        val newPostId = postOldToNew[oldPostId] ?: continue
                        val oldIdentityId = commentJson.getLong("identityId")
                        val newIdentityId = identityOldToNew[oldIdentityId] ?: continue
                        commentDao.insert(commentFromJson(commentJson, 0L, newPostId, newIdentityId))
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun identityFromJson(identityJson: JSONObject, id: Long): IdentityEntity {
        val birthYear = when {
            !identityJson.has("birthYear") || identityJson.isNull("birthYear") -> null
            else -> identityJson.optInt("birthYear")
        }
        val deathYear = when {
            !identityJson.has("deathYear") || identityJson.isNull("deathYear") -> null
            else -> identityJson.optInt("deathYear")
        }
        return IdentityEntity(
            id = id,
            name = identityJson.getString("name"),
            avatarResName = identityJson.optString("avatarResName", "avatar_default"),
            nationality = identityJson.optString("nationality", ""),
            gender = when (identityJson.optString("gender", "").uppercase(Locale.US)) {
                "MALE" -> Gender.MALE
                "FEMALE" -> Gender.FEMALE
                else -> Gender.OTHER
            },
            birthYear = birthYear,
            deathYear = deathYear,
            occupation = identityJson.optString("occupation", ""),
            motto = identityJson.optString("motto", ""),
            famousWork = identityJson.optString("famousWork", ""),
            bio = identityJson.optString("bio", ""),
            isActive = identityJson.optBoolean("isActive", false)
        )
    }

    private fun postFromJson(
        postJson: JSONObject,
        id: Long,
        identityId: Long? = null,
        imageUrisOverride: String? = null
    ): PostEntity {
        val resolvedIdentityId = identityId ?: postJson.getLong("identityId")
        return PostEntity(
            id = id,
            identityId = resolvedIdentityId,
            content = postJson.optString("content", ""),
            imageUris = imageUrisOverride ?: postJson.optString("imageUris", ""),
            extrasJson = postJson.optString("extrasJson", "{}"),
            createdAt = postJson.getLong("createdAt"),
            likeCount = postJson.optInt("likeCount", 0),
            commentCount = postJson.optInt("commentCount", 0),
            isLiked = postJson.optBoolean("isLiked", false)
        )
    }

    private fun mergeStagingAttachments(
        stagingRoot: File,
        oldPostId: Long,
        newPostId: Long,
        imageUrisField: String
    ): String {
        val oldPaths = PostAttachmentStorage.parseStoredPaths(imageUrisField)
        if (oldPaths.isEmpty()) return ""
        val prefix = "${PostAttachmentStorage.REL_ROOT}/$oldPostId/"
        val newPrefix = "${PostAttachmentStorage.REL_ROOT}/$newPostId/"
        val kept = mutableListOf<String>()
        for (rel in oldPaths) {
            val newRel = if (rel.startsWith(prefix)) {
                newPrefix + rel.removePrefix(prefix)
            } else {
                rel
            }
            val src = File(stagingRoot, rel)
            if (src.isFile) {
                val dst = PostAttachmentStorage.fileForRelativePath(context, newRel)
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
                kept.add(newRel)
            }
        }
        return if (kept.isEmpty()) "" else PostAttachmentStorage.serializePaths(kept)
    }

    private fun commentFromJson(
        commentJson: JSONObject,
        id: Long,
        postId: Long? = null,
        identityId: Long? = null
    ): CommentEntity {
        val resolvedPostId = postId ?: commentJson.getLong("postId")
        val resolvedIdentityId = identityId ?: commentJson.getLong("identityId")
        return CommentEntity(
            id = id,
            postId = resolvedPostId,
            identityId = resolvedIdentityId,
            content = commentJson.getString("content"),
            createdAt = commentJson.getLong("createdAt")
        )
    }

    suspend fun clearAllData() {
        commentDao.deleteAll()
        postDao.deleteAll()
        identityDao.deleteAll()
        PostAttachmentStorage.deleteEntireAttachmentTree(context)
    }
}
