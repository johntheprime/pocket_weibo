package com.pocketweibo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.pocketweibo.R
import com.pocketweibo.data.backup.AutoDailyBackup
import com.pocketweibo.data.DataSeeder
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.prefs.UiPreferences
import com.pocketweibo.data.repository.WeiboRepository
import com.pocketweibo.diagnostic.DiagnosticLog
import com.pocketweibo.diagnostic.DiagnosticLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PocketWeiboApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        WeiboRepository(
            database,
            database.identityDao(),
            database.identityScheduleDao(),
            database.postDao(),
            database.commentDao(),
            database.postReminderDao(),
            database.postReminderFiringDao(),
            database.topicDao(),
            this
        )
    }

    /** Startup reschedule work; see [onCreate]. */
    @Volatile
    private var startupJob: Job? = null

    /** Suspends until startup rescheduling has finished (no-op if already done). */
    suspend fun awaitStartup() {
        startupJob?.join()
    }

    var onExitConfirm: (() -> Unit)? = null
    private var lastBackPressTime = 0L

    fun shouldExit(): Boolean {
        val currentTime = System.currentTimeMillis()
        return if (currentTime - lastBackPressTime < 2000) {
            true
        } else {
            lastBackPressTime = currentTime
            false
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureReminderChannel()
        // Roadmap P1: restore the diagnostic flag and alarm registrations on IO without blocking the
        // main thread. Ordering guarantee: every BroadcastReceiver calls [awaitStartup] before touching
        // reminders/schedules, so overdue recovery never races an in-flight alarm delivery.
        startupJob = applicationScope.launch(Dispatchers.IO) {
            DiagnosticLogBuffer.captureEnabled =
                UiPreferences.isDiagnosticLogCaptureEnabled(this@PocketWeiboApp)
            if (DiagnosticLogBuffer.captureEnabled) {
                DiagnosticLog.i("PW_Reminder", "Diagnostic capture on at process start")
            }
            repository.rescheduleAllPostRemindersFromDb()
            repository.rescheduleAllIdentitySchedulesFromDb()
        }
        applicationScope.launch(Dispatchers.IO) {
            awaitStartup()
            DataSeeder.seedIfFirstRun(this@PocketWeiboApp, repository)
            AutoDailyBackup.runIfDue(this@PocketWeiboApp, repository)
        }
    }

    private fun ensureReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        // Older builds used IMPORTANCE_DEFAULT; Android does not raise importance on update — replace channel.
        nm.deleteNotificationChannel(LEGACY_REMINDER_CHANNEL_ID)
        val ch = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.reminder_channel_desc)
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "post_reminders_high"
        private const val LEGACY_REMINDER_CHANNEL_ID = "post_reminders"
    }
}
