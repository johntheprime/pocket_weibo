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
    val parentCommentId: Long? = null,
    val replyToIdentityName: String? = null,
    val likeCount: Int = 0,
    val isLikedByMe: Boolean = false
)

@Dao
interface CommentDao {
    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarResName as identityAvatarResName, c.content, c.createdAt,
               c.parentCommentId, 
               (SELECT i2.name FROM comments p 
                INNER JOIN identities i2 ON p.identityId = i2.id 
                WHERE p.id = c.parentCommentId) as replyToIdentityName,
               c.likeCount, 
               CASE WHEN :currentIdentityId = 0 THEN 0 WHEN c.likedByIdentityIds LIKE '%' || :currentIdentityId || '%' THEN 1 ELSE 0 END as isLikedByMe
        FROM comments c
        INNER JOIN identities i ON c.identityId = i.id
        WHERE c.postId = :postId
        ORDER BY c.createdAt ASC
    """)
    fun getCommentsByPost(postId: Long, currentIdentityId: Long): Flow<List<CommentWithIdentity>>

    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarResName as identityAvatarResName, c.content, c.createdAt,
               c.parentCommentId, 
               (SELECT i2.name FROM comments p 
                INNER JOIN identities i2 ON p.identityId = i2.id 
                WHERE p.id = c.parentCommentId) as replyToIdentityName,
               c.likeCount, 0 as isLikedByMe
        FROM comments c
        INNER JOIN identities i ON c.identityId = i.id
        WHERE c.postId = :postId
        ORDER BY c.createdAt ASC
    """)
    fun getCommentsByPost(postId: Long): Flow<List<CommentWithIdentity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity): Long

    @Update
    suspend fun update(comment: CommentEntity)

    @Delete
    suspend fun delete(comment: CommentEntity)

    @Query("DELETE FROM comments")
    suspend fun deleteAll()

    @Query("UPDATE comments SET likeCount = likeCount + 1, likedByIdentityIds = CASE WHEN likedByIdentityIds = '' THEN :identityIdStr WHEN likedByIdentityIds LIKE '%' || :identityIdStr || '%' THEN likedByIdentityIds ELSE likedByIdentityIds || ',' || :identityIdStr END WHERE id = :commentId")
    suspend fun likeComment(commentId: Long, identityIdStr: String)

    @Query("UPDATE comments SET likeCount = MAX(0, likeCount - 1), likedByIdentityIds = REPLACE(REPLACE(likedByIdentityIds, :identityIdStr, ''), ',,', ',') WHERE id = :commentId")
    suspend fun unlikeComment(commentId: Long, identityIdStr: String)

    @Query("UPDATE comments SET likeCount = likeCount + 1, likedByIdentityIds = CASE WHEN likedByIdentityIds = '' THEN :identityIdStr WHEN likedByIdentityIds LIKE '%' || :identityIdStr || '%' THEN likedByIdentityIds ELSE likedByIdentityIds || ',' || :identityIdStr END WHERE id = :commentId")
    suspend fun likeCommentById(commentId: Long, identityIdStr: String)

    @Query("UPDATE comments SET likeCount = MAX(0, likeCount - 1), likedByIdentityIds = REPLACE(REPLACE(likedByIdentityIds, :identityIdStr, ''), ',,', ',') WHERE id = :commentId")
    suspend fun unlikeCommentById(commentId: Long, identityIdStr: String)
}