package app.contrail.ui

import app.contrail.core.model.Airport
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

fun Airport.zone(): ZoneId = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

/** Clock time at the airport, which is what boarding passes and departure boards show. */
fun Instant?.localAt(a: Airport): String = this?.atZone(a.zone())?.format(hm) ?: "--:--"

fun LocalDate.day(): String = format(dayFmt)
fun LocalDate.dayYear(): String = format(dayYearFmt)

fun Duration.hm(): String {
    val h = toHours(); val m = toMinutes() % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun relative(target: Instant?, now: Instant): String? {
    target ?: return null
    val d = Duration.between(now, target)
    if (d.isNegative) return null
    return when {
        d.toMinutes() < 60 -> "in ${d.toMinutes()}m"
        d.toHours() < 48 -> "in ${d.hm()}"
        else -> "in ${d.toDays()} days"
    }
}

fun km(v: Double): String = String.format(Locale.US, "%,.0f km", v)
