package com.pocketweibo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pocketweibo.PocketWeiboApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores alarm registrations after reboot or app update. DB rows are authoritative.
 */
class PostReminderBootReceiver : BroadcastReceiver() {

    private companion object {
        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+). */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED -> Unit
            else -> return
        }
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = context.applicationContext as PocketWeiboApp
                app.awaitStartup()
                app.repository.rescheduleAllPostRemindersFromDb()
                app.repository.rescheduleAllIdentitySchedulesFromDb()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
