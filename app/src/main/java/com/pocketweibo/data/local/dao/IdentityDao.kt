package com.pocketweibo.data.local.dao

import androidx.room.*
import com.pocketweibo.data.local.entity.IdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities ORDER BY createdAt DESC")
    fun getAllIdentities(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE isActive = 1 LIMIT 1")
    fun getActiveIdentity(): Flow<IdentityEntity?>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun getIdentityById(id: Long): IdentityEntity?

    /** New row only ([IdentityEntity.id] = 0 for auto id). Never use REPLACE on this table: SQLite deletes the old row first and CASCADE-wipes posts for that identity. */
    @Insert
    suspend fun insert(identity: IdentityEntity): Long

    @Update
    suspend fun update(identity: IdentityEntity)

    @Delete
    suspend fun delete(identity: IdentityEntity)

    @Query("UPDATE identities SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE identities SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("UPDATE identities SET customAvatarUri = NULL WHERE id = :id")
    suspend fun clearCustomAvatar(id: Long)

    @Query("DELETE FROM identities")
    suspend fun deleteAll()
}
