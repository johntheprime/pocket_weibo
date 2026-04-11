package com.myweibo.data.local.dao

import androidx.room.*
import com.myweibo.data.local.entity.CommentEntity
import kotlinx.coroutines.flow.Flow

data class CommentWithIdentity(
    val id: Long,
    val postId: Long,
    val identityId: Long,
    val identityName: String,
    val identityAvatarColor: Int,
    val content: String,
    val createdAt: Long
)

@Dao
interface CommentDao {
    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarColor as identityAvatarColor, c.content, c.createdAt
        FROM comments c
        INNER JOIN identities i ON c.identityId = i.id
        WHERE c.postId = :postId
        ORDER BY c.createdAt ASC
    """)
    fun getCommentsByPost(postId: Long): Flow<List<CommentWithIdentity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: CommentEntity): Long

    @Delete
    suspend fun delete(comment: CommentEntity)
}
