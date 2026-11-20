package app.contrail.core.importer

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
)

data class FlightCandidate(
    val sourceEventId: String,
    val airlineCode: String,      // IATA, e.g. "SQ"
    val flightNumber: String,     // digits, e.g. "638"
    val date: LocalDate,          // local departure date, used for the status API lookup
    val origin: String?,          // IATA airport, may be filled later by the status API
    val destination: String?,
    val confidence: Confidence,
) {
    val designator get() = "$airlineCode$flightNumber"
}

enum class Confidence { HIGH, MEDIUM, LOW }

object FlightEventParser {

    // Two-character IATA airline code (letters or letter+digit), optional space, 1 to 4 digits.
    private val flightNo = Regex("""\b([A-Z]{2}|[A-Z]\d|\d[A-Z])\s?(\d{1,4})\b""")

    // "SIN-NRT", "SIN to NRT", "SIN → NRT", "(SIN) ... (NRT)"
    private val routeDash = Regex("""\b([A-Z]{3})\s?(?:-|–|→|to)\s?([A-Z]{3})\b""")
    private val routeParens = Regex("""\(([A-Z]{3})\).{0,80}?\(([A-Z]{3})\)""", RegexOption.DOT_MATCHES_ALL)

    private val flightWords = Regex("""(?i)\b(flight|depart|departure|boarding|gate|terminal|airline|PNR|booking ref)""")

    // Codes that match the airline pattern but are common words/abbreviations in calendars.
    private val airlineStopList = setOf("AM", "PM", "OK", "TV", "ID", "NO", "IN", "ON", "AT", "TO", "UP", "GO", "ME", "US")
    private val airportStopList = setOf("THE", "AND", "FOR", "YOU", "ALL", "NEW", "GMT", "UTC", "SGT", "PNR", "REF")

    fun parse(event: CalendarEvent): FlightCandidate? {
        val text = listOf(event.subject, event.location.orEmpty(), event.body).joinToString("\n")
        val upper = text.uppercase(Locale.ROOT)

        val match = (flightNo.findAll(event.subject.uppercase(Locale.ROOT)) + flightNo.findAll(upper))
            .firstOrNull { it.groupValues[1] !in airlineStopList } ?: return null

        val route = (routeDash.find(upper) ?: routeParens.find(upper))
            ?.let { it.groupValues[1] to it.groupValues[2] }
            ?.takeIf { (a, b) -> a != b && a !in airportStopList && b !in airportStopList }

        val hasFlightWords = flightWords.containsMatchIn(text)
        val confidence = when {
            route != null && hasFlightWords -> Confidence.HIGH
            route != null || hasFlightWords -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        return FlightCandidate(
            sourceEventId = event.id,
            airlineCode = match.groupValues[1],
            flightNumber = match.groupValues[2].trimStart('0').ifEmpty { "0" },
            date = event.start.toLocalDate(),
            origin = route?.first,
            destination = route?.second,
            confidence = confidence,
        )
    }

    /** LOW candidates are shown to the user for confirmation, never imported silently. */
    fun parseAll(events: List<CalendarEvent>): List<FlightCandidate> =
        events.mapNotNull(::parse).distinctBy { it.designator to it.date }
}
