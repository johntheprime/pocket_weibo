package com.pocketweibo

import android.app.Application
import com.pocketweibo.data.DataSeeder
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.repository.WeiboRepository

class PocketWeiboApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        WeiboRepository(
            database.identityDao(),
            database.postDao(),
            database.commentDao()
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
        DataSeeder.seedIfEmpty(repository)
    }
}
