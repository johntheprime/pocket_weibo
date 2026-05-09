package com.pocketweibo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.diagnostic.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IdentityScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != IdentityScheduleAlarmScheduler.ACTION_SWITCH_IDENTITY) return
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId <= 0L) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = context.applicationContext as PocketWeiboApp
                val dao = app.database.identityScheduleDao()
                val row = dao.getById(scheduleId) ?: return@launch
                if (!row.enabled) return@launch

                app.database.identityDao().deactivateAll()
                app.database.identityDao().activate(row.identityId)
                DiagnosticLog.d(
                    "PW_IdSchedule",
                    "switched to identity ${row.identityId} by schedule $scheduleId"
                )

                val next = IdentityScheduleNextFire.computeNext(
                    row.hour, row.minute, row.daysOfWeek
                )
                val refreshed = dao.getById(scheduleId)
                if (refreshed != null && refreshed.enabled) {
                    IdentityScheduleAlarmScheduler.scheduleNext(context, scheduleId, next)
                }
            } catch (e: Exception) {
                DiagnosticLog.e("PW_IdSchedule", "receiver error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }
}
