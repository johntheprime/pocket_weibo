package com.pocketweibo.ui.screens.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HomeSearchTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(2026, 11, 27, 21)

    @Test fun allHasNoBounds() = assertNull(SearchDateRange.All.bounds(now, zone))

    @Test fun todayCoversLocalDayOnly() {
        val r = SearchDateRange.Today
        val xs = listOf(at(2026, 11, 27, 0), at(2026, 11, 27, 23), at(2026, 11, 26, 23))
        assertEquals(xs.take(2), xs.filterByDateRange(r, { it }, now, zone))
    }

    @Test fun last7DaysIncludesToday_andSixDaysBack() {
        val xs = listOf(at(2026, 11, 21, 0), at(2026, 11, 20, 23), at(2026, 11, 27, 22))
        assertEquals(listOf(xs[0], xs[2]), xs.filterByDateRange(SearchDateRange.Last7Days, { it }, now, zone))
    }

    @Test fun customIsInclusive_andOrderInsensitive() {
        val r = SearchDateRange.Custom(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1))
        val xs = listOf(at(2026, 11, 1, 0), at(2026, 11, 10, 23), at(2026, 11, 11, 0), at(2026, 10, 31, 23))
        assertEquals(xs.take(2), xs.filterByDateRange(r, { it }, now, zone))
    }

    @Test fun cursorAtEnd_movesCursorToAppendPosition() {
        val s = HomeSearchState(text = TextFieldValue("苏轼|李白", selection = TextRange(0))).cursorAtEnd()
        assertEquals(TextRange(5), s.text.selection)
    }

    @Test fun activeWhenQueryOrRangeSet() {
        assertFalse(HomeSearchState().isActive)
        assertTrue(HomeSearchState(text = TextFieldValue(" a ")).isActive)
        assertTrue(HomeSearchState(range = SearchDateRange.Today).isActive)
    }
}
