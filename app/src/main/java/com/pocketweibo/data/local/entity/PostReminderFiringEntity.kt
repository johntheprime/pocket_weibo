package com.pocketweibo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pocketweibo.reminder.ReminderRepeatRule

/**
 * Permanent record of one successfully delivered app notification.
 * One-shot reminders are deleted after firing and repeating rows advance to the next time,
 * so this history is the only reliable way to know that a post was actually reminded.
 */
@Entity(
    tableName = "post_reminder_firings",
    foreignKeys = [
        ForeignKey(
            entity = PostEntity::class,
            parentColumns = ["id"],
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("postId"), Index("firedAtMillis")]
)
data class PostReminderFiringEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val postId: Long,
    /** Original reminder row; nullable because one-shot rows are deleted after firing. */
    val reminderId: Long? = null,
    /** Time the alarm was scheduled for. */
    val scheduledForMillis: Long,
    /** Time the notification was actually posted. */
    val firedAtMillis: Long,
    /** [ReminderRepeatRule] value of the firing reminder. */
    val repeatRule: String = ReminderRepeatRule.NONE
)
