package com.pocketweibo.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Roadmap P1 (REVIEW-2 §6): in-memory Room + repository tests that pin down BUG-001 / BUG-002
 * and the atomic override import. Uses a plain [Application] so PocketWeiboApp.onCreate
 * (alarms, seeding, auto-backup) does not run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class WeiboRepositoryDataTest {

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

    private suspend fun identity(name: String, active: Boolean = false): Long =
        db.identityDao().insert(IdentityEntity(name = name, isActive = active))

    // ---- BUG-001 ----

    @Test
    fun exportImportRoundTrip_keepsPinned() = runBlocking {
        val who = identity("A", active = true)
        val pinned = db.postDao().insert(PostEntity(identityId = who, content = "pinned", isPinned = true))
        val normal = db.postDao().insert(PostEntity(identityId = who, content = "normal"))

        val json = repo.exportAllData()
        assertTrue(repo.importData(json, override = true))

        assertTrue(db.postDao().getPostEntityById(pinned)!!.isPinned)
        assertFalse(db.postDao().getPostEntityById(normal)!!.isPinned)
    }

    @Test
    fun autoBackupRoundTrip_keepsPinned() = runBlocking {
        val who = identity("A", active = true)
        val pinned = db.postDao().insert(PostEntity(identityId = who, content = "p", isPinned = true))

        val json = repo.exportTextOnlyForAutoBackup()
        assertTrue(repo.importData(json, override = true))

        assertTrue(db.postDao().getPostEntityById(pinned)!!.isPinned)
    }

    @Test
    fun importOldFileWithoutIsPinned_defaultsToUnpinned() = runBlocking {
        val old = """{"version":3,"identities":[{"id":1,"name":"A"}],
            "posts":[{"id":5,"identityId":1,"content":"x","createdAt":1}],"comments":[]}"""
        assertTrue(repo.importData(old, override = true))
        assertFalse(db.postDao().getPostEntityById(5)!!.isPinned)
    }

    // ---- BUG-002 ----

    @Test
    fun likeState_usesExactIdNotSubstring() = runBlocking {
        val one = identity("one", active = true)          // id 1
        repeat(9) { identity("filler$it") }                // ids 2..10
        val eleven = identity("eleven")                    // id 11
        assertEquals(11L, eleven)
        val post = db.postDao().insert(PostEntity(identityId = one, content = "p"))
        val c = db.commentDao().insert(CommentEntity(postId = post, identityId = one, content = "c"))

        repo.likeComment(c, eleven)
        val asOne = repo.getCommentsByPost(post).first().single()
        assertFalse("id 1 must not match likedBy \"11,\"", asOne.isLikedByMe)

        repo.likeComment(c, one)
        assertTrue(repo.getCommentsByPost(post).first().single().isLikedByMe)
    }

    @Test
    fun like_isIdempotent_andUnlikeRemovesOnlyThatId() = runBlocking {
        val one = identity("one", active = true)
        repeat(9) { identity("filler$it") }
        val eleven = identity("eleven")
        val post = db.postDao().insert(PostEntity(identityId = one, content = "p"))
        val c = db.commentDao().insert(CommentEntity(postId = post, identityId = one, content = "c"))

        repo.likeComment(c, eleven)
        repo.likeComment(c, one)
        repo.likeComment(c, one) // double tap: no-op
        var row = db.commentDao().getEntityById(c)!!
        assertEquals(2, row.likeCount)
        assertEquals("11,1,", row.likedBy)

        repo.unlikeComment(c, one)
        row = db.commentDao().getEntityById(c)!!
        assertEquals(1, row.likeCount)
        assertEquals("11,", row.likedBy)

        repo.unlikeComment(c, one) // not liked: no-op, no negative drift
        row = db.commentDao().getEntityById(c)!!
        assertEquals(1, row.likeCount)
        assertEquals("11,", row.likedBy)
    }

    // ---- atomic override import ----

    @Test
    fun overrideImport_failureRollsBackDatabase() = runBlocking {
        val who = identity("keep", active = true)
        db.postDao().insert(PostEntity(identityId = who, content = "existing"))

        // Second post lacks the required createdAt -> throws mid-import.
        val bad = """{"identities":[{"id":7,"name":"new"}],
            "posts":[{"id":1,"identityId":7,"content":"ok","createdAt":1},{"id":2,"identityId":7,"content":"bad"}]}"""
        assertFalse(repo.importData(bad, override = true))

        val posts = db.postDao().getAllPosts().first()
        assertEquals(listOf("existing"), posts.map { it.content })
        assertEquals(listOf("keep"), db.identityDao().getAllIdentities().first().map { it.name })
    }
}
