package com.pocketweibo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = PostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["identityId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("postId"), Index("identityId")]
)
data class CommentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val postId: Long,
    val identityId: Long?,
    val content: String,
    val audioPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val replyingToCommentId: Long? = null,
    val likeCount: Int = 0,
    val likedBy: String = ""
)
