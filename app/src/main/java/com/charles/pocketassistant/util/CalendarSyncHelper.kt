package com.charles.pocketassistant.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs appointments and reminders to the device's default calendar.
 * Requires READ_CALENDAR and WRITE_CALENDAR permissions.
 */
@Singleton
class CalendarSyncHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "CalendarSync"
    }

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the primary calendar ID for insertion.
     * Returns the first writable local calendar, or null if none found.
     */
    private fun getPrimaryCalendarId(): Long? {
        if (!hasCalendarPermission()) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            }
        }
        return null
    }

    /**
     * Insert an event into the device calendar.
     *
     * @param title Event title
     * @param description Event description
     * @param startMillis Start time in milliseconds
     * @param endMillis End time (defaults to start + 1 hour)
     * @param location Optional location string
     * @param addReminder Whether to add a 15-minute reminder
     * @return The calendar event ID, or null if insertion failed
     */
    fun insertEvent(
        title: String,
        description: String = "",
        startMillis: Long,
        endMillis: Long = startMillis + 3_600_000L,
        location: String? = null,
        addReminder: Boolean = true
    ): Long? {
        val calendarId = getPrimaryCalendarId()
        if (calendarId == null) {
            Log.w(TAG, "No writable calendar found")
            return null
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (!location.isNullOrBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, location)
            }
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.let { ContentUris.parseId(it) }
            Log.d(TAG, "Inserted calendar event: id=$eventId title=$title")

            if (addReminder && eventId != null) {
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 15)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
            eventId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            null
        }
    }

    /**
     * Insert a bill due-date reminder as an all-day event.
     */
    fun insertBillReminder(
        vendor: String,
        amount: String,
        dueDateMillis: Long
    ): Long? {
        val title = if (amount.isNotBlank()) "$vendor bill due ($amount)" else "$vendor bill due"
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, getPrimaryCalendarId() ?: return null)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, "Bill tracked by Pocket Assistant")
            put(CalendarContract.Events.DTSTART, dueDateMillis)
            put(CalendarContract.Events.DTEND, dueDateMillis + 86_400_000L)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.let { ContentUris.parseId(it) }
            if (eventId != null) {
                // Add a reminder 1 day before
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 24 * 60)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
            Log.d(TAG, "Inserted bill reminder: id=$eventId title=$title")
            eventId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert bill reminder", e)
            null
        }
    }
}
