package com.pocketweibo.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class IdentityScheduleNextFireTest {

    @Test
    fun daily_returnsTodayIfFuture() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val targetHour = now.hour
        val targetMin = if (now.minute < 59) now.minute + 1 else now.minute
        val next = IdentityScheduleNextFire.computeNext(targetHour, targetMin, "")
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(now.toLocalDate(), znext.toLocalDate())
        assertTrue(znext.toLocalTime().toSecondOfDay() >= now.toLocalTime().toSecondOfDay())
    }

    @Test
    fun daily_returnsTomorrowIfPast() {
        val zone = ZoneId.systemDefault()
        val next = IdentityScheduleNextFire.computeNext(0, 0, "")
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        val expected = ZonedDateTime.now(zone).toLocalDate()
        val actual = znext.toLocalDate()
        assertTrue(actual == expected || actual == expected.plusDays(1))
    }

    @Test
    fun weekdays_skipsWeekend() {
        val zone = ZoneId.systemDefault()
        val friday = ZonedDateTime.of(2026, 5, 15, 10, 1, 0, 0, zone)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val next = IdentityScheduleNextFire.computeNext(
            10, 0, "1,2,3,4,5",
            nowMillis = friday.toInstant().toEpochMilli()
        )
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(DayOfWeek.MONDAY, znext.dayOfWeek)
        assertEquals(LocalDate.of(2026, 5, 18), znext.toLocalDate())
    }

    @Test
    fun weekdays_firesTodayIfNotPast() {
        val zone = ZoneId.systemDefault()
        val friday = ZonedDateTime.of(2026, 5, 15, 9, 0, 0, 0, zone)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val next = IdentityScheduleNextFire.computeNext(
            10, 0, "1,2,3,4,5",
            nowMillis = friday.toInstant().toEpochMilli()
        )
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(friday.toLocalDate(), znext.toLocalDate())
        assertEquals(10, znext.hour)
    }

    @Test
    fun customDays_returnsNextAllowed() {
        val zone = ZoneId.systemDefault()
        val wednesday = ZonedDateTime.of(2026, 5, 13, 8, 0, 0, 0, zone)
        assertEquals(DayOfWeek.WEDNESDAY, wednesday.dayOfWeek)
        val next = IdentityScheduleNextFire.computeNext(
            9, 0, "1,3,5",
            nowMillis = wednesday.toInstant().toEpochMilli()
        )
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(wednesday.toLocalDate(), znext.toLocalDate())
    }

    @Test
    fun customDays_advancesToNextAllowedDay() {
        val zone = ZoneId.systemDefault()
        val wednesdayEvening = ZonedDateTime.of(2026, 5, 13, 20, 0, 0, 0, zone)
        assertEquals(DayOfWeek.WEDNESDAY, wednesdayEvening.dayOfWeek)
        val next = IdentityScheduleNextFire.computeNext(
            9, 0, "1,3,5",
            nowMillis = wednesdayEvening.toInstant().toEpochMilli()
        )
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(DayOfWeek.FRIDAY, znext.dayOfWeek)
        assertEquals(LocalDate.of(2026, 5, 15), znext.toLocalDate())
    }

    @Test
    fun weekends_returnsSaturdayOrSunday() {
        val zone = ZoneId.systemDefault()
        val friday = ZonedDateTime.of(2026, 5, 15, 14, 0, 0, 0, zone)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val next = IdentityScheduleNextFire.computeNext(
            14, 0, "6,7",
            nowMillis = friday.toInstant().toEpochMilli()
        )
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(DayOfWeek.SATURDAY, znext.dayOfWeek)
    }

    @Test
    fun weekends_firesTodayIfSaturday() {
        val zone = ZoneId.systemDefault()
        val saturday = ZonedDateTime.of(2026, 5, 16, 8, 0, 0, 0, zone)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        val next = IdentityScheduleNextFire.computeNext(
            14, 0, "6,7",
            nowMillis = saturday.toInstant().toEpochMilli()
        )
        val znext = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), zone)
        assertEquals(saturday.toLocalDate(), znext.toLocalDate())
    }
}
