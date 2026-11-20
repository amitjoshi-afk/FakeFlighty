package app.contrail.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.contrail.core.importer.CalendarEvent
import java.time.Instant
import java.time.ZoneId

/**
 * Reads events from the phone's calendar provider. Outlook for Android writes its events here
 * when the user turns on Settings > (account) > Sync calendars. Some company policies (Intune)
 * turn that switch off; in that case this returns nothing and the app offers .ics import instead.
 *
 * Needs <uses-permission android:name="android.permission.READ_CALENDAR" /> and a runtime request.
 */
class DeviceCalendarSource(private val context: Context) {

    data class DeviceCalendar(val id: Long, val name: String, val account: String, val accountType: String) {
        /** Guess for Outlook-synced calendars; confirm the exact account type on a real device. */
        val isOutlook get() = accountType.contains("microsoft", ignoreCase = true)
    }

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        return context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        DeviceCalendar(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty())
                    )
                }
            }.orEmpty()
    }

    /**
     * Uses the Instances table so recurring events are expanded and times are already resolved.
     * Default window: one year back (for stats) and one year ahead (for upcoming trips).
     */
    fun events(
        calendarIds: Set<Long>,
        from: Instant = Instant.now().minusSeconds(365L * 86_400),
        to: Instant = Instant.now().plusSeconds(365L * 86_400),
    ): List<CalendarEvent> {
        if (!hasPermission() || calendarIds.isEmpty()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from.toEpochMilli())
            android.content.ContentUris.appendId(it, to.toEpochMilli())
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val zone = ZoneId.systemDefault()
        return query(context.contentResolver, uri, cols)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (c.getLong(7) !in calendarIds) continue
                    val tz = c.getString(6)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                    add(
                        CalendarEvent(
                            id = "device-${c.getLong(0)}-${c.getLong(4)}",
                            subject = c.getString(1).orEmpty(),
                            body = c.getString(2).orEmpty(),
                            location = c.getString(3),
                            start = Instant.ofEpochMilli(c.getLong(4)).atZone(tz).toOffsetDateTime(),
                            end = Instant.ofEpochMilli(c.getLong(5)).atZone(tz).toOffsetDateTime(),
                        )
                    )
                }
            }
        }.orEmpty()
    }

    private fun query(cr: ContentResolver, uri: android.net.Uri, cols: Array<String>) =
        cr.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")
}
