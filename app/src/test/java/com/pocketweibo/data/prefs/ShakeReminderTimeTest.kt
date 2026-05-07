package com.pocketweibo.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ShakeReminderTimeTest {

    @Test
    fun beforeEvening_usesTodayEvening() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MAY, 7, 14, 30, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val now = cal.timeInMillis
        val settings = ShakeReminderSettings(20, 0, 9, 0)
        val fire = nextShakeReminderFireAtMillis(settings, now, 5_000L)
        val out = Calendar.getInstance().apply { timeInMillis = fire }
        assertEquals(2026, out.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, out.get(Calendar.MONTH))
        assertEquals(7, out.get(Calendar.DAY_OF_MONTH))
        assertEquals(20, out.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, out.get(Calendar.MINUTE))
    }

    @Test
    fun afterEvening_usesNextMorning() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MAY, 7, 22, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val now = cal.timeInMillis
        val settings = ShakeReminderSettings(20, 0, 9, 0)
        val fire = nextShakeReminderFireAtMillis(settings, now, 5_000L)
        val out = Calendar.getInstance().apply { timeInMillis = fire }
        assertEquals(2026, out.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, out.get(Calendar.MONTH))
        assertEquals(8, out.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, out.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, out.get(Calendar.MINUTE))
    }
}
