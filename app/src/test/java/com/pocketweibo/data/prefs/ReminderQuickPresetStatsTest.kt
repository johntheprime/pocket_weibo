package com.pocketweibo.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderQuickPresetStatsTest {

    @Test
    fun defaultOrderWhenAllZero() {
        val bar = ReminderQuickPresetStats.barFromCounts(
            ReminderQuickPresetId.DEFAULT_ORDER.associateWith { 0 }
        )
        assertEquals(
            listOf(
                ReminderQuickPresetId.M15,
                ReminderQuickPresetId.M30,
                ReminderQuickPresetId.H1
            ),
            bar.topThree
        )
        assertEquals(
            listOf(
                ReminderQuickPresetId.H3,
                ReminderQuickPresetId.H6,
                ReminderQuickPresetId.H10
            ),
            bar.remainder
        )
    }

    @Test
    fun mostUsedFirst_tieBreakStable() {
        val bar = ReminderQuickPresetStats.barFromCounts(
            mapOf(
                ReminderQuickPresetId.M15 to 1,
                ReminderQuickPresetId.M30 to 5,
                ReminderQuickPresetId.H1 to 5,
                ReminderQuickPresetId.H3 to 0,
                ReminderQuickPresetId.H6 to 0,
                ReminderQuickPresetId.H10 to 0
            )
        )
        assertEquals(
            listOf(
                ReminderQuickPresetId.M30,
                ReminderQuickPresetId.H1,
                ReminderQuickPresetId.M15
            ),
            bar.topThree
        )
        assertEquals(
            listOf(
                ReminderQuickPresetId.H3,
                ReminderQuickPresetId.H6,
                ReminderQuickPresetId.H10
            ),
            bar.remainder
        )
    }
}
