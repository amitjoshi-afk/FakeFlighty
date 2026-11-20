package app.contrail.core.stats

import app.contrail.core.airports.AirportDb
import app.contrail.core.model.Flight
import java.time.Duration
import java.time.LocalDate

data class Ranked(val key: String, val count: Int)

data class FlightStats(
    val flights: Int,
    val distanceKm: Double,
    /** Flights whose distance could be computed (both airports known). */
    val flightsWithDistance: Int,
    /** Sum of calendar durations. Only flights with plausible start and end times count. */
    val timeInAir: Duration,
    val flightsWithTime: Int,
    val airports: Int,
    val countries: List<String>,
    val airlines: Int,
    val international: Int,
    val longest: Pair<Flight, Double>?,
    val shortest: Pair<Flight, Double>?,
    val topRoutes: List<Ranked>,
    val topAirports: List<Ranked>,
    val topAirlines: List<Ranked>,
    val perYear: List<Pair<Int, Int>>,
    val firstFlight: Flight?,
) {
    /** Earth's equatorial circumference is about 40,075 km. */
    val timesAroundEarth: Double get() = distanceKm / 40_075.0
    /** Average distance from the Earth to the Moon is about 384,400 km. */
    val shareToMoon: Double get() = distanceKm / 384_400.0
}

object Stats {

    fun years(flights: List<Flight>): List<Int> = flights.map { it.date.year }.distinct().sortedDescending()

    /** @param flown flights that have already happened (the caller filters out future ones). */
    fun compute(flown: List<Flight>, db: AirportDb, year: Int? = null): FlightStats {
        val list = flown.filter { year == null || it.date.year == year }
        val withDist = list.mapNotNull { f -> db.distanceKm(f.origin, f.destination)?.let { f to it } }
        val withTime = list.mapNotNull { it.duration }
        val airportCodes = list.flatMap { listOfNotNull(it.origin, it.destination) }
        val countries = airportCodes.mapNotNull { db[it]?.countryName }.distinct().sorted()
        val international = list.count { f ->
            val a = db[f.origin]?.country; val b = db[f.destination]?.country
            a != null && b != null && a != b
        }
        fun rank(keys: List<String>, n: Int = 5) =
            keys.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(n).map { Ranked(it.key, it.value) }

        return FlightStats(
            flights = list.size,
            distanceKm = withDist.sumOf { it.second },
            flightsWithDistance = withDist.size,
            timeInAir = withTime.fold(Duration.ZERO, Duration::plus),
            flightsWithTime = withTime.size,
            airports = airportCodes.toSet().size,
            countries = countries,
            airlines = list.map { it.airlineCode }.toSet().size,
            international = international,
            longest = withDist.maxByOrNull { it.second },
            shortest = withDist.minByOrNull { it.second },
            topRoutes = rank(list.filter { it.hasRoute }.map { routeKey(it) }),
            topAirports = rank(airportCodes),
            topAirlines = rank(list.map { it.airlineCode }),
            perYear = flown.groupingBy { it.date.year }.eachCount().toSortedMap().map { it.key to it.value },
            firstFlight = list.minByOrNull { it.date },
        )
    }

    /** Treats SIN→NRT and NRT→SIN as the same route. */
    fun routeKey(f: Flight): String = listOf(f.origin!!, f.destination!!).sorted().joinToString(" ⇄ ")
}

/** Flights grouped into trips: a new trip starts after more than [gapDays] days at home or away. */
data class Trip(val flights: List<Flight>) {
    val start: LocalDate get() = flights.first().date
    val end: LocalDate get() = flights.last().date
}

object Trips {
    fun group(flights: List<Flight>, gapDays: Long = 2): List<Trip> {
        val sorted = flights.sortedWith(compareBy<Flight> { it.date }.thenBy { it.departureUtc })
        val trips = mutableListOf<MutableList<Flight>>()
        for (f in sorted) {
            val last = trips.lastOrNull()?.last()
            // Consecutive flights belong together if they connect (same airport) within a few days,
            // or simply happen within the gap.
            val connects = last != null && last.destination != null && last.destination == f.origin
            val close = last != null && !f.date.isAfter(last.date.plusDays(gapDays))
            if (last != null && (close || (connects && !f.date.isAfter(last.date.plusDays(gapDays * 2))))) trips.last() += f
            else trips += mutableListOf(f)
        }
        return trips.map { Trip(it) }
    }

    /** "Trip to Tokyo": the farthest airport from the starting point, or the last destination. */
    fun title(t: Trip, db: AirportDb): String {
        val start = t.flights.first().origin
        val far = t.flights.mapNotNull { it.destination }.maxByOrNull { db.distanceKm(start, it) ?: 0.0 }
        val code = far ?: t.flights.last().destination
        val place = db[code]?.city?.takeIf { it.isNotBlank() } ?: code
        return place?.let { "Trip to $it" } ?: "Trip"
    }
}
