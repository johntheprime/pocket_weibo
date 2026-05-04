package com.pocketweibo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pocketweibo.diagnostic.DiagnosticLog

object PostReminderAlarmScheduler {

    const val ACTION_POST_REMINDER = "com.pocketweibo.ACTION_POST_REMINDER"

    private const val TAG = "PW_Reminder"

    /** Stable per (reminder row, post); avoids rare PendingIntent collisions from id-only mixing. */
    internal fun requestCode(reminderDbId: Long, postId: Long): Int {
        var h = 17
        h = 31 * h + java.lang.Long.hashCode(reminderDbId)
        h = 31 * h + java.lang.Long.hashCode(postId)
        return h
    }

    /** Pre-3.22.1 used reminder id only; cancel to avoid duplicate alarms after upgrade. */
    private fun cancelLegacyPendingIntent(context: Context, reminderDbId: Long, postId: Long) {
        val legacyCode = (reminderDbId xor (reminderDbId shl 20)).toInt()
        val intent = Intent(context, PostReminderReceiver::class.java).apply {
            action = ACTION_POST_REMINDER
            putExtra(PostReminderReceiver.EXTRA_REMINDER_ID, reminderDbId)
            putExtra(PostReminderReceiver.EXTRA_POST_ID, postId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            legacyCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        pi.cancel()
        DiagnosticLog.d(TAG, "cancel legacy PI reminderDbId=$reminderDbId postId=$postId")
    }

    fun schedule(context: Context, reminderDbId: Long, postId: Long, fireAtMillis: Long) {
        cancelLegacyPendingIntent(context, reminderDbId, postId)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PostReminderReceiver::class.java).apply {
            action = ACTION_POST_REMINDER
            putExtra(PostReminderReceiver.EXTRA_REMINDER_ID, reminderDbId)
            putExtra(PostReminderReceiver.EXTRA_POST_ID, postId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(reminderDbId, postId),
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
        DiagnosticLog.d(
            TAG,
            "schedule alarm reminderDbId=$reminderDbId postId=$postId fireAtMillis=$fireAtMillis deltaMs=${fireAtMillis - System.currentTimeMillis()}"
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMillis, showPi), pi)
        } catch (e: SecurityException) {
            DiagnosticLog.e(TAG, "setAlarmClock SecurityException, trying setExactAndAllowWhileIdle", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
                } else {
                    @Suppress("DEPRECATION")
                    am.setExact(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
                }
            } catch (e2: Exception) {
                DiagnosticLog.e(TAG, "fallback exact alarm failed", e2)
            }
        }
    }

    /**
     * Deliver reminder pipeline now (e.g. fire time passed while device was off).
     * Cancels any pending alarm for this row first.
     */
    fun deliverImmediately(context: Context, reminderDbId: Long, postId: Long) {
        cancel(context, reminderDbId, postId)
        val intent = Intent(context, PostReminderReceiver::class.java).apply {
            action = ACTION_POST_REMINDER
            putExtra(PostReminderReceiver.EXTRA_REMINDER_ID, reminderDbId)
            putExtra(PostReminderReceiver.EXTRA_POST_ID, postId)
        }
        DiagnosticLog.d(TAG, "deliverImmediately reminderDbId=$reminderDbId postId=$postId")
        context.sendBroadcast(intent)
    }

    fun cancel(context: Context, reminderDbId: Long, postId: Long) {
        cancelLegacyPendingIntent(context, reminderDbId, postId)
        val intent = Intent(context, PostReminderReceiver::class.java).apply {
            action = ACTION_POST_REMINDER
            putExtra(PostReminderReceiver.EXTRA_REMINDER_ID, reminderDbId)
            putExtra(PostReminderReceiver.EXTRA_POST_ID, postId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(reminderDbId, postId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        pi.cancel()
        DiagnosticLog.d(TAG, "cancel alarm reminderDbId=$reminderDbId postId=$postId")
    }
}
