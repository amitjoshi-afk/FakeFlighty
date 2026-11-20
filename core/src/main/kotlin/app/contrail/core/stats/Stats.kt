package app.contrail.core.stats

import app.contrail.core.model.Flight
import app.contrail.core.model.FlightStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class FlightStats(
    val flights: Int,
    val distanceKm: Double,
    val timeInAir: Duration,
    val airports: Int,
    val airlines: Int,
    /** Share of measured flights that arrived no more than 15 minutes late (the usual on-time definition). */
    val onTimeShare: Double?,
    val averageArrivalDelayMin: Double?,
    val longest: Flight?,
    val topRoute: Pair<String, Int>?,
    val topAirport: Pair<String, Int>?,
) {
    /** Earth's equatorial circumference is about 40,075 km. */
    val timesAroundEarth: Double get() = distanceKm / 40_075.0
}

object Stats {
    const val ON_TIME_MIN = 15

    fun years(flights: List<Flight>): List<Int> = flights.map { it.date.year }.distinct().sortedDescending()

    fun compute(all: List<Flight>, now: Instant, year: Int? = null): FlightStats {
        val flown = all.filter {
            it.isPast(now) && it.status != FlightStatus.Canceled && it.status != FlightStatus.CanceledUncertain &&
                (year == null || it.date.year == year)
        }
        val dist = flown.sumOf { distance(it) ?: 0.0 }
        val air = flown.fold(Duration.ZERO) { acc, f -> acc + (duration(f) ?: Duration.ZERO) }
        val airports = flown.flatMap { listOf(it.departure.airport.code, it.arrival.airport.code) }.filter { it != "???" }
        val delays = flown.mapNotNull { it.arrival.delayMinutes }
        val routes = flown.groupingBy { it.route }.eachCount().maxByOrNull { it.value }
        val topAirport = airports.groupingBy { it }.eachCount().maxByOrNull { it.value }
        return FlightStats(
            flights = flown.size,
            distanceKm = dist,
            timeInAir = air,
            airports = airports.toSet().size,
            airlines = flown.map { it.designator.take(2) }.toSet().size,
            onTimeShare = if (delays.isEmpty()) null else delays.count { it <= ON_TIME_MIN }.toDouble() / delays.size,
            averageArrivalDelayMin = if (delays.isEmpty()) null else delays.average(),
            longest = flown.maxByOrNull { distance(it) ?: 0.0 },
            topRoute = routes?.toPair(),
            topAirport = topAirport?.toPair(),
        )
    }

    /** Reported great-circle distance, or haversine from airport coordinates. */
    fun distance(f: Flight): Double? {
        f.distanceKm?.let { return it }
        val a = f.departure.airport; val b = f.arrival.airport
        if (a.lat == null || a.lon == null || b.lat == null || b.lon == null) return null
        return haversineKm(a.lat, a.lon, b.lat, b.lon)
    }

    fun duration(f: Flight): Duration? {
        val a = f.departure.bestUtc ?: return null
        val b = f.arrival.bestUtc ?: return null
        return Duration.between(a, b).takeIf { !it.isNegative }
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // mean Earth radius
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}

/** Flights grouped into trips: a new trip starts after a gap of more than [gapHours] on the ground. */
data class Trip(val flights: List<Flight>) {
    val start: LocalDate get() = flights.first().date
    val end: LocalDate get() = flights.last().date
    val title: String get() = flights.last().arrival.airport.displayName.let { "Trip to $it" }
}

object Trips {
    fun group(flights: List<Flight>, gapHours: Long = 36): List<Trip> {
        val sorted = flights.sortedBy { it.departure.bestUtc ?: it.date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC) }
        val trips = mutableListOf<MutableList<Flight>>()
        for (f in sorted) {
            val last = trips.lastOrNull()?.last()
            val lastArr = last?.arrival?.bestUtc
            val dep = f.departure.bestUtc
            val sameTrip = last != null && (
                if (lastArr != null && dep != null) Duration.between(lastArr, dep).toHours() <= gapHours
                else !f.date.isAfter(last.date.plusDays(1))
            )
            if (sameTrip) trips.last() += f else trips += mutableListOf(f)
        }
        return trips.map { Trip(it) }
    }
}
