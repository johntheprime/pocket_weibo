package com.pocketweibo.data

import org.junit.Test
import org.junit.Assert.*

class IdentityEntityTest {
    @Test
    fun testIdentityEntity_defaultValues() {
        val identity = com.pocketweibo.data.local.entity.IdentityEntity(
            id = 1L,
            name = "Test User"
        )
        assertEquals("Test User", identity.name)
        assertEquals(1L, identity.id)
    }

    @Test
    fun testIdentityEntity_fullValues() {
        val identity = com.pocketweibo.data.local.entity.IdentityEntity(
            id = 2L,
            name = "Full User",
            avatarResName = "avatar_1",
            nationality = "China",
            gender = com.pocketweibo.data.local.entity.Gender.MALE,
            birthYear = 1900,
            deathYear = 1970,
            occupation = "Writer",
            motto = "Test motto",
            famousWork = "Test work",
            bio = "Test bio",
            isActive = true
        )
        assertEquals("China", identity.nationality)
        assertEquals(com.pocketweibo.data.local.entity.Gender.MALE, identity.gender)
        assertTrue(identity.isActive)
    }
}

class PostEntityTest {
    @Test
    fun testPostEntity_defaultValues() {
        val post = com.pocketweibo.data.local.entity.PostEntity(
            identityId = 1L,
            content = "Test content"
        )
        assertEquals("Test content", post.content)
        assertEquals(1L, post.identityId)
        assertEquals(0, post.likeCount)
        assertEquals(0, post.commentCount)
        assertFalse(post.isLiked)
    }

    @Test
    fun testPostEntity_nullIdentityId() {
        val post = com.pocketweibo.data.local.entity.PostEntity(
            identityId = null,
            content = "Orphan"
        )
        assertNull(post.identityId)
        assertEquals("Orphan", post.content)
    }

    @Test
    fun testPostEntity_withValues() {
        val timestamp = System.currentTimeMillis()
        val post = com.pocketweibo.data.local.entity.PostEntity(
            id = 5L,
            identityId = 10L,
            content = "Hello World",
            imageUris = "uri1,uri2",
            createdAt = timestamp,
            likeCount = 100,
            commentCount = 50,
            isLiked = true
        )
        assertEquals(100, post.likeCount)
        assertEquals(50, post.commentCount)
        assertTrue(post.isLiked)
    }
}

class CommentEntityTest {
    @Test
    fun testCommentEntity() {
        val comment = com.pocketweibo.data.local.entity.CommentEntity(
            postId = 1L,
            identityId = 2L,
            content = "Nice post!"
        )
        assertEquals("Nice post!", comment.content)
        assertEquals("", comment.audioPath)
        assertEquals(1L, comment.postId)
        assertEquals(2L, comment.identityId)
    }

    @Test
    fun testCommentEntity_nullIdentityId() {
        val comment = com.pocketweibo.data.local.entity.CommentEntity(
            postId = 1L,
            identityId = null,
            content = "Orphan comment"
        )
        assertNull(comment.identityId)
    }
}