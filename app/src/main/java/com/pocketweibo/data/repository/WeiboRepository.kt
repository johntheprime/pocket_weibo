package com.pocketweibo.data.repository

import android.content.Context
import android.net.Uri
import com.pocketweibo.diagnostic.DiagnosticLog
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketweibo.data.local.dao.CommentDao
import com.pocketweibo.data.local.dao.CommentSearchRow
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.local.dao.IdentityDao
import com.pocketweibo.data.local.dao.PostDao
import com.pocketweibo.data.local.dao.PostReminderDao
import com.pocketweibo.data.local.dao.PostReminderWithPreview
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.Gender
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import com.pocketweibo.data.local.entity.PostReminderEntity
import com.pocketweibo.data.media.CommentAttachmentStorage
import com.pocketweibo.data.media.IdentityAvatarStorage
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.data.media.PostVoice
import com.pocketweibo.reminder.PostReminderAlarmScheduler
import com.pocketweibo.reminder.ReminderRepeatRule
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

private const val REMINDER_LOG_TAG = "PW_Reminder"

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
    val allCommentsForSearch: Flow<List<CommentSearchRow>> = commentDao.observeAllCommentsForSearch()

    suspend fun getIdentityById(id: Long): IdentityEntity? = identityDao.getIdentityById(id)

    suspend fun insertIdentity(identity: IdentityEntity): Long = withContext(Dispatchers.IO) {
        if (identity.id == 0L) {
            identityDao.insert(identity)
        } else {
            identityDao.update(identity)
            identity.id
        }
    }

    suspend fun updateIdentity(identity: IdentityEntity) = identityDao.update(identity)

    suspend fun clearCustomAvatar(identityId: Long) = identityDao.clearCustomAvatar(identityId)

    /**
     * Inserts or replaces [identity], then optionally writes a gallery [pickedAvatarUri] into
     * [IdentityAvatarStorage] or removes stored custom art when [deleteCustomAvatar] is true.
     */
    suspend fun saveIdentityWithAvatarOptions(
        identity: IdentityEntity,
        pickedAvatarUri: Uri?,
        deleteCustomAvatar: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val customForRow = when {
            pickedAvatarUri != null -> null
            deleteCustomAvatar -> null
            else -> identity.customAvatarUri
        }
        val toSave = identity.copy(customAvatarUri = customForRow)
        val rowId = if (toSave.id == 0L) {
            identityDao.insert(toSave)
        } else {
            identityDao.update(toSave)
            toSave.id
        }

        if (pickedAvatarUri != null) {
            if (writePickerToIdentityAvatar(rowId, pickedAvatarUri)) {
                val rel = IdentityAvatarStorage.relativePath(rowId)
                val cur = identityDao.getIdentityById(rowId) ?: return@withContext rowId
                identityDao.update(cur.copy(customAvatarUri = rel))
            }
        } else if (deleteCustomAvatar) {
            IdentityAvatarStorage.deleteForIdentity(context, rowId)
        }
        rowId
    }

    private suspend fun writePickerToIdentityAvatar(identityId: Long, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val prepared = PostAttachmentStorage.prepareOneGalleryImage(
                context,
                source,
                storeOriginalQuality = false
            ) ?: return@withContext false
            try {
                val rel = IdentityAvatarStorage.relativePath(identityId)
                val dest = IdentityAvatarStorage.fileForRelativePath(context, rel)
                dest.parentFile?.mkdirs()
                if (dest.exists()) dest.delete()
                prepared.copyTo(dest, overwrite = true)
                dest.isFile && dest.length() > 0L
            } finally {
                if (prepared.exists()) prepared.delete()
            }
        }

    suspend fun deleteIdentity(identity: IdentityEntity) = withContext(Dispatchers.IO) {
        val wasActive = identity.isActive
        IdentityAvatarStorage.deleteForIdentity(context, identity.id)
        identityDao.delete(identity)
        if (wasActive) {
            identityDao.deactivateAll()
            identityDao.getFirstIdentityByCreatedAt()?.let { identityDao.activate(it.id) }
        }
    }

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
        preparedVoice: File? = null,
    ): Long {
        val base = PostEntity(
            identityId = identityId,
            content = content.trim(),
            imageUris = "",
            audioPath = "",
            extrasJson = "{}"
        )
        val newId = postDao.insert(base)
        var imageJson = ""
        if (preparedFiles.isNotEmpty()) {
            imageJson = PostAttachmentStorage.movePreparedFilesIntoPost(context, newId, preparedFiles)
        }
        var audioRel = ""
        if (preparedVoice != null) {
            audioRel = PostAttachmentStorage.movePreparedVoiceIntoPost(context, newId, preparedVoice)
        }
        if (imageJson.isNotEmpty() || audioRel.isNotEmpty()) {
            val cur = postDao.getPostEntityById(newId)!!
            postDao.update(
                cur.copy(
                    imageUris = imageJson.ifEmpty { cur.imageUris },
                    audioPath = audioRel.ifEmpty { cur.audioPath },
                )
            )
        }
        return newId
    }

    suspend fun getPostEntityById(id: Long): PostEntity? = postDao.getPostEntityById(id)

    suspend fun deletePost(post: PostEntity) {
        cancelPostRemindersInternal(post.id)
        for (cid in commentDao.listCommentIdsForPost(post.id)) {
            CommentAttachmentStorage.deleteForComment(context, cid)
        }
        PostAttachmentStorage.deleteAllForPost(context, post.id)
        postDao.delete(post)
    }

    suspend fun schedulePostReminder(
        postId: Long,
        fireAtMillis: Long,
        repeatRule: String = ReminderRepeatRule.NONE
    ) {
        withContext(Dispatchers.IO) {
            cancelPostRemindersInternal(postId)
            val rowId = postReminderDao.insert(
                PostReminderEntity(
                    postId = postId,
                    fireAtMillis = fireAtMillis,
                    repeatRule = repeatRule
                )
            )
            DiagnosticLog.d(
                REMINDER_LOG_TAG,
                "DB insert reminder rowId=$rowId postId=$postId fireAtMillis=$fireAtMillis repeat=$repeatRule"
            )
            PostReminderAlarmScheduler.schedule(context, rowId, postId, fireAtMillis)
        }
    }

    /**
     * Re-register [AlarmManager] from DB (source of truth). Call on process start and after reboot.
     * Overdue rows are delivered immediately so notifications are not silently lost.
     */
    suspend fun rescheduleAllPostRemindersFromDb() = withContext(Dispatchers.IO) {
        val rows = postReminderDao.listAll()
        val now = System.currentTimeMillis()
        DiagnosticLog.d(REMINDER_LOG_TAG, "rescheduleAllFromDb count=${rows.size} now=$now")
        for (r in rows) {
            if (r.fireAtMillis <= now) {
                DiagnosticLog.w(
                    REMINDER_LOG_TAG,
                    "stale reminder id=${r.id} postId=${r.postId} fireAt=${r.fireAtMillis} — immediate recovery"
                )
                PostReminderAlarmScheduler.deliverImmediately(context, r.id, r.postId)
            } else {
                PostReminderAlarmScheduler.schedule(context, r.id, r.postId, r.fireAtMillis)
            }
        }
    }

    private suspend fun cancelPostRemindersInternal(postId: Long) {
        val rows = postReminderDao.listForPost(postId)
        for (r in rows) {
            PostReminderAlarmScheduler.cancel(context, r.id, r.postId)
        }
        postReminderDao.deleteByPostId(postId)
    }

    fun observePendingRemindersWithPreview(): Flow<List<PostReminderWithPreview>> =
        postReminderDao.observePendingRemindersWithPreview()

    suspend fun cancelReminderById(reminderId: Long) {
        withContext(Dispatchers.IO) {
            val row = postReminderDao.getById(reminderId) ?: return@withContext
            PostReminderAlarmScheduler.cancel(context, row.id, row.postId)
            postReminderDao.deleteById(reminderId)
        }
    }

    suspend fun togglePostLike(postId: Long) = postDao.toggleLike(postId)

    fun getCommentsByPost(postId: Long): Flow<List<CommentWithIdentity>> =
        commentDao.getCommentsByPost(postId)

    suspend fun insertComment(comment: CommentEntity): Long {
        val newId = commentDao.insert(comment)
        postDao.incrementCommentCount(comment.postId)
        return newId
    }

    /**
     * Adds a comment; [text] may be blank when [preparedVoice] is set (stored as [PostVoice.STORED_PLACEHOLDER]).
     * Returns new row id, or **-1** when nothing to insert or voice file could not be saved.
     */
    suspend fun insertCommentWithOptionalVoice(
        postId: Long,
        identityId: Long,
        text: String,
        preparedVoice: File?,
        replyingToCommentId: Long? = null,
    ): Long {
        val trimmed = text.trim()
        val hasVoice = preparedVoice != null && preparedVoice.isFile && preparedVoice.length() > 0L
        if (trimmed.isEmpty() && !hasVoice) return -1L
        val content = if (hasVoice && trimmed.isEmpty()) PostVoice.STORED_PLACEHOLDER else trimmed
        val newId = commentDao.insert(
            CommentEntity(
                postId = postId,
                identityId = identityId,
                content = content,
                replyingToCommentId = replyingToCommentId,
            )
        )
        postDao.incrementCommentCount(postId)
        if (hasVoice) {
            val rel = CommentAttachmentStorage.movePreparedVoiceIntoComment(context, newId, preparedVoice!!)
            if (rel.isEmpty()) {
                val inserted = commentDao.getEntityById(newId)!!
                commentDao.delete(inserted)
                postDao.decrementCommentCount(postId)
                return -1L
            }
            val cur = commentDao.getEntityById(newId)!!
            commentDao.update(cur.copy(audioPath = rel))
        }
        return newId
    }

    suspend fun deleteComment(commentId: Long) {
        val entity = commentDao.getEntityById(commentId) ?: return
        CommentAttachmentStorage.deleteForComment(context, commentId)
        commentDao.delete(entity)
        postDao.decrementCommentCount(entity.postId)
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
                identity.customAvatarUri?.takeIf { it.isNotBlank() }?.let { put("customAvatarUri", it) }
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
                if (post.identityId != null) put("identityId", post.identityId) else put("identityId", JSONObject.NULL)
                put("content", post.content)
                put("imageUris", post.imageUris)
                put("audioPath", post.audioPath)
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
                if (comment.identityId != null) put("identityId", comment.identityId) else put("identityId", JSONObject.NULL)
                put("content", comment.content)
                put("audioPath", comment.audioPath)
                put("createdAt", comment.createdAt)
                put("replyingToCommentId", comment.replyingToCommentId ?: JSONObject.NULL)
                put("likeCount", comment.likeCount)
                put("likedBy", comment.likedBy)
            }
            commentsArray.put(commentJson)
        }
        json.put("comments", commentsArray)

        json.put("exportedAt", System.currentTimeMillis())
        json.put("version", 2)

        return json.toString(2)
    }

    /**
     * JSON only: identities (no [IdentityEntity.customAvatarUri]), posts with empty [PostEntity.imageUris],
     * comments, and [post_reminders] rows. For automatic encrypted daily backups (no binary media).
     */
    suspend fun exportTextOnlyForAutoBackup(): String = withContext(Dispatchers.IO) {
        val identities = identityDao.getAllIdentities().first()
        val posts = postDao.getAllPosts().first()
        val comments = commentDao.listAllForBackup()
        val reminders = postReminderDao.listAll()

        val json = JSONObject()
        json.put("kind", "pocket_weibo_text_auto_backup")
        json.put("exportedAt", System.currentTimeMillis())
        json.put("version", 1)

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
                put("createdAt", identity.createdAt)
                put("isActive", identity.isActive)
            }
            identitiesArray.put(identityJson)
        }
        json.put("identities", identitiesArray)

        val postsArray = JSONArray()
        posts.forEach { post ->
            val postJson = JSONObject().apply {
                put("id", post.id)
                if (post.identityId != null) put("identityId", post.identityId) else put("identityId", JSONObject.NULL)
                put("content", post.content)
                put("imageUris", "")
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
                if (comment.identityId != null) put("identityId", comment.identityId) else put("identityId", JSONObject.NULL)
                put("content", comment.content)
                put("audioPath", comment.audioPath)
                put("createdAt", comment.createdAt)
                put("replyingToCommentId", comment.replyingToCommentId ?: JSONObject.NULL)
                put("likeCount", comment.likeCount)
                put("likedBy", comment.likedBy)
            }
            commentsArray.put(commentJson)
        }
        json.put("comments", commentsArray)

        val remindersArray = JSONArray()
        reminders.forEach { r ->
            remindersArray.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("postId", r.postId)
                    put("fireAtMillis", r.fireAtMillis)
                    put("repeatRule", r.repeatRule)
                }
            )
        }
        json.put("postReminders", remindersArray)

        json.toString(2)
    }

    /**
     * Writes `data.json` plus files under [PostAttachmentStorage.REL_ROOT],
     * [CommentAttachmentStorage.REL_ROOT], and custom avatars into a ZIP under cache.
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
                val audio = post.audioPath.trim()
                if (audio.isNotEmpty()) {
                    val af = PostAttachmentStorage.fileForRelativePath(context, audio)
                    if (af.isFile) {
                        zos.putNextEntry(ZipEntry(audio.replace(File.separatorChar, '/')))
                        af.inputStream().use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            val identitiesZip = identityDao.getAllIdentities().first()
            for (ident in identitiesZip) {
                val rel = ident.customAvatarUri?.trim()?.takeIf { it.isNotEmpty() && !it.contains("://") }
                    ?: continue
                val f = IdentityAvatarStorage.fileForRelativePath(context, rel)
                if (f.isFile) {
                    zos.putNextEntry(ZipEntry(rel.replace(File.separatorChar, '/')))
                    f.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            for (c in commentDao.listAllForBackup()) {
                val rel = c.audioPath.trim()
                if (rel.isEmpty()) continue
                val cf = CommentAttachmentStorage.fileForRelativePath(context, rel)
                if (cf.isFile) {
                    zos.putNextEntry(ZipEntry(rel.replace(File.separatorChar, '/')))
                    cf.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
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
            val identity = post.identityId?.let { id -> identities.find { it.id == id } }
            val author = identity?.name
                ?: if (post.identityId == null) r.getString(R.string.deleted_identity_label)
                else r.getString(R.string.md_unknown_author)
            sb.appendLine("### ${r.getString(R.string.md_post_heading, author)}")
            sb.appendLine()
            sb.appendLine(post.content)
            sb.appendLine()
            val attachmentCount = PostAttachmentStorage.parseStoredPaths(post.imageUris).size
            if (attachmentCount > 0) {
                sb.appendLine("- ${r.getString(R.string.md_field_attachments)}: $attachmentCount")
            }
            if (post.audioPath.isNotBlank()) {
                sb.appendLine("- ${r.getString(R.string.md_field_voice_attachment)}")
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
                        name.startsWith("${CommentAttachmentStorage.REL_ROOT}/") -> {
                            val out = CommentAttachmentStorage.fileForRelativePath(context, name)
                            out.parentFile?.mkdirs()
                            out.writeBytes(content)
                        }
                        name.startsWith("${IdentityAvatarStorage.REL_ROOT}/") -> {
                            val out = File(context.filesDir, name)
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
                        val parsed = identityFromJson(identityJson, 0L)
                        val toInsert = if (mergeAttachmentStaging != null) {
                            parsed.copy(customAvatarUri = null)
                        } else {
                            parsed
                        }
                        val newId = identityDao.insert(toInsert)
                        identityOldToNew[oldId] = newId
                        if (mergeAttachmentStaging != null) {
                            val oldPath = identityJson.optString("customAvatarUri", "").takeIf { it.isNotBlank() }
                            if (!oldPath.isNullOrBlank() &&
                                mergeStagingIdentityAvatar(mergeAttachmentStaging, oldPath, newId)
                            ) {
                                val cur = identityDao.getIdentityById(newId)!!
                                identityDao.update(
                                    cur.copy(customAvatarUri = IdentityAvatarStorage.relativePath(newId))
                                )
                            }
                        }
                    }
                }
                val postOldToNew = mutableMapOf<Long, Long>()
                if (json.has("posts")) {
                    val postsArray = json.getJSONArray("posts")
                    for (i in 0 until postsArray.length()) {
                        val postJson = postsArray.getJSONObject(i)
                        val oldPostId = postJson.getLong("id")
                        val newIdentityId: Long? = if (postJson.isNull("identityId")) {
                            null
                        } else {
                            identityOldToNew[postJson.getLong("identityId")]
                        }
                        val stripImages = mergeAttachmentStaging != null
                        val newPostId = postDao.insert(
                            postFromJson(
                                postJson,
                                id = 0L,
                                imageUrisOverride = if (stripImages) "" else null,
                                audioPathOverride = if (stripImages) "" else null,
                                identityId = newIdentityId,
                                useIdentityFromJson = false
                            )
                        )
                        postOldToNew[oldPostId] = newPostId
                        if (mergeAttachmentStaging != null) {
                            val mergedImages = mergeStagingAttachments(
                                mergeAttachmentStaging,
                                oldPostId,
                                newPostId,
                                postJson.optString("imageUris", "")
                            )
                            val mergedAudio = mergeStagingAudio(
                                mergeAttachmentStaging,
                                oldPostId,
                                newPostId,
                                postJson.optString("audioPath", "")
                            )
                            if (mergedImages.isNotEmpty() || mergedAudio.isNotEmpty()) {
                                val current = postDao.getPostEntityById(newPostId)!!
                                postDao.update(
                                    current.copy(
                                        imageUris = mergedImages.ifEmpty { current.imageUris },
                                        audioPath = mergedAudio.ifEmpty { current.audioPath },
                                    )
                                )
                            }
                        }
                    }
                }
                if (json.has("comments")) {
                    val commentsArray = json.getJSONArray("comments")
                    val sorted = (0 until commentsArray.length())
                        .map { commentsArray.getJSONObject(it) }
                        .sortedBy { it.getLong("createdAt") }
                    val commentOldToNew = mutableMapOf<Long, Long>()
                    for (commentJson in sorted) {
                        val oldPostId = commentJson.getLong("postId")
                        val newPostId = postOldToNew[oldPostId] ?: continue
                        val newIdentityId: Long? = if (commentJson.isNull("identityId")) {
                            null
                        } else {
                            identityOldToNew[commentJson.getLong("identityId")]
                        }
                        val oldCommentId = commentJson.getLong("id")
                        val oldReplying = when {
                            !commentJson.has("replyingToCommentId") ||
                                commentJson.isNull("replyingToCommentId") -> null
                            else -> commentJson.getLong("replyingToCommentId")
                        }
                        val newReplying = oldReplying?.let { commentOldToNew[it] }
                        val stripAudio = mergeAttachmentStaging != null
                        val newCommentId = commentDao.insert(
                            commentFromJson(
                                commentJson,
                                id = 0L,
                                postId = newPostId,
                                identityId = newIdentityId,
                                useFixedPostAndIdentity = true,
                                replyingToCommentId = newReplying,
                                useExplicitReplying = true,
                                audioPathOverride = if (stripAudio) "" else null,
                            )
                        )
                        commentOldToNew[oldCommentId] = newCommentId
                        if (mergeAttachmentStaging != null) {
                            val mergedAudio = mergeStagingCommentAudio(
                                mergeAttachmentStaging,
                                oldCommentId,
                                newCommentId,
                                commentJson.optString("audioPath", ""),
                            )
                            if (mergedAudio.isNotEmpty()) {
                                val cur = commentDao.getEntityById(newCommentId)!!
                                commentDao.update(cur.copy(audioPath = mergedAudio))
                            }
                        }
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
            customAvatarUri = identityJson.optString("customAvatarUri", "").takeIf { it.isNotBlank() },
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
        imageUrisOverride: String? = null,
        audioPathOverride: String? = null,
        identityId: Long? = null,
        useIdentityFromJson: Boolean = true
    ): PostEntity {
        val resolvedIdentityId = if (useIdentityFromJson) {
            when {
                postJson.isNull("identityId") -> null
                else -> postJson.getLong("identityId")
            }
        } else {
            identityId
        }
        return PostEntity(
            id = id,
            identityId = resolvedIdentityId,
            content = postJson.optString("content", ""),
            imageUris = imageUrisOverride ?: postJson.optString("imageUris", ""),
            audioPath = audioPathOverride ?: postJson.optString("audioPath", ""),
            extrasJson = postJson.optString("extrasJson", "{}"),
            createdAt = postJson.getLong("createdAt"),
            likeCount = postJson.optInt("likeCount", 0),
            commentCount = postJson.optInt("commentCount", 0),
            isLiked = postJson.optBoolean("isLiked", false)
        )
    }

    private fun mergeStagingAudio(
        stagingRoot: File,
        oldPostId: Long,
        newPostId: Long,
        audioPath: String,
    ): String {
        val t = audioPath.trim().replace('\\', '/').trimStart('/')
        if (t.isEmpty()) return ""
        val prefix = "${PostAttachmentStorage.REL_ROOT}/$oldPostId/"
        val newPrefix = "${PostAttachmentStorage.REL_ROOT}/$newPostId/"
        val newRel = if (t.startsWith(prefix)) {
            newPrefix + t.removePrefix(prefix)
        } else {
            t
        }
        val src = File(stagingRoot, t)
        if (!src.isFile) return ""
        val dst = PostAttachmentStorage.fileForRelativePath(context, newRel)
        dst.parentFile?.mkdirs()
        return try {
            src.copyTo(dst, overwrite = true)
            if (dst.isFile && dst.length() > 0L) newRel else ""
        } catch (_: Exception) {
            ""
        }
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

    private fun mergeStagingCommentAudio(
        stagingRoot: File,
        oldCommentId: Long,
        newCommentId: Long,
        audioPath: String,
    ): String {
        val t = audioPath.trim().replace('\\', '/').trimStart('/')
        if (t.isEmpty()) return ""
        val prefix = "${CommentAttachmentStorage.REL_ROOT}/$oldCommentId/"
        val newPrefix = "${CommentAttachmentStorage.REL_ROOT}/$newCommentId/"
        val newRel = if (t.startsWith(prefix)) {
            newPrefix + t.removePrefix(prefix)
        } else {
            t
        }
        val src = File(stagingRoot, t)
        if (!src.isFile) return ""
        val dst = CommentAttachmentStorage.fileForRelativePath(context, newRel)
        dst.parentFile?.mkdirs()
        return try {
            src.copyTo(dst, overwrite = true)
            if (dst.isFile && dst.length() > 0L) newRel else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun commentFromJson(
        commentJson: JSONObject,
        id: Long,
        postId: Long? = null,
        identityId: Long? = null,
        useFixedPostAndIdentity: Boolean = false,
        replyingToCommentId: Long? = null,
        useExplicitReplying: Boolean = false,
        audioPathOverride: String? = null,
    ): CommentEntity {
        val resolvedPostId = if (useFixedPostAndIdentity) postId!! else commentJson.getLong("postId")
        val resolvedIdentityId = if (useFixedPostAndIdentity) {
            identityId
        } else {
            when {
                commentJson.isNull("identityId") -> null
                else -> commentJson.getLong("identityId")
            }
        }
        val replying = if (useExplicitReplying) {
            replyingToCommentId
        } else {
            when {
                !commentJson.has("replyingToCommentId") || commentJson.isNull("replyingToCommentId") -> null
                else -> commentJson.getLong("replyingToCommentId")
            }
        }
        val audioPath = audioPathOverride ?: commentJson.optString("audioPath", "")
        return CommentEntity(
            id = id,
            postId = resolvedPostId,
            identityId = resolvedIdentityId,
            content = commentJson.optString("content", ""),
            audioPath = audioPath,
            createdAt = commentJson.getLong("createdAt"),
            replyingToCommentId = replying,
            likeCount = commentJson.optInt("likeCount", 0),
            likedBy = commentJson.optString("likedBy", "")
        )
    }

    suspend fun clearAllData() {
        commentDao.deleteAll()
        postDao.deleteAll()
        identityDao.deleteAll()
        PostAttachmentStorage.deleteEntireAttachmentTree(context)
        CommentAttachmentStorage.deleteEntireTree(context)
        IdentityAvatarStorage.deleteEntireTree(context)
    }

    private fun mergeStagingIdentityAvatar(
        stagingRoot: File,
        oldRelative: String,
        newIdentityId: Long
    ): Boolean {
        val norm = oldRelative.replace('\\', '/').trimStart('/')
        val src = File(stagingRoot, norm)
        if (!src.isFile) return false
        val dest = IdentityAvatarStorage.fileForRelativePath(
            context,
            IdentityAvatarStorage.relativePath(newIdentityId)
        )
        dest.parentFile?.mkdirs()
        return try {
            src.copyTo(dest, overwrite = true)
            dest.isFile && dest.length() > 0L
        } catch (_: Exception) {
            false
        }
    }
}
