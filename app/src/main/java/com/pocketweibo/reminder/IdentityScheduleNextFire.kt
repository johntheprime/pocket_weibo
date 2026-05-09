package com.pocketweibo.reminder

import java.util.Calendar

object IdentityScheduleNextFire {

    private val DAY_MAP = mapOf(
        Calendar.MONDAY to 1,
        Calendar.TUESDAY to 2,
        Calendar.WEDNESDAY to 3,
        Calendar.THURSDAY to 4,
        Calendar.FRIDAY to 5,
        Calendar.SATURDAY to 6,
        Calendar.SUNDAY to 7
    )

    fun computeNext(hour: Int, minute: Int, daysOfWeek: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply { timeInMillis = nowMillis }.apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (daysOfWeek.isBlank()) {
            if (target.timeInMillis < now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis
        }

        val allowedDays = daysOfWeek.split(",").map { it.trim().toIntOrNull() ?: 0 }.filter { it in 1..7 }.toSet()
        if (allowedDays.isEmpty()) {
            if (target.timeInMillis < now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis
        }

        for (offset in 0..7) {
            val check = Calendar.getInstance().apply {
                timeInMillis = target.timeInMillis
                add(Calendar.DAY_OF_MONTH, offset)
            }
            val storedDay = DAY_MAP[check.get(Calendar.DAY_OF_WEEK)] ?: check.get(Calendar.DAY_OF_WEEK)
            if (storedDay in allowedDays) {
                if (offset == 0 && check.timeInMillis >= now.timeInMillis) {
                    return check.timeInMillis
                } else if (offset > 0) {
                    check.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                    check.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                    check.set(Calendar.SECOND, 0)
                    check.set(Calendar.MILLISECOND, 0)
                    return check.timeInMillis
                }
            }
        }
        target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis
    }
}
