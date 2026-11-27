package com.pocketweibo.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.backup.ZipSafety
import kotlinx.coroutines.flow.flatMapLatest
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
import com.pocketweibo.data.local.dao.IdentityScheduleDao
import com.pocketweibo.data.local.dao.IdentityScheduleWithIdentityName
import com.pocketweibo.data.local.dao.PostDao
import com.pocketweibo.data.local.dao.LastReminderFiring
import com.pocketweibo.data.local.dao.PostReminderDao
import com.pocketweibo.data.local.dao.PostReminderFiringDao
import com.pocketweibo.data.local.dao.PostReminderWithPreview
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.dao.TopicDao
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.Gender
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.IdentityScheduleEntity
import com.pocketweibo.data.local.entity.PostEntity
import com.pocketweibo.data.local.entity.PostReminderEntity
import com.pocketweibo.data.local.entity.PostReminderFiringEntity
import com.pocketweibo.data.local.entity.TopicAliasEntity
import com.pocketweibo.data.local.entity.TopicExclusionEntity
import com.pocketweibo.data.media.CommentAttachmentStorage
import com.pocketweibo.data.media.IdentityAvatarStorage
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.data.media.PostVoice
import com.pocketweibo.data.prefs.UiPreferences
import com.pocketweibo.reminder.IdentityScheduleAlarmScheduler
import com.pocketweibo.reminder.IdentityScheduleNextFire
import com.pocketweibo.reminder.PostReminderAlarmScheduler
import com.pocketweibo.reminder.PostReminderCalendarSync
import com.pocketweibo.reminder.ReminderRepeatRule
import com.pocketweibo.R
import com.pocketweibo.ui.util.resolveTopicKey
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
import java.util.zip.ZipOutputStream

private val Context.draftDataStore by preferencesDataStore(name = "draft")

private const val REMINDER_LOG_TAG = "PW_Reminder"

class WeiboRepository(
    private val database: AppDatabase,
    private val identityDao: IdentityDao,
    private val identityScheduleDao: IdentityScheduleDao,
    private val postDao: PostDao,
    private val commentDao: CommentDao,
    private val postReminderDao: PostReminderDao,
    private val postReminderFiringDao: PostReminderFiringDao,
    private val topicDao: TopicDao,
    private val context: Context
) {
    val allIdentities: Flow<List<IdentityEntity>> = identityDao.getAllIdentities()
    val activeIdentity: Flow<IdentityEntity?> = identityDao.getActiveIdentity()
    val allPosts: Flow<List<PostWithIdentity>> = postDao.getAllPosts()
    val allCommentsForSearch: Flow<List<CommentSearchRow>> = commentDao.observeAllCommentsForSearch()
    val topicAliases: Flow<List<TopicAliasEntity>> = topicDao.observeAliases()
    val topicExclusions: Flow<List<TopicExclusionEntity>> = topicDao.observeExclusions()

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
        // REVIEW-3 §2.1: delete + active reassignment commit together; the file goes after commit.
        database.withTransaction {
            identityDao.delete(identity)
            if (wasActive) {
                identityDao.deactivateAll()
                identityDao.getFirstIdentityByCreatedAt()?.let { identityDao.activate(it.id) }
            }
        }
        IdentityAvatarStorage.deleteForIdentity(context, identity.id)
    }

    suspend fun setActiveIdentity(id: Long) {
        // REVIEW-3 §2.1: never observable with zero active identities.
        database.withTransaction {
            identityDao.deactivateAll()
            identityDao.activate(id)
        }
    }

    fun observeIdentitySchedules(): Flow<List<IdentityScheduleWithIdentityName>> =
        identityScheduleDao.observeAll()

    suspend fun insertIdentitySchedule(schedule: IdentityScheduleEntity): Long {
        val newId = identityScheduleDao.insert(schedule)
        val next = IdentityScheduleNextFire.computeNext(
            schedule.hour, schedule.minute, schedule.daysOfWeek
        )
        IdentityScheduleAlarmScheduler.scheduleNext(context, newId, next)
        return newId
    }

    suspend fun updateIdentitySchedule(schedule: IdentityScheduleEntity) {
        identityScheduleDao.update(schedule)
        if (schedule.enabled) {
            val next = IdentityScheduleNextFire.computeNext(
                schedule.hour, schedule.minute, schedule.daysOfWeek
            )
            IdentityScheduleAlarmScheduler.scheduleNext(context, schedule.id, next)
        } else {
            IdentityScheduleAlarmScheduler.cancel(context, schedule.id)
        }
    }

    suspend fun deleteIdentitySchedule(scheduleId: Long) {
        IdentityScheduleAlarmScheduler.cancel(context, scheduleId)
        identityScheduleDao.deleteById(scheduleId)
    }

    suspend fun setIdentityScheduleEnabled(scheduleId: Long, enabled: Boolean) {
        identityScheduleDao.setEnabled(scheduleId, enabled)
        if (enabled) {
            val row = identityScheduleDao.getById(scheduleId) ?: return
            val next = IdentityScheduleNextFire.computeNext(
                row.hour, row.minute, row.daysOfWeek
            )
            IdentityScheduleAlarmScheduler.scheduleNext(context, scheduleId, next)
        } else {
            IdentityScheduleAlarmScheduler.cancel(context, scheduleId)
        }
    }

    suspend fun rescheduleAllIdentitySchedulesFromDb() {
        val rows = identityScheduleDao.listEnabled()
        for (r in rows) {
            val next = IdentityScheduleNextFire.computeNext(
                r.hour, r.minute, r.daysOfWeek
            )
            IdentityScheduleAlarmScheduler.scheduleNext(context, r.id, next)
        }
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
            syncReminderToCalendar(rowId, postId, fireAtMillis, repeatRule)
        }
    }

    /**
     * Creates a system-calendar event for [reminderId] when calendar sync is enabled.
     * Returns `true` when an event was created and its ID stored.
     */
    private suspend fun syncReminderToCalendar(
        reminderId: Long,
        postId: Long,
        fireAtMillis: Long,
        repeatRule: String,
    ): Boolean {
        if (!UiPreferences.isCalendarSyncEnabled(context)) return false
        if (!PostReminderCalendarSync.hasPermissions(context)) {
            DiagnosticLog.w(REMINDER_LOG_TAG, "calendar sync on but permission missing; skip reminder=$reminderId")
            return false
        }
        val calendarId = PostReminderCalendarSync.findWritableCalendarId(context) ?: run {
            DiagnosticLog.w(REMINDER_LOG_TAG, "calendar sync on but no writable calendar; skip reminder=$reminderId")
            return false
        }
        val post = postDao.getPostEntityById(postId) ?: return false
        val identityName = post.identityId?.let { identityDao.getIdentityById(it)?.name }
        val preview = post.content.trim().replace('\n', ' ').take(60)
        val title = if (preview.isBlank()) {
            context.getString(R.string.calendar_event_title)
        } else {
            context.getString(R.string.calendar_event_title_with_preview, preview)
        }
        val description = buildString {
            if (!identityName.isNullOrBlank()) append(identityName).append(": ")
            append(post.content)
        }
        val event = PostReminderCalendarSync.createEvent(
            context = context,
            calendarId = calendarId,
            title = title,
            description = description,
            startMillis = fireAtMillis,
            repeatRule = repeatRule,
        ) ?: return false
        postReminderDao.updateCalendarIds(reminderId, event.eventId, event.calendarId)
        return true
    }

    data class CalendarBackfillResult(val synced: Int, val failed: Int)

    /**
     * Writes every pending reminder that has no calendar event yet. Used after the user enables
     * calendar sync, and safe to call repeatedly.
     */
    suspend fun backfillRemindersToCalendar(): CalendarBackfillResult = withContext(Dispatchers.IO) {
        var synced = 0
        var failed = 0
        val rows = postReminderDao.listAll()
        for (row in rows) {
            if (row.calendarEventId != null) continue
            if (syncReminderToCalendar(row.id, row.postId, row.fireAtMillis, row.repeatRule)) {
                synced++
            } else {
                failed++
            }
        }
        DiagnosticLog.d(REMINDER_LOG_TAG, "calendar backfill synced=$synced failed=$failed")
        CalendarBackfillResult(synced, failed)
    }

    /** Deletes every app-created calendar event and clears stored calendar IDs. */
    suspend fun removeAllCalendarEvents() = withContext(Dispatchers.IO) {
        val rows = postReminderDao.listAll()
        for (row in rows) {
            row.calendarEventId?.let { PostReminderCalendarSync.deleteEvent(context, it) }
        }
        postReminderDao.clearAllCalendarIds()
        DiagnosticLog.d(REMINDER_LOG_TAG, "removed app calendar events count=${rows.count { it.calendarEventId != null }}")
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
            r.calendarEventId?.let { PostReminderCalendarSync.deleteEvent(context, it) }
        }
        postReminderDao.deleteByPostId(postId)
    }

    fun observePendingRemindersWithPreview(): Flow<List<PostReminderWithPreview>> =
        postReminderDao.observePendingRemindersWithPreview()

    /** Pending reminders keyed by post, used for feed markers and post detail. */
    fun observePostReminders(): Flow<List<PostReminderEntity>> =
        postReminderDao.observeAll()

    /** Last actual notification per post; drives the reminder-based TODO exception. */
    fun observeLastReminderFirings(): Flow<List<LastReminderFiring>> =
        postReminderFiringDao.observeLastFiringByPost()

    suspend fun setTodoDoneAt(postId: Long, doneAt: Long?) {
        postDao.setTodoDoneAt(postId, doneAt)
    }

    /**
     * Manually merges [fromKey] into [toKey]. Existing chains that pointed at [fromKey]
     * are retargeted, and post exclusions move with the topic.
     */
    suspend fun mergeTopics(fromKey: String, toKey: String) = withContext(Dispatchers.IO) {
        val from = fromKey.lowercase(Locale.ROOT)
        val to = toKey.lowercase(Locale.ROOT)
        if (from.isBlank() || to.isBlank() || from == to) return@withContext
        val existing = topicDao.listAliases().associate { it.fromKey to it.toKey }
        // Refuse cycles such as a → b while b already resolves to a.
        if (resolveTopicKey(to, existing) == from) return@withContext
        database.withTransaction {
            topicDao.retargetAliases(fromKey = from, toKey = to)
            topicDao.copyExclusions(fromKey = from, toKey = to)
            topicDao.clearExclusions(topicKey = from)
            topicDao.upsertAlias(TopicAliasEntity(fromKey = from, toKey = to))
        }
    }

    /** Drops merge aliases that currently resolve to [canonicalKey], splitting them back out. */
    suspend fun unmergeTopicAliases(canonicalKey: String) = withContext(Dispatchers.IO) {
        val resolvedTarget = canonicalKey.lowercase(Locale.ROOT)
        val aliases = topicDao.listAliases()
        val fromKeys = aliases
            .filter { resolveTopicKey(it.fromKey, assocAliases(aliases)) == resolvedTarget }
            .map { it.fromKey }
        if (fromKeys.isNotEmpty()) topicDao.deleteAliases(fromKeys)
    }

    suspend fun excludePostFromTopic(topicKey: String, postId: Long) = withContext(Dispatchers.IO) {
        topicDao.insertExclusion(
            TopicExclusionEntity(
                topicKey = topicKey.lowercase(Locale.ROOT),
                postId = postId
            )
        )
    }

    suspend fun restoreExcludedPosts(topicKey: String) = withContext(Dispatchers.IO) {
        topicDao.clearExclusions(topicKey.lowercase(Locale.ROOT))
    }

    private fun assocAliases(rows: List<TopicAliasEntity>): Map<String, String> =
        rows.associate { it.fromKey to it.toKey }

    suspend fun cancelReminderById(reminderId: Long) {
        withContext(Dispatchers.IO) {
            val row = postReminderDao.getById(reminderId) ?: return@withContext
            PostReminderAlarmScheduler.cancel(context, row.id, row.postId)
            row.calendarEventId?.let { PostReminderCalendarSync.deleteEvent(context, it) }
            postReminderDao.deleteById(reminderId)
        }
    }

    suspend fun togglePostLike(postId: Long) = postDao.toggleLike(postId)
    suspend fun togglePostPin(postId: Long) = postDao.togglePin(postId)

    /** BUG-002: like-state is computed for the currently active identity (exact id match). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getCommentsByPost(postId: Long): Flow<List<CommentWithIdentity>> =
        activeIdentity.flatMapLatest { identity ->
            commentDao.getCommentsByPost(postId, identity?.id ?: -1L)
        }

    suspend fun insertComment(comment: CommentEntity): Long = database.withTransaction {
        val newId = commentDao.insert(comment)
        postDao.incrementCommentCount(comment.postId)
        newId
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
        database.withTransaction {
            commentDao.delete(entity)
            postDao.decrementCommentCount(entity.postId)
        }
        CommentAttachmentStorage.deleteForComment(context, commentId)
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

    /** F-069: hide/unhide a comment; hidden comments fold to the bottom of the detail list until restored. */
    suspend fun setCommentHidden(commentId: Long, hidden: Boolean) {
        commentDao.setCommentHiddenAt(
            commentId,
            if (hidden) System.currentTimeMillis() else 0L
        )
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

    private suspend fun topicAliasesJson(): JSONArray = JSONArray().apply {
        for (row in topicDao.listAliases()) {
            put(JSONObject().apply {
                put("fromKey", row.fromKey)
                put("toKey", row.toKey)
            })
        }
    }

    private suspend fun topicExclusionsJson(): JSONArray = JSONArray().apply {
        for (row in topicDao.listExclusions()) {
            put(JSONObject().apply {
                put("topicKey", row.topicKey)
                put("postId", row.postId)
            })
        }
    }

    private fun reminderFiringsJson(rows: List<PostReminderFiringEntity>): JSONArray = JSONArray().apply {
        rows.forEach { row ->
            put(
                JSONObject().apply {
                    put("id", row.id)
                    put("postId", row.postId)
                    if (row.reminderId != null) put("reminderId", row.reminderId) else put("reminderId", JSONObject.NULL)
                    put("scheduledForMillis", row.scheduledForMillis)
                    put("firedAtMillis", row.firedAtMillis)
                    put("repeatRule", row.repeatRule)
                }
            )
        }
    }

    suspend fun exportAllData(): String {
        val identities = identityDao.getAllIdentities().first()
        val posts = postDao.getAllPosts().first()
        val comments = mutableListOf<CommentWithIdentity>()
        
        posts.forEach { post ->
            comments.addAll(commentDao.getCommentsByPostNoAuth(post.id).first())
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
                put("isPinned", post.isPinned)
                if (post.todoDoneAt != null) put("todoDoneAt", post.todoDoneAt) else put("todoDoneAt", JSONObject.NULL)
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
                put("hiddenAt", comment.hiddenAt)
            }
            commentsArray.put(commentJson)
        }
        json.put("comments", commentsArray)
        json.put("postReminderFirings", reminderFiringsJson(postReminderFiringDao.listAll()))
        // v5: timers were missing from full exports, so every restore lost them.
        json.put("postReminders", postRemindersJson(postReminderDao.listAll()))
        json.put("identitySchedules", identitySchedulesJson())
        json.put("topicAliases", topicAliasesJson())
        json.put("topicExclusions", topicExclusionsJson())

        json.put("exportedAt", System.currentTimeMillis())
        json.put("version", 5)

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
        json.put("version", 4)

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
                put("isPinned", post.isPinned)
                if (post.todoDoneAt != null) put("todoDoneAt", post.todoDoneAt) else put("todoDoneAt", JSONObject.NULL)
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
                put("hiddenAt", comment.hiddenAt)
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
        json.put("identitySchedules", identitySchedulesJson())
        json.put("postReminderFirings", reminderFiringsJson(postReminderFiringDao.listAll()))
        json.put("topicAliases", topicAliasesJson())
        json.put("topicExclusions", topicExclusionsJson())

        json.toString(2)
    }

    /**
     * Writes `data.json` plus files under [PostAttachmentStorage.REL_ROOT],
     * [CommentAttachmentStorage.REL_ROOT], and custom avatars into a ZIP under cache.
     * Re-import via Settings → Import (merge or replace).
     */
    /**
     * FP-P1: ZIP for the share sheet. Uses one fixed file name in [com.pocketweibo.data.media.ShareCache]
     * so repeated exports overwrite instead of piling up full copies of all media in cache.
     */
    suspend fun exportAllDataZip(): File = withContext(Dispatchers.IO) {
        val dir = com.pocketweibo.data.media.ShareCache.dir(context)
        // Remove timestamped copies written by older versions.
        dir.listFiles()?.filter { it.name.startsWith("pocket_weibo_backup_") && it.name.endsWith(".zip") }
            ?.forEach { it.delete() }
        val outFile = com.pocketweibo.data.media.ShareCache.file(context, "pocket_weibo_backup.zip")
        FileOutputStream(outFile).use { writeAllDataZip(it) }
        outFile
    }

    /** FP-P1: streams the ZIP straight to a user-chosen document (SAF); no cache copy. */
    suspend fun exportAllDataZipTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "w") ?: return@withContext false
        out.use { writeAllDataZip(it) }
        true
    }

    private suspend fun writeAllDataZip(target: java.io.OutputStream) {
        val json = exportAllData()
        ZipOutputStream(target.buffered()).use { zos ->
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

    /**
     * REVIEW-3 §1.1/§1.2/§6.1: both modes extract into a private staging dir first (Zip Slip guard,
     * streamed with size caps), validate `data.json`, then import. Replace mode only swaps the live
     * media trees after the database transaction has committed, so a failed import leaves the
     * existing data and media untouched.
     */
    private suspend fun importZipBackup(uri: Uri, override: Boolean): Boolean = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, "pw_import_staging_${System.currentTimeMillis()}")
        try {
            val rejected = context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipSafety.extractTo(raw, staging)
            } ?: return@withContext false
            if (rejected.isNotEmpty()) {
                DiagnosticLog.w("PW_Import", "skipped ${rejected.size} unsafe ZIP entries")
            }
            val jsonFile = File(staging, "data.json")
            if (!jsonFile.isFile) return@withContext false
            val jsonText = jsonFile.readText()
            JSONObject(jsonText.trim().trimStart('\uFEFF')) // validate before touching anything
            if (override) {
                importData(jsonText, override = true, mergeAttachmentStaging = null, replaceMediaFrom = staging)
            } else {
                importData(jsonText, override = false, mergeAttachmentStaging = staging)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            staging.deleteRecursively()
        }
    }

    private val mediaRoots = listOf(
        PostAttachmentStorage.REL_ROOT,
        CommentAttachmentStorage.REL_ROOT,
        IdentityAvatarStorage.REL_ROOT
    )

    /** Replaces live media trees with the ones in [staging]; rolls back on failure. */
    private fun swapMediaTrees(staging: File?) {
        val moved = mutableListOf<String>()
        try {
            for (root in mediaRoots) {
                val live = File(context.filesDir, root)
                val old = File(context.filesDir, "$root.old")
                old.deleteRecursively()
                if (live.exists() && !live.renameTo(old)) throw java.io.IOException("cannot move $root")
                moved += root
                val src = staging?.let { File(it, root) }
                if (src != null && src.exists() && !src.renameTo(live)) {
                    src.copyRecursively(live, overwrite = true)
                }
            }
            for (root in mediaRoots) File(context.filesDir, "$root.old").deleteRecursively()
        } catch (e: Exception) {
            for (root in moved) {
                val live = File(context.filesDir, root)
                val old = File(context.filesDir, "$root.old")
                if (old.exists()) {
                    live.deleteRecursively()
                    old.renameTo(live)
                }
            }
            throw e
        }
    }

    suspend fun importData(
        jsonString: String,
        override: Boolean,
        mergeAttachmentStaging: File?,
        skipClearOnOverride: Boolean = false,
        /** Replace mode only: staging dir whose media trees replace the live ones after commit. */
        replaceMediaFrom: File? = null
    ): Boolean {
        var sideEffects: ClearSideEffects? = null
        return try {
            // Roadmap P0 / REVIEW-3 §1.2: all DB writes are atomic; file and alarm side effects
            // of the clear run only after the transaction has committed.
            database.withTransaction {
            val json = JSONObject(jsonString.trim().trimStart('\uFEFF'))

            if (override) {
                if (!skipClearOnOverride) sideEffects = clearAllRows()
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
                if (json.has("postReminderFirings")) {
                    val firingsArray = json.getJSONArray("postReminderFirings")
                    for (i in 0 until firingsArray.length()) {
                        val firingJson = firingsArray.getJSONObject(i)
                        postReminderFiringDao.insert(
                            firingFromJson(
                                firingJson,
                                id = firingJson.optLong("id", 0L),
                                postId = firingJson.getLong("postId")
                            )
                        )
                    }
                }
                // Timers: ids are preserved in replace mode, so backup ids map to themselves.
                val importedPostIds = postDao.listAllIds().toHashSet()
                val importedIdentityIds = identityDao.listAllIds().toHashSet()
                json.optJSONArray("postReminders")?.let { arr ->
                    insertRemindersFromJson(arr) { id -> id.takeIf { it in importedPostIds } }
                }
                json.optJSONArray("identitySchedules")?.let { arr ->
                    insertSchedulesFromJson(arr) { id -> id.takeIf { it in importedIdentityIds } }
                }
                if (json.has("topicAliases")) {
                    val aliasesArray = json.getJSONArray("topicAliases")
                    for (i in 0 until aliasesArray.length()) {
                        val aliasJson = aliasesArray.getJSONObject(i)
                        topicDao.upsertAlias(
                            TopicAliasEntity(
                                fromKey = aliasJson.getString("fromKey"),
                                toKey = aliasJson.getString("toKey")
                            )
                        )
                    }
                }
                if (json.has("topicExclusions")) {
                    val exclusionsArray = json.getJSONArray("topicExclusions")
                    for (i in 0 until exclusionsArray.length()) {
                        val exclusionJson = exclusionsArray.getJSONObject(i)
                        topicDao.insertExclusion(
                            TopicExclusionEntity(
                                topicKey = exclusionJson.getString("topicKey"),
                                postId = exclusionJson.getLong("postId")
                            )
                        )
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
                if (json.has("postReminderFirings")) {
                    val firingsArray = json.getJSONArray("postReminderFirings")
                    for (i in 0 until firingsArray.length()) {
                        val firingJson = firingsArray.getJSONObject(i)
                        val newPostId = postOldToNew[firingJson.getLong("postId")] ?: continue
                        postReminderFiringDao.insert(
                            firingFromJson(firingJson, id = 0L, postId = newPostId)
                        )
                    }
                }
                json.optJSONArray("postReminders")?.let { arr ->
                    insertRemindersFromJson(arr) { id -> postOldToNew[id] }
                }
                json.optJSONArray("identitySchedules")?.let { arr ->
                    insertSchedulesFromJson(arr) { id -> identityOldToNew[id] }
                }
                if (json.has("topicAliases")) {
                    val aliasesArray = json.getJSONArray("topicAliases")
                    for (i in 0 until aliasesArray.length()) {
                        val aliasJson = aliasesArray.getJSONObject(i)
                        topicDao.upsertAlias(
                            TopicAliasEntity(
                                fromKey = aliasJson.getString("fromKey").lowercase(Locale.ROOT),
                                toKey = aliasJson.getString("toKey").lowercase(Locale.ROOT)
                            )
                        )
                    }
                }
                if (json.has("topicExclusions")) {
                    val exclusionsArray = json.getJSONArray("topicExclusions")
                    for (i in 0 until exclusionsArray.length()) {
                        val exclusionJson = exclusionsArray.getJSONObject(i)
                        val newPostId = postOldToNew[exclusionJson.getLong("postId")] ?: continue
                        topicDao.insertExclusion(
                            TopicExclusionEntity(
                                topicKey = exclusionJson.getString("topicKey").lowercase(Locale.ROOT),
                                postId = newPostId
                            )
                        )
                    }
                }
            }

            }
            if (override) {
                sideEffects?.let { applyClearSideEffects(it) }
                swapMediaTrees(replaceMediaFrom)
            }
            // Re-arm alarms for restored timers in both modes (schedule() replaces existing ones).
            rescheduleAllPostRemindersFromDb()
            rescheduleAllIdentitySchedulesFromDb()

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
            isLiked = postJson.optBoolean("isLiked", false),
            isPinned = postJson.optBoolean("isPinned", false),
            todoDoneAt = when {
                !postJson.has("todoDoneAt") || postJson.isNull("todoDoneAt") -> null
                else -> postJson.getLong("todoDoneAt")
            }
        )
    }

    // ---- Timers in backups: post reminders + identity schedules ----

    private fun postRemindersJson(rows: List<PostReminderEntity>): JSONArray = JSONArray().apply {
        rows.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("postId", r.postId)
                put("fireAtMillis", r.fireAtMillis)
                put("repeatRule", r.repeatRule)
            })
        }
    }

    private suspend fun identitySchedulesJson(): JSONArray = JSONArray().apply {
        identityScheduleDao.listAll().forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("identityId", r.identityId)
                put("hour", r.hour)
                put("minute", r.minute)
                put("daysOfWeek", r.daysOfWeek)
                put("enabled", r.enabled)
            })
        }
    }

    /**
     * One-shot reminders whose time has passed while they were only in a backup are dropped;
     * repeating ones are rolled forward to the next occurrence after [now]. Returns null to skip.
     */
    private fun restoredFireAt(fireAt: Long, rule: String, now: Long): Long? {
        if (fireAt > now) return fireAt
        if (!ReminderRepeatRule.isRepeating(rule)) return null
        var next: Long = fireAt
        var guard = 0
        while (next <= now && guard++ < 5000) {
            next = ReminderRepeatRule.computeNextFireAfter(next, rule) ?: return null
        }
        return next.takeIf { it > now }
    }

    /** Inserts reminders from [arr], mapping backup post ids through [postIdMap]. Returns count. */
    private suspend fun insertRemindersFromJson(arr: JSONArray, postIdMap: (Long) -> Long?): Int {
        val now = System.currentTimeMillis()
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val postId = postIdMap(o.optLong("postId", -1L)) ?: continue
            val rule = o.optString("repeatRule", ReminderRepeatRule.NONE)
            val fireAt = restoredFireAt(o.optLong("fireAtMillis", 0L), rule, now) ?: continue
            if (postReminderDao.listForPost(postId).isNotEmpty()) continue // one reminder per post
            postReminderDao.insert(PostReminderEntity(postId = postId, fireAtMillis = fireAt, repeatRule = rule))
            n++
        }
        return n
    }

    /** Inserts identity schedules from [arr], mapping identity ids through [identityIdMap]. Returns count. */
    private suspend fun insertSchedulesFromJson(arr: JSONArray, identityIdMap: (Long) -> Long?): Int {
        val existing = identityScheduleDao.listAll()
            .map { Triple(it.identityId, it.hour * 60 + it.minute, it.daysOfWeek) }.toMutableSet()
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val identityId = identityIdMap(o.optLong("identityId", -1L)) ?: continue
            val hour = o.optInt("hour", -1)
            val minute = o.optInt("minute", -1)
            if (hour !in 0..23 || minute !in 0..59) continue
            val days = o.optString("daysOfWeek", "")
            if (!existing.add(Triple(identityId, hour * 60 + minute, days))) continue
            identityScheduleDao.insert(
                IdentityScheduleEntity(
                    identityId = identityId, hour = hour, minute = minute,
                    daysOfWeek = days, enabled = o.optBoolean("enabled", true)
                )
            )
            n++
        }
        return n
    }

    data class TimerRestoreResult(val reminders: Int, val schedules: Int)

    /**
     * Restores only timers (post reminders + identity schedules) from a JSON, ZIP or encrypted
     * auto-backup (`.pwb`) file into the current data, without touching posts or identities.
     * Backup rows are matched to current rows by content: posts by (createdAt, content),
     * identities by name. Existing timers are kept; duplicates are skipped.
     */
    suspend fun restoreTimersFromBackup(uri: Uri): TimerRestoreResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("cannot read backup")
        val text = when {
            bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'W'.code.toByte() &&
                bytes[2] == 'B'.code.toByte() -> String(com.pocketweibo.data.backup.AutoBackupCrypto.decrypt(bytes, context), Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() -> {
                var found: String? = null
                java.util.zip.ZipInputStream(bytes.inputStream()).use { zis ->
                    var e = zis.nextEntry
                    while (e != null && found == null) {
                        if (ZipSafety.normalizeName(e.name) == "data.json") {
                            found = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        e = zis.nextEntry
                    }
                }
                found ?: error("no data.json in ZIP")
            }
            else -> bytes.toString(Charsets.UTF_8)
        }
        val json = JSONObject(text.trim().trimStart('\uFEFF'))

        val currentPosts = postDao.getAllPosts().first()
            .associateBy({ it.createdAt to it.content }, { it.id })
        val backupPostIds = mutableMapOf<Long, Long>()
        json.optJSONArray("posts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                currentPosts[p.optLong("createdAt") to p.optString("content", "")]
                    ?.let { backupPostIds[p.optLong("id")] = it }
            }
        }
        val currentIdentities = identityDao.getAllIdentities().first()
            .groupBy { it.name }.mapValues { (_, v) -> v.first().id }
        val backupIdentityIds = mutableMapOf<Long, Long>()
        json.optJSONArray("identities")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                currentIdentities[o.optString("name")]?.let { backupIdentityIds[o.optLong("id")] = it }
            }
        }

        val result = database.withTransaction {
            TimerRestoreResult(
                reminders = json.optJSONArray("postReminders")
                    ?.let { insertRemindersFromJson(it) { id -> backupPostIds[id] } } ?: 0,
                schedules = json.optJSONArray("identitySchedules")
                    ?.let { insertSchedulesFromJson(it) { id -> backupIdentityIds[id] } } ?: 0
            )
        }
        rescheduleAllPostRemindersFromDb()
        rescheduleAllIdentitySchedulesFromDb()
        for (row in postReminderDao.listAll()) {
            if (row.calendarEventId == null) syncReminderToCalendar(row.id, row.postId, row.fireAtMillis, row.repeatRule)
        }
        result
    }

    private fun firingFromJson(json: JSONObject, id: Long, postId: Long): PostReminderFiringEntity {
        val scheduledFor = when {
            json.has("scheduledForMillis") && !json.isNull("scheduledForMillis") ->
                json.getLong("scheduledForMillis")
            else -> json.getLong("firedAtMillis")
        }
        return PostReminderFiringEntity(
            id = id,
            postId = postId,
            reminderId = when {
                !json.has("reminderId") || json.isNull("reminderId") -> null
                else -> json.getLong("reminderId")
            },
            scheduledForMillis = scheduledFor,
            firedAtMillis = json.getLong("firedAtMillis"),
            repeatRule = json.optString("repeatRule", ReminderRepeatRule.NONE)
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
            likedBy = commentJson.optString("likedBy", ""),
            hiddenAt = commentJson.optLong("hiddenAt", 0L)
        )
    }

    /** Alarms and calendar events that belonged to rows removed by [clearAllRows]. */
    private class ClearSideEffects(
        val reminders: List<PostReminderEntity>,
        val scheduleIds: List<Long>
    )

    /** Deletes all rows (no files, alarms or calendar events); safe inside a transaction. */
    private suspend fun clearAllRows(): ClearSideEffects {
        val effects = ClearSideEffects(
            reminders = postReminderDao.listAll(),
            scheduleIds = identityScheduleDao.listEnabled().map { it.id }
        )
        commentDao.deleteAll()
        postReminderFiringDao.deleteAll()
        topicDao.clearAllAliases()
        topicDao.clearAllExclusions()
        postDao.deleteAll()
        identityDao.deleteAll()
        return effects
    }

    /** BUG-003: cancel alarms and calendar mirrors of cleared rows so nothing fires afterwards. */
    private fun applyClearSideEffects(effects: ClearSideEffects) {
        for (row in effects.reminders) {
            PostReminderAlarmScheduler.cancel(context, row.id, row.postId)
            row.calendarEventId?.let { PostReminderCalendarSync.deleteEvent(context, it) }
        }
        for (id in effects.scheduleIds) IdentityScheduleAlarmScheduler.cancel(context, id)
    }

    /**
     * REVIEW-3 §2.4: removes media folders whose post / comment / identity row no longer exists
     * (left behind by crashes or failed imports). Only numeric-id entries are touched.
     */
    suspend fun sweepOrphanMedia(): Int = withContext(Dispatchers.IO) {
        val postIds = postDao.listAllIds().toHashSet()
        val commentIds = commentDao.listAllIds().toHashSet()
        val identityIds = identityDao.listAllIds().toHashSet()
        var removed = 0
        PostAttachmentStorage.rootDir(context).listFiles()?.forEach { f ->
            val id = f.name.toLongOrNull() ?: return@forEach
            if (id !in postIds && f.deleteRecursively()) removed++
        }
        CommentAttachmentStorage.rootDir(context).listFiles()?.forEach { f ->
            val id = f.name.toLongOrNull() ?: return@forEach
            if (id !in commentIds && f.deleteRecursively()) removed++
        }
        java.io.File(context.filesDir, IdentityAvatarStorage.REL_ROOT).listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (id !in identityIds && f.delete()) removed++
        }
        removed
    }

    suspend fun clearAllData() {
        val effects = database.withTransaction { clearAllRows() }
        applyClearSideEffects(effects)
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
