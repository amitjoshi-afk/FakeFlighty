package app.contrail.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

fun zoneOrDefault(id: String?): ZoneId = id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

/** Clock time in the zone the calendar entry was written in, which normally matches the boarding pass. */
fun Instant?.clock(zone: String?): String = this?.atZone(zoneOrDefault(zone))?.format(hm) ?: "--:--"

fun LocalDate.day(): String = format(dayFmt)
fun LocalDate.dayYear(): String = format(dayYearFmt)
fun LocalDate.monthYear(): String = format(monthFmt)

fun Duration.hm(): String {
    val h = toHours(); val m = toMinutes() % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Large durations as days and hours, e.g. "3d 4h". */
fun Duration.dh(): String {
    val d = toDays(); val h = toHours() % 24
    return if (d > 0) "${d}d ${h}h" else hm()
}

fun km(v: Double): String = String.format(Locale.US, "%,.0f km", v)
