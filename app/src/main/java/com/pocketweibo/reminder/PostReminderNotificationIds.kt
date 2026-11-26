package com.pocketweibo.reminder

object PostReminderNotificationIds {

    /**
     * Stable, non-negative id for [NotificationManager.notify] (some OEMs mishandle negative ids).
     * BUG-008: derived from the reminder row id alone, which is unique, instead of a hash of
     * (postId, reminderId) that could collide and replace another post's notification.
     * [postId] is kept in the signature for call-site compatibility.
     */
    @Suppress("UNUSED_PARAMETER")
    fun notifyId(postId: Long, reminderId: Long): Int =
        (reminderId and Int.MAX_VALUE.toLong()).toInt()
}
