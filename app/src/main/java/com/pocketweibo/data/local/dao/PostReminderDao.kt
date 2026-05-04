package com.pocketweibo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pocketweibo.data.local.entity.PostReminderEntity
import kotlinx.coroutines.flow.Flow

/** Join row for Me page and other pending-reminder UIs. */
data class PostReminderWithPreview(
    val reminderId: Long,
    val postId: Long,
    val fireAtMillis: Long,
    val content: String,
    val identityName: String
)

@Dao
interface PostReminderDao {

    @Insert
    suspend fun insert(entity: PostReminderEntity): Long

    @Query("SELECT * FROM post_reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PostReminderEntity?

    @Query("SELECT * FROM post_reminders WHERE postId = :postId")
    suspend fun listForPost(postId: Long): List<PostReminderEntity>

    @Query("SELECT * FROM post_reminders ORDER BY fireAtMillis ASC")
    suspend fun listAll(): List<PostReminderEntity>

    @Query(
        """
        SELECT r.id AS reminderId, r.postId AS postId, r.fireAtMillis AS fireAtMillis,
               p.content AS content, i.name AS identityName
        FROM post_reminders r
        INNER JOIN posts p ON r.postId = p.id
        INNER JOIN identities i ON p.identityId = i.id
        ORDER BY r.fireAtMillis ASC
        """
    )
    fun observePendingRemindersWithPreview(): Flow<List<PostReminderWithPreview>>

    @Query("DELETE FROM post_reminders WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Long)

    @Query("DELETE FROM post_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
