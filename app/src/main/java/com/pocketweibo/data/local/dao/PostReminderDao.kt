package com.pocketweibo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pocketweibo.data.local.entity.PostReminderEntity

@Dao
interface PostReminderDao {

    @Insert
    suspend fun insert(entity: PostReminderEntity): Long

    @Query("SELECT * FROM post_reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PostReminderEntity?

    @Query("SELECT * FROM post_reminders WHERE postId = :postId")
    suspend fun listForPost(postId: Long): List<PostReminderEntity>

    @Query("DELETE FROM post_reminders WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Long)

    @Query("DELETE FROM post_reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
