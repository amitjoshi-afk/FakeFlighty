package app.contrail.core.importer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Minimal iCalendar (RFC 5545) reader for flight events.
 * Handles line folding, escaped text, UTC times ("...Z"), TZID times, and all-day dates.
 * Windows zone names that Outlook writes (e.g. "Singapore Standard Time") fall back to [defaultZone].
 */
object IcsSource {

    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun read(ics: String, defaultZone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> {
        val lines = unfold(ics)
        val events = mutableListOf<CalendarEvent>()
        var props: MutableMap<String, Pair<Map<String, String>, String>>? = null

        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> props = mutableMapOf()
                line == "END:VEVENT" -> {
                    props?.let { toEvent(it, defaultZone)?.let(events::add) }
                    props = null
                }
                props != null -> {
                    val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
                    val head = line.substring(0, colon).split(';')
                    val params = head.drop(1).mapNotNull {
                        val eq = it.indexOf('=')
                        if (eq > 0) it.substring(0, eq).uppercase() to it.substring(eq + 1).trim('"') else null
                    }.toMap()
                    props.putIfAbsent(head[0].uppercase(), params to line.substring(colon + 1))
                }
            }
        }
        return events
    }

    private fun unfold(ics: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in ics.replace("\r\n", "\n").split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else if (raw.isNotEmpty()) out += raw
        }
        return out
    }

    private fun unescape(s: String) =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun toEvent(p: Map<String, Pair<Map<String, String>, String>>, zone: ZoneId): CalendarEvent? {
        val start = p["DTSTART"]?.let { time(it, zone) } ?: return null
        val end = p["DTEND"]?.let { time(it, zone) } ?: start
        return CalendarEvent(
            id = p["UID"]?.second ?: "ics-${start.toEpochSecond()}",
            subject = unescape(p["SUMMARY"]?.second.orEmpty()),
            body = unescape(p["DESCRIPTION"]?.second.orEmpty()),
            location = p["LOCATION"]?.second?.let(::unescape),
            start = start,
            end = end,
        )
    }

    private fun time(prop: Pair<Map<String, String>, String>, fallback: ZoneId): OffsetDateTime? {
        val (params, value) = prop
        return runCatching {
            when {
                params["VALUE"] == "DATE" || value.length == 8 ->
                    LocalDate.parse(value, dateOnly).atStartOfDay(fallback).toOffsetDateTime()
                value.endsWith("Z") ->
                    LocalDateTime.parse(value.dropLast(1), dateTime).atOffset(ZoneOffset.UTC)
                else -> {
                    val zone = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: fallback
                    LocalDateTime.parse(value, dateTime).atZone(zone).toOffsetDateTime()
                }
            }
        }.getOrNull()
    }
}
