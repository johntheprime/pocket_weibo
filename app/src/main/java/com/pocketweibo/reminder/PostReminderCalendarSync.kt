package com.pocketweibo.reminder

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.pocketweibo.diagnostic.DiagnosticLog
import java.util.TimeZone

/**
 * Writes post reminders into the system calendar so the OS can remind the user even when
 * PocketWeibo is not running. Events are created with the local reminder time and an RRULE
 * matching [ReminderRepeatRule].
 */
object PostReminderCalendarSync {

    private const val TAG = "PW_CalendarSync"

    /** Calendar events are kept short; the alert fires at the event start. */
    const val EVENT_DURATION_MILLIS: Long = 30L * 60L * 1000L

    data class EventRef(val calendarId: Long, val eventId: Long)

    fun hasPermissions(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Prefer the primary writable calendar; otherwise the first writable one. */
    fun findWritableCalendarId(context: Context): Long? {
        if (!hasPermissions(context)) return null
        val withPrimary = runCatching {
            queryWritableCalendar(
                context = context,
                sortOrder = "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
                    "${CalendarContract.Calendars.VISIBLE} DESC, " +
                    "${CalendarContract.Calendars._ID} ASC",
            )
        }.getOrNull()
        if (withPrimary != null) return withPrimary
        // Some OEM providers expose a reduced Calendars schema; retry without IS_PRIMARY.
        return runCatching {
            queryWritableCalendar(
                context = context,
                sortOrder = "${CalendarContract.Calendars.VISIBLE} DESC, " +
                    "${CalendarContract.Calendars._ID} ASC",
            )
        }.getOrNull().also { id ->
            if (id == null) DiagnosticLog.w(TAG, "no writable calendar found")
        }
    }

    private fun queryWritableCalendar(context: Context, sortOrder: String): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /**
     * Inserts an event plus a start-time alert into [calendarId].
     * Returns `null` when the provider rejects the write (permission revoked, no calendar, …).
     */
    fun createEvent(
        context: Context,
        calendarId: Long,
        title: String,
        description: String,
        startMillis: Long,
        repeatRule: String,
    ): EventRef? {
        if (!hasPermissions(context)) return null
        val rrule = ReminderRepeatRule.toCalendarRrule(repeatRule)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + EVENT_DURATION_MILLIS)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            if (rrule != null) put(CalendarContract.Events.RRULE, rrule)
        }
        return try {
            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                values,
            ) ?: return null
            val eventId = ContentUris.parseId(uri)
            insertStartAlert(context, eventId)
            DiagnosticLog.d(
                TAG,
                "created event eventId=$eventId calendarId=$calendarId repeat=${rrule ?: "NONE"}",
            )
            EventRef(calendarId = calendarId, eventId = eventId)
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "createEvent failed calendarId=$calendarId", e)
            null
        }
    }

    fun deleteEvent(context: Context, eventId: Long) {
        if (!hasPermissions(context)) return
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val deleted = context.contentResolver.delete(uri, null, null)
            DiagnosticLog.d(TAG, "delete event eventId=$eventId deleted=$deleted")
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "deleteEvent failed eventId=$eventId", e)
        }
    }

    private fun insertStartAlert(context: Context, eventId: Long) {
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        try {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
        } catch (e: Exception) {
            // Event still exists and has HAS_ALARM=1; provider will use its default alert.
            DiagnosticLog.w(TAG, "insert alert failed eventId=$eventId: ${e.message}")
        }
    }
}
