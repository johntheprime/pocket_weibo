package com.pocketweibo.ui.util

import android.content.res.Resources
import com.pocketweibo.R
import com.pocketweibo.reminder.ReminderRepeatRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Resources.appLocale(): Locale = configuration.locales[0] ?: Locale.getDefault()

fun Resources.formatReminderFireAt(fireAtMillis: Long): String {
    val fmt = SimpleDateFormat(getString(R.string.reminder_fire_at_pattern), appLocale())
    return fmt.format(Date(fireAtMillis))
}

/** Short repeat label ("daily", "weekdays", …) or `null` for a one-shot reminder. */
fun Resources.reminderRepeatShortLabel(rule: String): String? = when (rule) {
    ReminderRepeatRule.DAILY -> getString(R.string.reminder_repeat_short_daily)
    ReminderRepeatRule.WORKDAYS -> getString(R.string.reminder_repeat_short_workdays)
    ReminderRepeatRule.WEEKLY -> getString(R.string.reminder_repeat_short_weekly)
    ReminderRepeatRule.MONTHLY -> getString(R.string.reminder_repeat_short_monthly)
    else -> null
}

fun Resources.formatTimeUntilFire(fireAtMillis: Long, nowMillis: Long): String {
    val rem = fireAtMillis - nowMillis
    if (rem <= 0L) return getString(R.string.reminder_due_soon)
    if (rem < 60_000L) return getString(R.string.reminder_in_under_minute)
    val totalMin = rem / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> getQuantityString(R.plurals.reminder_in_minutes_future, m.toInt(), m.toInt())
        m == 0L -> getQuantityString(R.plurals.reminder_in_hours_future, h.toInt(), h.toInt())
        else -> getString(R.string.reminder_in_mixed_future, h.toInt(), m.toInt())
    }
}
