package com.pocketweibo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketweibo.data.local.entity.IdentityScheduleEntity
import kotlinx.coroutines.flow.Flow

data class IdentityScheduleWithIdentityName(
    val id: Long,
    val identityId: Long,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String,
    val enabled: Boolean,
    val identityName: String
)

@Dao
interface IdentityScheduleDao {
    @Query("""
        SELECT s.id, s.identityId, s.hour, s.minute, s.daysOfWeek, s.enabled, i.name AS identityName
        FROM identity_schedules s
        INNER JOIN identities i ON i.id = s.identityId
        ORDER BY s.hour, s.minute
    """)
    fun observeAll(): Flow<List<IdentityScheduleWithIdentityName>>

    @Query("SELECT * FROM identity_schedules WHERE enabled = 1")
    suspend fun listEnabled(): List<IdentityScheduleEntity>

    @Query("SELECT * FROM identity_schedules WHERE id = :id")
    suspend fun getById(id: Long): IdentityScheduleEntity?

    @Insert
    suspend fun insert(schedule: IdentityScheduleEntity): Long

    @Update
    suspend fun update(schedule: IdentityScheduleEntity)

    @Delete
    suspend fun delete(schedule: IdentityScheduleEntity)

    @Query("DELETE FROM identity_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE identity_schedules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM identity_schedules")
    suspend fun deleteAll()
}
