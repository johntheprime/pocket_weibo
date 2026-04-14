package com.pocketweibo.data.local.dao

import androidx.room.*
import com.pocketweibo.data.local.entity.CommentEntity
import kotlinx.coroutines.flow.Flow

data class CommentWithIdentity(
    val id: Long,
    val postId: Long,
    val identityId: Long,
    val identityName: String,
    val identityAvatarResName: String,
    val content: String,
    val createdAt: Long,
    val replyingToCommentId: Long?,
    val replyingToIdentityName: String?,
    val likeCount: Int,
    val isLikedByMe: Boolean
)

@Dao
interface CommentDao {
    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarResName as identityAvatarResName, c.content, c.createdAt,
               c.replyingToCommentId, 
               (SELECT i2.name FROM identities i2 INNER JOIN comments c2 ON c2.identityId = i2.id WHERE c2.id = c.replyingToCommentId) as replyingToIdentityName,
               c.likeCount,
               CASE WHEN c.likedBy LIKE '%' || :currentIdentityId || '%' THEN 1 ELSE 0 END as isLikedByMe
        FROM comments c
        INNER JOIN identities i ON c.identityId = i.id
        WHERE c.postId = :postId
        ORDER BY c.createdAt ASC
    """)
    fun getCommentsByPost(postId: Long, currentIdentityId: Long = 0): Flow<List<CommentWithIdentity>>

    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarResName as identityAvatarResName, c.content, c.createdAt,
               c.replyingToCommentId, 
               (SELECT i2.name FROM identities i2 INNER JOIN comments c2 ON c2.identityId = i2.id WHERE c2.id = c.replyingToCommentId) as replyingToIdentityName,
               c.likeCount, 0 as isLikedByMe
        FROM comments c
        INNER JOIN identities i ON c.identityId = i.id
        WHERE c.postId = :postId
        ORDER BY c.createdAt ASC
    """)
    fun getCommentsByPostNoAuth(postId: Long): Flow<List<CommentWithIdentity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity): Long

    @Delete
    suspend fun delete(comment: CommentEntity)

    @Query("DELETE FROM comments")
    suspend fun deleteAll()

    @Query("UPDATE comments SET likeCount = likeCount + 1, likedBy = likedBy || :identityId || ',' WHERE id = :commentId")
    suspend fun likeCommentById(commentId: Long, identityId: String)

    @Query("UPDATE comments SET likeCount = likeCount - 1, likedBy = REPLACE(likedBy, :identityId || ',', '') WHERE id = :commentId AND likeCount > 0")
    suspend fun unlikeCommentById(commentId: Long, identityId: String)

    @Query("UPDATE comments SET content = :newContent WHERE id = :commentId")
    suspend fun updateCommentContent(commentId: Long, newContent: String)
}
