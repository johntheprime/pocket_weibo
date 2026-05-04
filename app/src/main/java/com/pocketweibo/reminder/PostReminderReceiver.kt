package com.pocketweibo.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pocketweibo.diagnostic.DiagnosticLog
import com.pocketweibo.MainActivity
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PostReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PostReminderAlarmScheduler.ACTION_POST_REMINDER) {
            DiagnosticLog.d(TAG, "onReceive ignored action=${intent?.action}")
            return
        }
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val postId = intent.getLongExtra(EXTRA_POST_ID, -1L)
        DiagnosticLog.d(TAG, "onReceive extras reminderId=$reminderId postId=$postId")
        if (reminderId <= 0L || postId <= 0L) {
            DiagnosticLog.w(TAG, "onReceive bail: invalid extras")
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = context.applicationContext as PocketWeiboApp
                val dao = app.database.postReminderDao()
                val row = dao.getById(reminderId)
                if (row == null) {
                    DiagnosticLog.w(TAG, "no DB row for reminderId=$reminderId (already fired or cancelled?)")
                    return@launch
                }
                val post = app.database.postDao().getPostEntityById(row.postId)
                val title = context.getString(R.string.reminder_notification_title)
                val text = post?.content?.trim()?.take(80)?.ifBlank { context.getString(R.string.reminder_notification_body_fallback) }
                    ?: context.getString(R.string.reminder_notification_body_fallback)

                val open = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN_POST_ID, postId)
                }
                val openPi = PendingIntent.getActivity(
                    context,
                    postId.toInt(),
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, PocketWeiboApp.REMINDER_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_reminder)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(post?.content ?: text))
                    .setContentIntent(openPi)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val nid = PostReminderNotificationIds.notifyId(postId, reminderId)
                var posted = false
                try {
                    nm.notify(nid, notification)
                    posted = true
                    DiagnosticLog.d(TAG, "notify ok notificationId=$nid")
                } catch (e: SecurityException) {
                    DiagnosticLog.e(TAG, "notify SecurityException (POST_NOTIFICATIONS?)", e)
                } catch (e: Exception) {
                    DiagnosticLog.e(TAG, "notify failed", e)
                }

                if (posted) {
                    dao.deleteById(reminderId)
                    DiagnosticLog.d(TAG, "deleted reminder row id=$reminderId after successful notify")
                } else {
                    DiagnosticLog.w(TAG, "keeping reminder row id=$reminderId for retry / debugging")
                }
            } catch (e: Exception) {
                DiagnosticLog.e(TAG, "receiver pipeline error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PW_Reminder"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_POST_ID = "extra_post_id"
    }
}
