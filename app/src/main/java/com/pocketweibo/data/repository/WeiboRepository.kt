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
private val Context.messageDataStore by preferencesDataStore(name = "messages")

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
        val id = commentDao.insert(comment)
        postDao.incrementCommentCount(comment.postId)

        val post = postDao.getAllPosts().first().find { it.id == comment.postId }
        if (post != null && post.identityId != comment.identityId) {
            val currentUnread = getUnreadReceivedCount()
            setUnreadReceivedCount(currentUnread + 1)
        }

        return id
    }

    suspend fun deleteComment(comment: CommentEntity) {
        commentDao.delete(comment)
        postDao.decrementCommentCount(comment.postId)
    }

    suspend fun updateComment(comment: CommentEntity) {
        commentDao.update(comment)
    }

    fun getPostsByIdentity(identityId: Long): Flow<List<PostWithIdentity>> =
        postDao.getPostsByIdentity(identityId)

    companion object {
        private val DRAFT_CONTENT = stringPreferencesKey("draft_content")
        private val DRAFT_IDENTITY_ID = longPreferencesKey("draft_identity_id")
        private val UNREAD_RECEIVED_COUNT = longPreferencesKey("unread_received_count")
        private val UNREAD_SENT_COUNT = longPreferencesKey("unread_sent_count")
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

    suspend fun getUnreadReceivedCount(): Long {
        val prefs = context.messageDataStore.data.first()
        return prefs[UNREAD_RECEIVED_COUNT] ?: 0L
    }

    suspend fun setUnreadReceivedCount(count: Long) {
        context.messageDataStore.edit { prefs ->
            prefs[UNREAD_RECEIVED_COUNT] = count
        }
    }

    suspend fun getUnreadSentCount(): Long {
        val prefs = context.messageDataStore.data.first()
        return prefs[UNREAD_SENT_COUNT] ?: 0L
    }

    suspend fun setUnreadSentCount(count: Long) {
        context.messageDataStore.edit { prefs ->
            prefs[UNREAD_SENT_COUNT] = count
        }
    }

    suspend fun clearUnreadCounts() {
        context.messageDataStore.edit { it.clear() }
    }

    suspend fun exportSelectiveData(types: Set<String>): String {
        val json = JSONObject()

        if ("identities" in types) {
            val identities = identityDao.getAllIdentities().first()
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
        }

        if ("posts" in types) {
            val posts = postDao.getAllPosts().first()
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
        }

        if ("comments" in types) {
            val posts = postDao.getAllPosts().first()
            val comments = mutableListOf<CommentWithIdentity>()
            posts.forEach { post ->
                comments.addAll(commentDao.getCommentsByPost(post.id).first())
            }

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
        }

        json.put("exportedAt", System.currentTimeMillis())
        json.put("version", 1)

        return json.toString(2)
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

    suspend fun exportDataToCsv(): String {
        val identities = identityDao.getAllIdentities().first()
        val posts = postDao.getAllPosts().first()
        
        val sb = StringBuilder()
        
        sb.appendLine("=== 身份 (Identities) ===")
        sb.appendLine("ID,姓名,头像,国籍,性别,出生年份,去世年份,职业,座右铭,代表作,简介,是否激活")
        identities.forEach { identity ->
            sb.appendLine("${identity.id},\"${identity.name}\",${identity.avatarResName},\"${identity.nationality}\",${identity.gender},${identity.birthYear},${identity.deathYear},\"${identity.occupation}\",\"${identity.motto}\",\"${identity.famousWork}\",\"${identity.bio}\",${identity.isActive}")
        }
        
        sb.appendLine()
        sb.appendLine("=== 微博 (Posts) ===")
        sb.appendLine("ID,身份ID,内容,图片,创建时间,点赞数,评论数,是否点赞")
        posts.forEach { post ->
            sb.appendLine("${post.id},${post.identityId},\"${post.content.replace("\"", "\"\"")}\",\"${post.imageUris}\",${post.createdAt},${post.likeCount},${post.commentCount},${post.isLiked}")
        }
        
        return sb.toString()
    }

    suspend fun importData(jsonString: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            
            if (json.has("identities")) {
                val identitiesArray = json.getJSONArray("identities")
                for (i in 0 until identitiesArray.length()) {
                    val identityJson = identitiesArray.getJSONObject(i)
                    val identity = IdentityEntity(
                        id = identityJson.getLong("id"),
                        name = identityJson.getString("name"),
                        avatarResName = identityJson.optString("avatarResName", "avatar_default"),
                        nationality = identityJson.optString("nationality", ""),
                        gender = when (identityJson.optString("gender", "")) {
                            "MALE" -> Gender.MALE
                            "FEMALE" -> Gender.FEMALE
                            else -> Gender.OTHER
                        },
                        birthYear = identityJson.optInt("birthYear", 0),
                        deathYear = identityJson.optInt("deathYear", 0),
                        occupation = identityJson.optString("occupation", ""),
                        motto = identityJson.optString("motto", ""),
                        famousWork = identityJson.optString("famousWork", ""),
                        bio = identityJson.optString("bio", ""),
                        isActive = identityJson.optBoolean("isActive", false)
                    )
                    identityDao.insert(identity)
                }
            }

            if (json.has("posts")) {
                val postsArray = json.getJSONArray("posts")
                for (i in 0 until postsArray.length()) {
                    val postJson = postsArray.getJSONObject(i)
                    val post = PostEntity(
                        id = postJson.getLong("id"),
                        identityId = postJson.getLong("identityId"),
                        content = postJson.getString("content"),
                        imageUris = postJson.optString("imageUris", ""),
                        createdAt = postJson.getLong("createdAt"),
                        likeCount = postJson.optInt("likeCount", 0),
                        commentCount = postJson.optInt("commentCount", 0),
                        isLiked = postJson.optBoolean("isLiked", false)
                    )
                    postDao.insert(post)
                }
            }

            if (json.has("comments")) {
                val commentsArray = json.getJSONArray("comments")
                for (i in 0 until commentsArray.length()) {
                    val commentJson = commentsArray.getJSONObject(i)
                    val comment = CommentEntity(
                        id = commentJson.getLong("id"),
                        postId = commentJson.getLong("postId"),
                        identityId = commentJson.getLong("identityId"),
                        content = commentJson.getString("content"),
                        createdAt = commentJson.getLong("createdAt")
                    )
                    commentDao.insert(comment)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importData(jsonString: String, override: Boolean): Boolean {
        return try {
            if (override) {
                clearAllData()
            }
            
            val json = JSONObject(jsonString)
            
            if (json.has("identities")) {
                val identitiesArray = json.getJSONArray("identities")
                for (i in 0 until identitiesArray.length()) {
                    val identityJson = identitiesArray.getJSONObject(i)
                    val identity = IdentityEntity(
                        id = if (override) identityJson.getLong("id") else 0L,
                        name = identityJson.getString("name"),
                        avatarResName = identityJson.optString("avatarResName", "avatar_default"),
                        nationality = identityJson.optString("nationality", ""),
                        gender = when (identityJson.optString("gender", "")) {
                            "MALE" -> Gender.MALE
                            "FEMALE" -> Gender.FEMALE
                            else -> Gender.OTHER
                        },
                        birthYear = identityJson.optInt("birthYear", 0),
                        deathYear = identityJson.optInt("deathYear", 0),
                        occupation = identityJson.optString("occupation", ""),
                        motto = identityJson.optString("motto", ""),
                        famousWork = identityJson.optString("famousWork", ""),
                        bio = identityJson.optString("bio", ""),
                        isActive = identityJson.optBoolean("isActive", false)
                    )
                    identityDao.insert(identity)
                }
            }

            if (json.has("posts")) {
                val postsArray = json.getJSONArray("posts")
                for (i in 0 until postsArray.length()) {
                    val postJson = postsArray.getJSONObject(i)
                    val post = PostEntity(
                        id = if (override) postJson.getLong("id") else 0L,
                        identityId = postJson.getLong("identityId"),
                        content = postJson.getString("content"),
                        imageUris = postJson.optString("imageUris", ""),
                        createdAt = postJson.getLong("createdAt"),
                        likeCount = postJson.optInt("likeCount", 0),
                        commentCount = postJson.optInt("commentCount", 0),
                        isLiked = postJson.optBoolean("isLiked", false)
                    )
                    postDao.insert(post)
                }
            }

            if (json.has("comments")) {
                val commentsArray = json.getJSONArray("comments")
                for (i in 0 until commentsArray.length()) {
                    val commentJson = commentsArray.getJSONObject(i)
                    val comment = CommentEntity(
                        id = if (override) commentJson.getLong("id") else 0L,
                        postId = commentJson.getLong("postId"),
                        identityId = commentJson.getLong("identityId"),
                        content = commentJson.getString("content"),
                        createdAt = commentJson.getLong("createdAt")
                    )
                    commentDao.insert(comment)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAllData() {
        commentDao.deleteAll()
        postDao.deleteAll()
        identityDao.deleteAll()
    }
}
