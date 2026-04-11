package com.myweibo.data.local.dao

import androidx.room.*
import com.myweibo.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

data class PostWithIdentity(
    val id: Long,
    val identityId: Long,
    val identityName: String,
    val identityAvatarColor: Int,
    val content: String,
    val imageUris: String,
    val createdAt: Long,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean
)

@Dao
interface PostDao {
    @Query("""
        SELECT p.id, p.identityId, i.name as identityName, i.avatarColor as identityAvatarColor,
               p.content, p.imageUris, p.createdAt, p.likeCount, p.commentCount, p.isLiked
        FROM posts p
        INNER JOIN identities i ON p.identityId = i.id
        ORDER BY p.createdAt DESC
    """)
    fun getAllPosts(): Flow<List<PostWithIdentity>>

    @Query("""
        SELECT p.id, p.identityId, i.name as identityName, i.avatarColor as identityAvatarColor,
               p.content, p.imageUris, p.createdAt, p.likeCount, p.commentCount, p.isLiked
        FROM posts p
        INNER JOIN identities i ON p.identityId = i.id
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
}
