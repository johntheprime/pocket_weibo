package com.pocketweibo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocketweibo.reminder.ReminderRepeatRule

@Entity(
    tableName = "post_reminders",
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
data class PostReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val postId: Long,
    val fireAtMillis: Long,
    /** [com.pocketweibo.reminder.ReminderRepeatRule] value. */
    val repeatRule: String = ReminderRepeatRule.NONE
)
