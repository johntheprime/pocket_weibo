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
    val createdAt: Long
)

@Dao
interface CommentDao {
    @Query("""
        SELECT c.id, c.postId, c.identityId, i.name as identityName, 
               i.avatarResName as identityAvatarResName, c.content, c.createdAt
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

    @Query("DELETE FROM comments")
    suspend fun deleteAll()
}
