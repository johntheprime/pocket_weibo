package com.pocketweibo.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderRepeatRuleTest {

    @Test
    fun isRepeating_noneFalse() {
        assertFalse(ReminderRepeatRule.isRepeating(ReminderRepeatRule.NONE))
        assertTrue(ReminderRepeatRule.isRepeating(ReminderRepeatRule.DAILY))
        assertTrue(ReminderRepeatRule.isRepeating(ReminderRepeatRule.WORKDAYS))
    }

    @Test
    fun computeNext_noneReturnsNull() {
        assertNull(ReminderRepeatRule.computeNextFireAfter(System.currentTimeMillis(), ReminderRepeatRule.NONE))
    }

    @Test
    fun daily_advancesLocalDate() {
        val zone = ZoneId.systemDefault()
        val base = ZonedDateTime.of(2026, 6, 15, 10, 30, 0, 0, zone).toInstant().toEpochMilli()
        val next = ReminderRepeatRule.computeNextFireAfter(base, ReminderRepeatRule.DAILY)!!
        val d0 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(base), zone).toLocalDate()
        val d1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone).toLocalDate()
        assertEquals(d0.plusDays(1), d1)
    }

    @Test
    fun weekly_advancesSevenDays() {
        val zone = ZoneId.systemDefault()
        val base = ZonedDateTime.of(2026, 3, 2, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = ReminderRepeatRule.computeNextFireAfter(base, ReminderRepeatRule.WEEKLY)!!
        val d0 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(base), zone).toLocalDate()
        val d1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone).toLocalDate()
        assertEquals(d0.plusDays(7), d1)
    }

    @Test
    fun monthly_advancesOneMonth() {
        val zone = ZoneId.systemDefault()
        val base = ZonedDateTime.of(2026, 1, 31, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = ReminderRepeatRule.computeNextFireAfter(base, ReminderRepeatRule.MONTHLY)!!
        val z0 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(base), zone)
        val z1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(z0.toLocalDate().plusMonths(1), z1.toLocalDate())
    }

    @Test
    fun workdays_advancesToNextWeekday() {
        val zone = ZoneId.of("Asia/Shanghai")
        val base = ZonedDateTime.of(2026, 1, 14, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = ReminderRepeatRule.computeNextFireAfter(base, ReminderRepeatRule.WORKDAYS)!!
        val z1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(DayOfWeek.THURSDAY, z1.dayOfWeek)
        assertEquals(LocalDate.of(2026, 1, 15), z1.toLocalDate())
    }

    @Test
    fun workdays_skipsWeekendFromFriday() {
        val zone = ZoneId.of("Asia/Shanghai")
        val base = ZonedDateTime.of(2026, 1, 16, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = ReminderRepeatRule.computeNextFireAfter(base, ReminderRepeatRule.WORKDAYS)!!
        val z1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(DayOfWeek.MONDAY, z1.dayOfWeek)
        assertEquals(LocalDate.of(2026, 1, 19), z1.toLocalDate())
    }

    @Test
    fun nextAfterSuccessfulFire_skipsPastOccurrences() {
        val zone = ZoneId.systemDefault()
        val fired = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(2026, 1, 5, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = ReminderRepeatRule.nextAfterSuccessfulFire(fired, ReminderRepeatRule.DAILY, now)!!
        assertTrue(next > now)
        val nextDay = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone).toLocalDate()
        assertEquals(ZonedDateTime.of(2026, 1, 6, 9, 0, 0, 0, zone).toLocalDate(), nextDay)
    }
}
