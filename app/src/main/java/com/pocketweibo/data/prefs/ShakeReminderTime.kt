package com.pocketweibo.data.prefs

import java.util.Calendar

/** Global defaults for “shake on post detail → schedule reminder”. */
data class ShakeReminderSettings(
    val eveningHour: Int,
    val eveningMinute: Int,
    val morningHour: Int,
    val morningMinute: Int,
) {
    companion object {
        val Default = ShakeReminderSettings(20, 0, 9, 0)
    }
}

/**
 * Next fire time: **today at evening** if still more than [minLeadMs] in the future;
 * otherwise **tomorrow at morning** (after configured evening has passed).
 */
fun nextShakeReminderFireAtMillis(
    settings: ShakeReminderSettings,
    minLeadMs: Long = 5_000L,
): Long = nextShakeReminderFireAtMillis(settings, System.currentTimeMillis(), minLeadMs)

internal fun nextShakeReminderFireAtMillis(
    settings: ShakeReminderSettings,
    nowMillis: Long,
    minLeadMs: Long,
): Long {
    val now = nowMillis
    val eveningCal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.HOUR_OF_DAY, settings.eveningHour)
        set(Calendar.MINUTE, settings.eveningMinute)
    }
    val eveningToday = eveningCal.timeInMillis
    if (eveningToday > now + minLeadMs) return eveningToday

    val cal = Calendar.getInstance()
    cal.timeInMillis = now
    cal.add(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, settings.morningHour)
    cal.set(Calendar.MINUTE, settings.morningMinute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
