package com.pocketweibo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketweibo.data.local.AppDatabase
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.PostEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentityDeleteKeepsPostsTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingIdentity_setsNullAuthorOnPostAndComment_preservesRows() = runBlocking {
        val identityId = db.identityDao().insert(IdentityEntity(name = "ToDelete", isActive = true))
        db.identityDao().deactivateAll()
        db.identityDao().activate(identityId)

        val postId = db.postDao().insert(PostEntity(identityId = identityId, content = "post body"))
        val commentId = db.commentDao().insert(
            CommentEntity(postId = postId, identityId = identityId, content = "comment body")
        )

        val identity = db.identityDao().getIdentityById(identityId)!!
        db.identityDao().delete(identity)

        val post = db.postDao().getPostEntityById(postId)!!
        assertNull(post.identityId)

        val comment = db.commentDao().listAllForBackup().single { it.id == commentId }
        assertNull(comment.identityId)

        val withIdentity = db.postDao().getAllPosts().first().single { it.id == postId }
        assertNull(withIdentity.identityId)
        assertNull(withIdentity.identityName)

        assertEquals(1, db.postDao().getAllPosts().first().count { it.id == postId })
    }
}
