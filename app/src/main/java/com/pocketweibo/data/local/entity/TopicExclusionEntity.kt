package com.pocketweibo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A post manually removed from a topic. The post still keeps its hashtag text; it is only
 * hidden from that topic's detail list. [topicKey] is the canonical (post-merge) key.
 */
@Entity(
    tableName = "topic_exclusions",
    primaryKeys = ["topicKey", "postId"],
    foreignKeys = [
        ForeignKey(
            entity = PostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("postId")]
)
data class TopicExclusionEntity(
    val topicKey: String,
    val postId: Long
)
