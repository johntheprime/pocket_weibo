package com.pocketweibo.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostReminderNotificationIdsTest {

    @Test
    fun notifyId_isAlwaysNonNegative() {
        val samples = listOf(
            1L to 1L,
            1L to 2L,
            99L to 42L,
            Long.MAX_VALUE to 1L,
            1L to Long.MAX_VALUE,
            0x7FFF_FFFFL to 0x7FFF_FFFFL
        )
        for ((p, r) in samples) {
            val id = PostReminderNotificationIds.notifyId(p, r)
            assertTrue("postId=$p reminderId=$r -> id=$id", id >= 0)
        }
    }

    @Test
    fun notifyId_isStableForSameInputs() {
        val a = PostReminderNotificationIds.notifyId(10L, 20L)
        val b = PostReminderNotificationIds.notifyId(10L, 20L)
        assertEquals(a, b)
    }

    @Test
    fun notifyId_differsForDifferentPairs() {
        val a = PostReminderNotificationIds.notifyId(1L, 2L)
        val b = PostReminderNotificationIds.notifyId(2L, 1L)
        assertTrue(a != b)
    }
}
