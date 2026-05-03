package com.pocketweibo.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pocketweibo.MainActivity
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PostReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PostReminderAlarmScheduler.ACTION_POST_REMINDER) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val postId = intent.getLongExtra(EXTRA_POST_ID, -1L)
        if (reminderId <= 0L || postId <= 0L) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = context.applicationContext as PocketWeiboApp
                val dao = app.database.postReminderDao()
                val row = dao.getById(reminderId) ?: return@launch
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
                    .setSmallIcon(com.pocketweibo.R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(post?.content ?: text))
                    .setContentIntent(openPi)
                    .setAutoCancel(true)
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    nm.notify((postId xor reminderId).toInt(), notification)
                } catch (_: SecurityException) {
                    // POST_NOTIFICATIONS denied on API 33+
                }

                dao.deleteById(reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_POST_ID = "extra_post_id"
    }
}
