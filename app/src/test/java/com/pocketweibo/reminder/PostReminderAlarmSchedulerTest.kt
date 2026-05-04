package com.pocketweibo.reminder

import org.junit.Assert.assertNotEquals
import org.junit.Test

class PostReminderAlarmSchedulerTest {

    @Test
    fun requestCode_variesByPostId() {
        val a = PostReminderAlarmScheduler.requestCode(1L, 2L)
        val b = PostReminderAlarmScheduler.requestCode(1L, 3L)
        assertNotEquals(a, b)
    }

    @Test
    fun requestCode_variesByReminderId() {
        val a = PostReminderAlarmScheduler.requestCode(1L, 10L)
        val b = PostReminderAlarmScheduler.requestCode(2L, 10L)
        assertNotEquals(a, b)
    }
}
