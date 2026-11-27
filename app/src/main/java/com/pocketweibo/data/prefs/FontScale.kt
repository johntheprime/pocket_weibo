package com.pocketweibo.data.prefs

import kotlin.math.abs

/** Discrete font-size steps, stored as percentages in UiPreferences' DataStore. */
object FontScale {
    const val DEFAULT = 100
    val steps = listOf(85, 100, 115, 130)

    /** Maps a stored (or legacy/invalid) percentage onto the nearest selectable step. */
    fun normalize(percent: Int?): Int =
        steps.minByOrNull { abs(it - (percent ?: DEFAULT)) } ?: DEFAULT
}
