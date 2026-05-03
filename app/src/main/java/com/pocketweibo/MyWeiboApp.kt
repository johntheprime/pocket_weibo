package com.pocketweibo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.pocketweibo.R
import com.pocketweibo.data.DataSeeder
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.repository.WeiboRepository

class PocketWeiboApp : Application() {
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
        DataSeeder.seedIfEmpty(repository)
    }

    private fun ensureReminderChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = getString(R.string.reminder_channel_desc) }
        nm.createNotificationChannel(ch)
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "post_reminders"
    }
}
