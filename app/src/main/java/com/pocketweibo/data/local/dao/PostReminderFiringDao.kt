package com.pocketweibo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pocketweibo.data.local.entity.PostReminderFiringEntity
import kotlinx.coroutines.flow.Flow

data class LastReminderFiring(val postId: Long, val lastFiredAtMillis: Long)

@Dao
interface PostReminderFiringDao {

    @Insert
    suspend fun insert(entity: PostReminderFiringEntity): Long

    @Query("SELECT * FROM post_reminder_firings ORDER BY firedAtMillis ASC")
    suspend fun listAll(): List<PostReminderFiringEntity>

    @Query("SELECT * FROM post_reminder_firings ORDER BY firedAtMillis ASC")
    fun observeAll(): Flow<List<PostReminderFiringEntity>>

    @Query(
        """
        SELECT postId, MAX(firedAtMillis) AS lastFiredAtMillis
        FROM post_reminder_firings
        GROUP BY postId
        """
    )
    fun observeLastFiringByPost(): Flow<List<LastReminderFiring>>

    @Query("DELETE FROM post_reminder_firings")
    suspend fun deleteAll()
}
