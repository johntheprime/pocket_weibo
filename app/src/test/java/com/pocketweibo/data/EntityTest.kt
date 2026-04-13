package com.pocketweibo.data

import org.junit.Test
import org.junit.Assert.*

class CommentTimeFormattingTest {
    @Test
    fun testFormatCommentTime_justNow() {
        val now = System.currentTimeMillis()
        val result = formatCommentTime(now)
        assertEquals("刚刚", result)
    }

    @Test
    fun testFormatCommentTime_minutes() {
        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000)
        val result = formatCommentTime(fiveMinutesAgo)
        assertEquals("5分钟前", result)
    }

    @Test
    fun testFormatCommentTime_hours() {
        val now = System.currentTimeMillis()
        val threeHoursAgo = now - (3 * 60 * 60 * 1000)
        val result = formatCommentTime(threeHoursAgo)
        assertEquals("3小时前", result)
    }

    @Test
    fun testFormatCommentTime_days() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - (2 * 24 * 60 * 60 * 1000)
        val result = formatCommentTime(twoDaysAgo)
        assertEquals("2天前", result)
    }

    private fun formatCommentTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 7 -> "${days}天前"
            else -> "7天前"
        }
    }
}

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
        assertEquals(1L, comment.postId)
        assertEquals(2L, comment.identityId)
    }
}