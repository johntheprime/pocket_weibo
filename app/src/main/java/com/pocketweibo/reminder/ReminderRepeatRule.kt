package com.pocketweibo.reminder

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Stored in [com.pocketweibo.data.local.entity.PostReminderEntity.repeatRule].
 */
object ReminderRepeatRule {
    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"

    fun isRepeating(rule: String): Boolean = rule != NONE && rule in setOf(DAILY, WEEKLY, MONTHLY)

    /** Next occurrence in local timezone after [lastFireAtMillis] (exclusive step from that instant). */
    fun computeNextFireAfter(lastFireAtMillis: Long, rule: String): Long? {
        if (!isRepeating(rule)) return null
        val zone = ZoneId.systemDefault()
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastFireAtMillis), zone)
        val next = when (rule) {
            DAILY -> zdt.plusDays(1)
            WEEKLY -> zdt.plusWeeks(1)
            MONTHLY -> zdt.plusMonths(1)
            else -> return null
        }
        return next.toInstant().toEpochMilli()
    }

    /**
     * After a notification at [lastFiredScheduleMillis] (the row's `fireAtMillis` that just rang),
     * returns the next schedule time strictly after [nowMillis].
     */
    fun nextAfterSuccessfulFire(lastFiredScheduleMillis: Long, rule: String, nowMillis: Long): Long? {
        var next = computeNextFireAfter(lastFiredScheduleMillis, rule) ?: return null
        var guard = 0
        while (next <= nowMillis && guard++ < 400) {
            next = computeNextFireAfter(next, rule) ?: return null
        }
        return next
    }
}
