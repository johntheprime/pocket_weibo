package com.pocketweibo.data.media

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.repository.WeiboRepository
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

/** FP-P1: repeated exports must not pile up ZIP copies in cache; Clear cache empties it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ShareCacheExportTest {

    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: WeiboRepository

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

    private fun zipsInCache(): List<File> =
        ctx.cacheDir.walkTopDown().filter { it.isFile && it.name.endsWith(".zip") }.toList()

    @Test
    fun exportTwice_leavesAtMostOneZip() = runBlocking {
        // A leftover from an older version with a timestamped name.
        ShareCache.file(ctx, "pocket_weibo_backup_123.zip").writeText("old")
        repo.exportAllDataZip()
        repo.exportAllDataZip()
        assertEquals(1, zipsInCache().size)
    }

    @Test
    fun clearCache_removesShareFiles_andKeepsFilesDir() = runBlocking {
        repo.exportAllDataZip()
        val keep = File(ctx.filesDir, "post_attachments/1/0.jpg").apply { parentFile?.mkdirs(); writeText("img") }
        assertTrue(StorageUsage.measure(ctx).cacheBytes > 0L)
        StorageUsage.clearCache(ctx)
        assertEquals(0, zipsInCache().size)
        assertEquals(0L, StorageUsage.measure(ctx).cacheBytes)
        assertTrue(keep.isFile)
    }
}
