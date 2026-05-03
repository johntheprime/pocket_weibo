package com.pocketweibo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object PostReminderAlarmScheduler {

    const val ACTION_POST_REMINDER = "com.pocketweibo.ACTION_POST_REMINDER"

    private fun requestCode(reminderDbId: Long): Int =
        (reminderDbId xor (reminderDbId shl 20)).toInt()

    fun schedule(context: Context, reminderDbId: Long, postId: Long, fireAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PostReminderReceiver::class.java).apply {
            action = ACTION_POST_REMINDER
            putExtra(PostReminderReceiver.EXTRA_REMINDER_ID, reminderDbId)
            putExtra(PostReminderReceiver.EXTRA_POST_ID, postId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(reminderDbId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = Intent(context, com.pocketweibo.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(com.pocketweibo.MainActivity.EXTRA_OPEN_POST_ID, postId)
        }
        val showPi = PendingIntent.getActivity(
            context,
            (postId % Int.MAX_VALUE).toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMillis, showPi), pi)
    }

    fun cancel(context: Context, reminderDbId: Long, postId: Long) {
        val intent = Intent(context, PostReminderReceiver::class.java).apply {
            action = ACTION_POST_REMINDER
            putExtra(PostReminderReceiver.EXTRA_REMINDER_ID, reminderDbId)
            putExtra(PostReminderReceiver.EXTRA_POST_ID, postId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(reminderDbId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        pi.cancel()
    }
}
