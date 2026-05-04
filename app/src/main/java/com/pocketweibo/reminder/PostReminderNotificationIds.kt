package com.pocketweibo.reminder

import java.util.Objects

object PostReminderNotificationIds {

    /** Stable, non-negative id for [NotificationManager.notify] (some OEMs mishandle negative ids). */
    fun notifyId(postId: Long, reminderId: Long): Int =
        Objects.hash(postId, reminderId) and Int.MAX_VALUE
}
