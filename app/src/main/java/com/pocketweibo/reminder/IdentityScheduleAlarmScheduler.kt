package com.pocketweibo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pocketweibo.diagnostic.DiagnosticLog

object IdentityScheduleAlarmScheduler {

    const val ACTION_SWITCH_IDENTITY = "com.pocketweibo.ACTION_SWITCH_IDENTITY"

    private const val TAG = "PW_IdSchedule"

    internal fun requestCode(scheduleDbId: Long): Int {
        var h = 17
        h = 31 * h + java.lang.Long.hashCode(scheduleDbId)
        return h
    }

    fun scheduleNext(context: Context, scheduleDbId: Long, fireAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, IdentityScheduleReceiver::class.java).apply {
            action = ACTION_SWITCH_IDENTITY
            putExtra(IdentityScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleDbId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(scheduleDbId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        DiagnosticLog.d(
            TAG,
            "schedule scheduleDbId=$scheduleDbId fireAtMillis=$fireAtMillis deltaMs=${fireAtMillis - System.currentTimeMillis()}"
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
            }
        } catch (e: SecurityException) {
            DiagnosticLog.w(TAG, "setExactAndAllowWhileIdle denied, falling back to set()")
            try {
                am.set(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
            } catch (e2: Exception) {
                DiagnosticLog.e(TAG, "fallback set alarm failed", e2)
            }
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "setExact alarm failed", e)
            try {
                am.set(AlarmManager.RTC_WAKEUP, fireAtMillis, pi)
            } catch (_: Exception) {}
        }
    }

    fun cancel(context: Context, scheduleDbId: Long) {
        val intent = Intent(context, IdentityScheduleReceiver::class.java).apply {
            action = ACTION_SWITCH_IDENTITY
            putExtra(IdentityScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleDbId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(scheduleDbId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        pi.cancel()
        DiagnosticLog.d(TAG, "cancel scheduleDbId=$scheduleDbId")
    }
}
