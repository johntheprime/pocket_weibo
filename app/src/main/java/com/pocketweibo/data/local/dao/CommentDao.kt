package com.pocketweibo.data.local.dao

import androidx.room.*
import com.pocketweibo.data.local.entity.CommentEntity
import kotlinx.coroutines.flow.Flow

data class CommentWithIdentity(
    val id: Long,
    val postId: Long,
    val identityId: Long?,
    val identityName: String?,
    val identityAvatarResName: String?,
    val identityCustomAvatarUri: String?,
    val content: String,
    val audioPath: String,
    val createdAt: Long,
    val replyingToCommentId: Long?,
    val replyingToIdentityName: String?,
    val likeCount: Int,
    val likedBy: String,
    val isLikedByMe: Boolean
)

/** Comment row joined with parent post text and authors for global search. */
data class CommentSearchRow(
    val id: Long,
    val postId: Long,
    val content: String,
    val audioPath: String,
    val createdAt: Long,
    val commentAuthorName: String?,
    val postContent: String,
    val postAuthorName: String?
)

@Dao
interface CommentDao {
    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarResName as identityAvatarResName,
               i.customAvatarUri as identityCustomAvatarUri,
               c.content, c.audioPath, c.createdAt,
               c.replyingToCommentId, 
               (SELECT i2.name FROM comments c2 LEFT JOIN identities i2 ON c2.identityId = i2.id WHERE c2.id = c.replyingToCommentId) as replyingToIdentityName,
               c.likeCount,
               c.likedBy,
               CASE WHEN c.likedBy LIKE '%' || :currentIdentityId || '%' THEN 1 ELSE 0 END as isLikedByMe
        FROM comments c
        LEFT JOIN identities i ON c.identityId = i.id
        WHERE c.postId = :postId
        ORDER BY c.createdAt DESC
    """)
    fun getCommentsByPost(postId: Long, currentIdentityId: Long = 0): Flow<List<CommentWithIdentity>>

    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarResName as identityAvatarResName,
               i.customAvatarUri as identityCustomAvatarUri,
               c.content, c.audioPath, c.createdAt,
               c.replyingToCommentId, 
               (SELECT i2.name FROM comments c2 LEFT JOIN identities i2 ON c2.identityId = i2.id WHERE c2.id = c.replyingToCommentId) as replyingToIdentityName,
               c.likeCount,
               c.likedBy,
               0 as isLikedByMe
        FROM comments c
        LEFT JOIN identities i ON c.identityId = i.id
        WHERE c.postId = :postId
        ORDER BY c.createdAt DESC
    """)
    fun getCommentsByPostNoAuth(postId: Long): Flow<List<CommentWithIdentity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity): Long

    @Update
    suspend fun update(comment: CommentEntity)

    @Query("SELECT * FROM comments WHERE id = :id LIMIT 1")
    suspend fun getEntityById(id: Long): CommentEntity?

    @Query("SELECT id FROM comments WHERE postId = :postId")
    suspend fun listCommentIdsForPost(postId: Long): List<Long>

    @Delete
    suspend fun delete(comment: CommentEntity)

    @Query("SELECT * FROM comments ORDER BY createdAt ASC")
    suspend fun listAllForBackup(): List<CommentEntity>

    @Query("DELETE FROM comments")
    suspend fun deleteAll()

    @Query("UPDATE comments SET likeCount = likeCount + 1, likedBy = likedBy || :identityId || ',' WHERE id = :commentId")
    suspend fun likeCommentById(commentId: Long, identityId: String)

    @Query("UPDATE comments SET likeCount = likeCount - 1, likedBy = REPLACE(likedBy, :identityId || ',', '') WHERE id = :commentId AND likeCount > 0")
    suspend fun unlikeCommentById(commentId: Long, identityId: String)

    @Query("UPDATE comments SET content = :newContent WHERE id = :commentId")
    suspend fun updateCommentContent(commentId: Long, newContent: String)

    @Query(
        """
        SELECT c.id AS id, c.postId AS postId, c.content AS content, c.audioPath AS audioPath, c.createdAt AS createdAt,
               ci.name AS commentAuthorName, p.content AS postContent, pi.name AS postAuthorName
        FROM comments c
        LEFT JOIN identities ci ON c.identityId = ci.id
        INNER JOIN posts p ON c.postId = p.id
        LEFT JOIN identities pi ON p.identityId = pi.id
        ORDER BY c.createdAt DESC
        """
    )
    fun observeAllCommentsForSearch(): Flow<List<CommentSearchRow>>
}
