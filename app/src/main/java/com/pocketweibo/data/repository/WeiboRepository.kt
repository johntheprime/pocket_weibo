package com.pocketweibo.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketweibo.data.local.dao.CommentDao
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.local.dao.IdentityDao
import com.pocketweibo.data.local.dao.PostDao
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.draftDataStore by preferencesDataStore(name = "draft")

class WeiboRepository(
    private val identityDao: IdentityDao,
    private val postDao: PostDao,
    private val commentDao: CommentDao,
    private val context: Context
) {
    val allIdentities: Flow<List<IdentityEntity>> = identityDao.getAllIdentities()
    val activeIdentity: Flow<IdentityEntity?> = identityDao.getActiveIdentity()
    val allPosts: Flow<List<PostWithIdentity>> = postDao.getAllPosts()

    suspend fun getIdentityById(id: Long): IdentityEntity? = identityDao.getIdentityById(id)

    suspend fun insertIdentity(identity: IdentityEntity): Long = identityDao.insert(identity)

    suspend fun updateIdentity(identity: IdentityEntity) = identityDao.update(identity)

    suspend fun deleteIdentity(identity: IdentityEntity) = identityDao.delete(identity)

    suspend fun setActiveIdentity(id: Long) {
        identityDao.deactivateAll()
        identityDao.activate(id)
    }

    suspend fun insertPost(post: PostEntity): Long = postDao.insert(post)

    suspend fun deletePost(post: PostEntity) = postDao.delete(post)

    suspend fun togglePostLike(postId: Long) = postDao.toggleLike(postId)

    fun getCommentsByPost(postId: Long): Flow<List<CommentWithIdentity>> =
        commentDao.getCommentsByPost(postId)

    suspend fun insertComment(comment: CommentEntity): Long {
        commentDao.insert(comment)
        postDao.incrementCommentCount(comment.postId)
        return comment.id
    }

    suspend fun deleteComment(comment: CommentEntity) {
        commentDao.delete(comment)
        postDao.decrementCommentCount(comment.postId)
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
}
