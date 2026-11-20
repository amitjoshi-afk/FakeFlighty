package app.contrail.core.model

import app.contrail.core.importer.FlightCandidate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The saved list of flights. Pure logic; the app decides where the JSON lives. */
@Serializable
data class FlightBook(val flights: List<Flight> = emptyList()) {

    fun upsert(f: Flight): FlightBook {
        val existing = flights.firstOrNull { it.id == f.id }
        val merged = if (existing == null) f else f.copy(sourceEventId = f.sourceEventId ?: existing.sourceEventId)
        return copy(flights = flights.filterNot { it.id == f.id } + merged)
    }

    fun remove(id: String) = copy(flights = flights.filterNot { it.id == id })

    fun contains(c: FlightCandidate) = flights.any { it.id == Flight.idFor(c.designator, c.date) }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
        fun fromJson(s: String): FlightBook = runCatching { json.decodeFromString(serializer(), s) }.getOrElse { FlightBook() }

        /** A placeholder flight from a calendar entry, before any live lookup. */
        fun placeholder(c: FlightCandidate) = Flight(
            id = Flight.idFor(c.designator, c.date),
            designator = c.designator,
            date = c.date,
            departure = Movement(airport = Airport(iata = c.origin)),
            arrival = Movement(airport = Airport(iata = c.destination)),
            sourceEventId = c.sourceEventId,
        )
    }
}
