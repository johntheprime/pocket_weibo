package com.pocketweibo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketweibo.data.local.entity.TopicAliasEntity
import com.pocketweibo.data.local.entity.TopicExclusionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Query("SELECT * FROM topic_aliases")
    fun observeAliases(): Flow<List<TopicAliasEntity>>

    @Query("SELECT * FROM topic_exclusions")
    fun observeExclusions(): Flow<List<TopicExclusionEntity>>

    @Query("SELECT * FROM topic_aliases")
    suspend fun listAliases(): List<TopicAliasEntity>

    @Query("SELECT * FROM topic_exclusions")
    suspend fun listExclusions(): List<TopicExclusionEntity>

    @Query("DELETE FROM topic_aliases")
    suspend fun clearAllAliases()

    @Query("DELETE FROM topic_exclusions")
    suspend fun clearAllExclusions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: TopicAliasEntity)

    /** Keep existing chains valid when the target topic itself is merged elsewhere. */
    @Query("UPDATE topic_aliases SET toKey = :toKey WHERE toKey = :fromKey")
    suspend fun retargetAliases(fromKey: String, toKey: String)

    @Query("DELETE FROM topic_aliases WHERE fromKey = :fromKey")
    suspend fun deleteAlias(fromKey: String)

    @Query("DELETE FROM topic_aliases WHERE fromKey IN (:fromKeys)")
    suspend fun deleteAliases(fromKeys: List<String>)

    @Query("DELETE FROM topic_aliases WHERE toKey = :toKey")
    suspend fun deleteAliasesPointingTo(toKey: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExclusion(exclusion: TopicExclusionEntity)

    /** Move exclusions with the topic when a merge happens. */
    @Query(
        """
        INSERT OR IGNORE INTO topic_exclusions (topicKey, postId)
        SELECT :toKey, postId FROM topic_exclusions WHERE topicKey = :fromKey
        """
    )
    suspend fun copyExclusions(fromKey: String, toKey: String)

    @Query("DELETE FROM topic_exclusions WHERE topicKey = :topicKey AND postId = :postId")
    suspend fun deleteExclusion(topicKey: String, postId: Long)

    @Query("DELETE FROM topic_exclusions WHERE topicKey = :topicKey")
    suspend fun clearExclusions(topicKey: String)
}
