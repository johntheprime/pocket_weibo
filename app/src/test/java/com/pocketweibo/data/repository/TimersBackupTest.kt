package com.pocketweibo.data.repository

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.IdentityScheduleEntity
import com.pocketweibo.data.local.entity.PostEntity
import com.pocketweibo.data.local.entity.PostReminderEntity
import com.pocketweibo.reminder.ReminderRepeatRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Post reminders and identity schedules must survive export -> import, and be restorable alone. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TimersBackupTest {

    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: WeiboRepository
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = WeiboRepository(
            db, db.identityDao(), db.identityScheduleDao(), db.postDao(), db.commentDao(),
            db.postReminderDao(), db.postReminderFiringDao(), db.topicDao(), ctx
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): Pair<Long, Long> {
        val who = db.identityDao().insert(IdentityEntity(name = "A", isActive = true))
        val post = db.postDao().insert(PostEntity(identityId = who, content = "p", createdAt = 1000L))
        val now = System.currentTimeMillis()
        db.postReminderDao().insert(PostReminderEntity(postId = post, fireAtMillis = now + day))
        val past = db.postDao().insert(PostEntity(identityId = who, content = "old", createdAt = 2000L))
        db.postReminderDao().insert(
            PostReminderEntity(postId = past, fireAtMillis = now - 3 * day, repeatRule = ReminderRepeatRule.DAILY)
        )
        db.identityScheduleDao().insert(IdentityScheduleEntity(identityId = who, hour = 9, minute = 30, daysOfWeek = "1,2,3,4,5"))
        return who to post
    }

    @Test
    fun replaceImport_keepsRemindersAndSchedules() = runBlocking {
        seed()
        val json = repo.exportAllData()
        assertTrue(repo.importData(json, override = true))
        val reminders = db.postReminderDao().listAll()
        assertEquals(2, reminders.size)
        assertTrue("repeating reminder rolled forward", reminders.all { it.fireAtMillis > System.currentTimeMillis() })
        assertEquals(1, db.identityScheduleDao().listAll().size)
    }

    @Test
    fun mergeImport_mapsTimersToNewIds() = runBlocking {
        seed()
        val json = repo.exportAllData()
        assertTrue(repo.importData(json, override = false))
        val postIds = db.postDao().listAllIds().toSet()
        val reminders = db.postReminderDao().listAll()
        assertEquals(4, reminders.size)
        assertTrue(reminders.all { it.postId in postIds })
        assertEquals(2, db.identityScheduleDao().listAll().size)
    }

    @Test
    fun restoreTimers_onlyAddsTimers_matchedByContent() = runBlocking {
        seed()
        val file = File(ctx.cacheDir, "backup.json").apply { writeText(repo.exportAllData()) }
        // Simulate the lost-timers state: posts and identities still there, timers gone.
        db.postReminderDao().listAll().forEach { db.postReminderDao().deleteById(it.id) }
        db.identityScheduleDao().deleteAll()
        val postsBefore = db.postDao().listAllIds().size

        val r = repo.restoreTimersFromBackup(Uri.fromFile(file))
        assertEquals(2, r.reminders)
        assertEquals(1, r.schedules)
        assertEquals(postsBefore, db.postDao().listAllIds().size)

        val again = repo.restoreTimersFromBackup(Uri.fromFile(file))
        assertEquals("no duplicates", 0, again.reminders + again.schedules)
    }
}
