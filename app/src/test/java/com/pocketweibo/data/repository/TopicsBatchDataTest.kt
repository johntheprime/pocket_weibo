package com.pocketweibo.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import com.pocketweibo.ui.util.resolveTopicKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Batch topic housekeeping: the same repository calls the multi-select UI loops over. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TopicsBatchDataTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: WeiboRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = WeiboRepository(
            db,
            db.identityDao(),
            db.identityScheduleDao(),
            db.postDao(),
            db.commentDao(),
            db.postReminderDao(),
            db.postReminderFiringDao(),
            db.topicDao(),
            ctx
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun batchMerge_unmergeAndRestore() = runBlocking {
        val who = db.identityDao().insert(IdentityEntity(name = "A", isActive = true))
        val postId = db.postDao().insert(
            PostEntity(identityId = who, content = "#alpha #beta #gamma")
        )

        // Selection of several topics merges into the same canonical target.
        listOf("alpha", "beta").forEach { repo.mergeTopics(it, "gamma") }
        val aliases = db.topicDao().listAliases().associate { it.fromKey to it.toKey }
        assertEquals("gamma", aliases["alpha"])
        assertEquals("gamma", aliases["beta"])
        assertEquals("gamma", resolveTopicKey("alpha", aliases))
        assertEquals("gamma", resolveTopicKey("beta", aliases))

        // Excluded posts can be restored for the whole selection.
        repo.excludePostFromTopic("gamma", postId)
        assertEquals(1, db.topicDao().listExclusions().size)
        listOf("alpha", "gamma").forEach { repo.restoreExcludedPosts(it) }
        assertTrue(db.topicDao().listExclusions().isEmpty())

        // Unmerging the canonical topic splits all spellings back out in one batch.
        listOf("gamma").forEach { repo.unmergeTopicAliases(it) }
        assertTrue(db.topicDao().listAliases().isEmpty())
    }
}
