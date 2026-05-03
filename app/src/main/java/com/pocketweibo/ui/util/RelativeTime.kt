package com.pocketweibo.ui.util

import android.content.res.Resources
import com.pocketweibo.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RelativeTimePreset {
    FeedCard,
    MessageList,
    PostDetail,
    CommentSheet,
}

private fun Resources.appLocale(): Locale = configuration.locales[0] ?: Locale.getDefault()

fun Resources.formatRelativeTime(timestamp: Long, preset: RelativeTimePreset): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val sec = diff / 1000
    val min = sec / 60
    val h = min / 60
    val d = h / 24
    return when (preset) {
        RelativeTimePreset.FeedCard -> when {
            sec < 60 -> getString(R.string.time_just_now)
            min < 60 -> getString(R.string.time_minutes_ago, min.toInt())
            h < 12 -> getString(R.string.time_hours_ago, h.toInt())
            else -> SimpleDateFormat(getString(R.string.time_full_format), appLocale()).format(Date(timestamp))
        }
        RelativeTimePreset.MessageList -> when {
            min < 1 -> getString(R.string.time_just_now)
            min < 60 -> getString(R.string.time_minutes_ago, min.toInt())
            h < 24 -> getString(R.string.time_hours_ago, h.toInt())
            d < 7 -> getString(R.string.time_days_ago, d.toInt())
            else -> SimpleDateFormat(getString(R.string.time_message_list_past), appLocale()).format(Date(timestamp))
        }
        RelativeTimePreset.PostDetail -> when {
            sec < 60 -> getString(R.string.time_just_now)
            min < 60 -> getString(R.string.time_minutes_ago, min.toInt())
            h < 24 -> getString(R.string.time_hours_ago, h.toInt())
            d < 7 -> getString(R.string.time_days_ago, d.toInt())
            else -> SimpleDateFormat(getString(R.string.time_full_format), appLocale()).format(Date(timestamp))
        }
        RelativeTimePreset.CommentSheet -> when {
            min < 1 -> getString(R.string.time_just_now)
            min < 60 -> getString(R.string.time_minutes_ago, min.toInt())
            h < 24 -> getString(R.string.time_hours_ago, h.toInt())
            d < 7 -> getString(R.string.time_days_ago, d.toInt())
            else -> SimpleDateFormat(getString(R.string.time_short_format), appLocale()).format(Date(timestamp))
        }
    }
}
