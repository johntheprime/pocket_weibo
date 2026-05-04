package com.pocketweibo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.pocketweibo.R
import com.pocketweibo.data.DataSeeder
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.prefs.UiPreferences
import com.pocketweibo.data.repository.WeiboRepository
import com.pocketweibo.diagnostic.DiagnosticLog
import com.pocketweibo.diagnostic.DiagnosticLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PocketWeiboApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        WeiboRepository(
            database.identityDao(),
            database.postDao(),
            database.commentDao(),
            database.postReminderDao(),
            this
        )
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
        // DataStore is async; scheduling can run on IO right after start — restore capture synchronously
        // so DiagnosticLog lines are not dropped while the user believes capture is on.
        runBlocking(Dispatchers.IO) {
            DiagnosticLogBuffer.captureEnabled =
                UiPreferences.isDiagnosticLogCaptureEnabled(this@PocketWeiboApp)
        }
        if (DiagnosticLogBuffer.captureEnabled) {
            DiagnosticLog.i("PW_Reminder", "Diagnostic capture on at process start")
        }
        applicationScope.launch(Dispatchers.IO) {
            repository.rescheduleAllPostRemindersFromDb()
        }
        DataSeeder.seedIfEmpty(repository)
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
