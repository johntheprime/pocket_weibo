package com.pocketweibo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A manual topic merge: hashtags whose lowercase key is [fromKey] are shown under [toKey].
 * Keys are stored lowercase so `#AI` and `#ai` behave the same.
 */
@Entity(tableName = "topic_aliases")
data class TopicAliasEntity(
    @PrimaryKey val fromKey: String,
    val toKey: String
)
