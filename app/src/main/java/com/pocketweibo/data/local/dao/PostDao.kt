package com.pocketweibo.data.local.dao

import androidx.room.*
import com.pocketweibo.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

data class PostWithIdentity(
    val id: Long,
    val identityId: Long?,
    val identityName: String?,
    val identityAvatarResName: String?,
    val identityCustomAvatarUri: String?,
    val content: String,
    val imageUris: String,
    val audioPath: String,
    val extrasJson: String,
    val createdAt: Long,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean
)

@Dao
interface PostDao {
    @Query("""
        SELECT p.id, p.identityId, i.name as identityName, i.avatarResName as identityAvatarResName,
               i.customAvatarUri as identityCustomAvatarUri,
               p.content, p.imageUris, p.audioPath, p.extrasJson, p.createdAt, p.likeCount, p.commentCount, p.isLiked
        FROM posts p
        LEFT JOIN identities i ON p.identityId = i.id
        ORDER BY p.createdAt DESC
    """)
    fun getAllPosts(): Flow<List<PostWithIdentity>>

    @Query("""
        SELECT p.id, p.identityId, i.name as identityName, i.avatarResName as identityAvatarResName,
               i.customAvatarUri as identityCustomAvatarUri,
               p.content, p.imageUris, p.audioPath, p.extrasJson, p.createdAt, p.likeCount, p.commentCount, p.isLiked
        FROM posts p
        LEFT JOIN identities i ON p.identityId = i.id
        WHERE p.identityId = :identityId
        ORDER BY p.createdAt DESC
    """)
    fun getPostsByIdentity(identityId: Long): Flow<List<PostWithIdentity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity): Long

    @Update
    suspend fun update(post: PostEntity)

    @Delete
    suspend fun delete(post: PostEntity)

    @Query("UPDATE posts SET isLiked = NOT isLiked, likeCount = likeCount + CASE WHEN isLiked THEN -1 ELSE 1 END WHERE id = :postId")
    suspend fun toggleLike(postId: Long)

    @Query("UPDATE posts SET commentCount = commentCount + 1 WHERE id = :postId")
    suspend fun incrementCommentCount(postId: Long)

    @Query("UPDATE posts SET commentCount = commentCount - 1 WHERE id = :postId AND commentCount > 0")
    suspend fun decrementCommentCount(postId: Long)

    @Query("DELETE FROM posts")
    suspend fun deleteAll()

    @Query("SELECT * FROM posts WHERE id = :id LIMIT 1")
    suspend fun getPostEntityById(id: Long): PostEntity?
}
