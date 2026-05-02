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
import com.pocketweibo.data.local.entity.Gender
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    suspend fun clearCustomAvatar(identityId: Long) = identityDao.clearCustomAvatar(identityId)

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
        json.put("version", 1)

        return json.toString(2)
    }

    suspend fun exportAllDataToMarkdown(): String {
        val identities = identityDao.getAllIdentities().first()
        val posts = postDao.getAllPosts().first()
        
        val sb = StringBuilder()
        sb.appendLine("# PocketWeibo 备份")
        sb.appendLine()
        sb.appendLine("## 身份列表")
        sb.appendLine()
        
        identities.forEach { identity ->
            sb.appendLine("### ${identity.name}")
            sb.appendLine("- 国籍: ${identity.nationality}")
            sb.appendLine("- 性别: ${identity.gender}")
            sb.appendLine("- 职业: ${identity.occupation}")
            sb.appendLine("- 座右铭: ${identity.motto}")
            sb.appendLine("- 代表作: ${identity.famousWork}")
            sb.appendLine("- 简介: ${identity.bio}")
            sb.appendLine()
        }
        
        sb.appendLine("## 微博列表")
        sb.appendLine()
        
        posts.forEach { post ->
            val identity = identities.find { it.id == post.identityId }
            sb.appendLine("### ${identity?.name ?: "未知"} 的微博")
            sb.appendLine()
            sb.appendLine(post.content)
            sb.appendLine()
            sb.appendLine("- 点赞: ${post.likeCount}")
            sb.appendLine("- 评论: ${post.commentCount}")
            sb.appendLine("- 发布时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(post.createdAt))}")
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }
        
        return sb.toString()
    }

    suspend fun importData(jsonString: String): Boolean =
        importData(jsonString, override = false)

    suspend fun importData(jsonString: String, override: Boolean): Boolean {
        return try {
            val json = JSONObject(jsonString.trim().trimStart('\uFEFF'))

            if (override) {
                clearAllData()
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
                        val newPostId = postDao.insert(postFromJson(postJson, 0L, newIdentityId))
                        postOldToNew[oldPostId] = newPostId
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

    private fun postFromJson(postJson: JSONObject, id: Long, identityId: Long? = null): PostEntity {
        val resolvedIdentityId = identityId ?: postJson.getLong("identityId")
        return PostEntity(
            id = id,
            identityId = resolvedIdentityId,
            content = postJson.getString("content"),
            imageUris = postJson.optString("imageUris", ""),
            createdAt = postJson.getLong("createdAt"),
            likeCount = postJson.optInt("likeCount", 0),
            commentCount = postJson.optInt("commentCount", 0),
            isLiked = postJson.optBoolean("isLiked", false)
        )
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
    }
}
