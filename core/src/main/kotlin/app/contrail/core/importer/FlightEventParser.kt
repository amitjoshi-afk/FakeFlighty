package app.contrail.core.importer

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Turns calendar events into flight candidates. Events come from two sources:
 *   DeviceCalendarSource: the phone's calendar (CalendarContract), which the Outlook app
 *     fills when "Sync calendars" is on in Outlook for Android.
 *   IcsSource: an .ics file the user exports from Outlook or gets from an airline email.
 *
 * Airline and booking-tool events usually carry the flight in the subject
 * ("Flight to Tokyo (SQ 638)", "SQ638 SIN-NRT") and airports/times in the body.
 */

data class CalendarEvent(
    val id: String,
    val subject: String,
    val body: String,
    val location: String?,
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    /** IANA zone ids of the start and end, when the source provides them. */
    val startZone: String? = null,
    val endZone: String? = null,
    val allDay: Boolean = false,
)

data class FlightCandidate(
    val sourceEventId: String,
    val airlineCode: String,      // IATA, e.g. "SQ"
    val flightNumber: String,     // digits, e.g. "638"
    val date: LocalDate,          // local departure date
    val origin: String?,          // IATA airport
    val destination: String?,
    val confidence: Confidence,
    /** Calendar start and end; null for all-day entries. */
    val departureUtc: Instant? = null,
    val arrivalUtc: Instant? = null,
    val departureZone: String? = null,
    val arrivalZone: String? = null,
) {
    val designator get() = "$airlineCode$flightNumber"
}

enum class Confidence { HIGH, MEDIUM, LOW }

object FlightEventParser {

    // Two-character IATA airline code (letters or letter+digit), optional space, 1 to 4 digits.
    private val flightNo = Regex("""\b([A-Z]{2}|[A-Z]\d|\d[A-Z])\s?(\d{1,4})\b""")

    // "SIN-NRT", "SIN to NRT", "SIN → NRT"
    private val routeDash = Regex("""\b([A-Z]{3})\s?(?:-|–|→|>|TO)\s?([A-Z]{3})\b""")
    // "Singapore (SIN) ... Tokyo (NRT)"
    private val parenCode = Regex("""\(([A-Z]{3})\)""")

    private val flightWords = Regex("""(?i)\b(flight|fly|depart|departure|boarding|gate|terminal|airline|airways|PNR|booking ref|confirmation)""")

    // Codes that match the airline pattern but are common words/abbreviations in calendars.
    private val airlineStopList = setOf("AM", "PM", "OK", "TV", "ID", "NO", "IN", "ON", "AT", "TO", "UP", "GO", "ME", "US", "Q1", "Q2", "Q3", "Q4")
    private val airportStopList = setOf("THE", "AND", "FOR", "YOU", "ALL", "NEW", "GMT", "UTC", "SGT", "PNR", "REF", "CET", "EST", "PST")

    /**
     * @param isAirport optional check against the airport database. When given, a route is only
     *   accepted if both codes are real airports, which removes most false matches.
     */
    fun parse(event: CalendarEvent, isAirport: ((String) -> Boolean)? = null): FlightCandidate? {
        val text = listOf(event.subject, event.location.orEmpty(), event.body).joinToString("\n")
        val upper = text.uppercase(Locale.ROOT)

        val match = (flightNo.findAll(event.subject.uppercase(Locale.ROOT)) + flightNo.findAll(upper))
            .firstOrNull { it.groupValues[1] !in airlineStopList } ?: return null

        fun ok(code: String) = code !in airportStopList && (isAirport?.invoke(code) ?: true)

        val route = routeDash.findAll(upper).map { it.groupValues[1] to it.groupValues[2] }
            .firstOrNull { (a, b) -> a != b && ok(a) && ok(b) }
            ?: parenCode.findAll(upper).map { it.groupValues[1] }.filter(::ok).distinct().take(2).toList()
                .takeIf { it.size == 2 }?.let { it[0] to it[1] }

        val hasFlightWords = flightWords.containsMatchIn(text)
        val confidence = when {
            route != null && hasFlightWords -> Confidence.HIGH
            route != null || hasFlightWords -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        val timed = !event.allDay
        return FlightCandidate(
            sourceEventId = event.id,
            airlineCode = match.groupValues[1],
            flightNumber = match.groupValues[2].trimStart('0').ifEmpty { "0" },
            date = event.start.toLocalDate(),
            origin = route?.first,
            destination = route?.second,
            confidence = confidence,
            departureUtc = if (timed) event.start.toInstant() else null,
            arrivalUtc = if (timed && event.end.isAfter(event.start)) event.end.toInstant() else null,
            departureZone = event.startZone,
            arrivalZone = event.endZone ?: event.startZone,
        )
    }

    /** LOW candidates are shown to the user for confirmation, never imported silently. */
    fun parseAll(events: List<CalendarEvent>, isAirport: ((String) -> Boolean)? = null): List<FlightCandidate> =
        events.mapNotNull { parse(it, isAirport) }.distinctBy { it.designator to it.date }
}
