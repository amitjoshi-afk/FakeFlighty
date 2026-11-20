package app.contrail.core.model

import app.contrail.core.importer.FlightCandidate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The saved list of flights. Pure logic; the app decides where the JSON lives. */
@Serializable
data class FlightBook(val flights: List<Flight> = emptyList()) {

    fun upsert(f: Flight): FlightBook = copy(flights = flights.filterNot { it.id == f.id } + f)

    /** Replaces a flight whose id may have changed (an edit of its number or date). */
    fun replace(oldId: String, f: Flight): FlightBook = copy(flights = flights.filterNot { it.id == oldId || it.id == f.id } + f)

    fun remove(id: String) = copy(flights = flights.filterNot { it.id == id })

    fun contains(c: FlightCandidate) = flights.any { it.id == Flight.idFor(c.designator, c.date) }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
        fun fromJson(s: String): FlightBook = runCatching { json.decodeFromString(serializer(), s) }.getOrElse { FlightBook() }

        /** A flight built from a calendar entry or manual entry. */
        fun fromCandidate(c: FlightCandidate) = Flight(
            id = Flight.idFor(c.designator, c.date),
            designator = c.designator,
            date = c.date,
            origin = c.origin,
            destination = c.destination,
            departureUtc = c.departureUtc,
            arrivalUtc = c.arrivalUtc,
            departureZone = c.departureZone,
            arrivalZone = c.arrivalZone,
            sourceEventId = c.sourceEventId,
        )
    }
}
