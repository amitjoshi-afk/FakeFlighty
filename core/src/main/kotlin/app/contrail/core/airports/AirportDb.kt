package app.contrail.core.airports

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AirportInfo(
    val iata: String,
    val icao: String,
    val name: String,
    val city: String,
    /** ISO 3166-1 alpha-2 country code, e.g. "SG". */
    val country: String,
    val countryName: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Offline airport lookup by IATA code, built from OurAirports data (public domain, ourairports.com).
 * The app ships a trimmed CSV in assets/airports.csv:
 *   iata,icao,name,city,country,country_name,lat,lon
 */
class AirportDb(private val byIata: Map<String, AirportInfo>) {

    operator fun get(iata: String?): AirportInfo? = iata?.let { byIata[it.uppercase()] }

    fun contains(iata: String?) = get(iata) != null

    val size get() = byIata.size

    /** Great-circle distance in km between two airports, or null if either is unknown. */
    fun distanceKm(from: String?, to: String?): Double? {
        val a = get(from) ?: return null
        val b = get(to) ?: return null
        return haversineKm(a.lat, a.lon, b.lat, b.lon)
    }

    companion object {
        val EMPTY = AirportDb(emptyMap())

        fun parse(csv: String): AirportDb {
            val map = HashMap<String, AirportInfo>(8192)
            csv.lineSequence().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val f = splitCsv(line)
                if (f.size < 8) return@forEach
                val lat = f[6].toDoubleOrNull() ?: return@forEach
                val lon = f[7].toDoubleOrNull() ?: return@forEach
                map[f[0]] = AirportInfo(f[0], f[1], f[2], f[3], f[4], f[5], lat, lon)
            }
            return AirportDb(map)
        }

        /** Minimal RFC 4180 field splitter: handles quoted fields with commas and doubled quotes. */
        fun splitCsv(line: String): List<String> {
            val out = mutableListOf<String>()
            val sb = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> { out += sb.toString(); sb.clear() }
                    else -> sb.append(c)
                }
                i++
            }
            out += sb.toString()
            return out
        }

        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0 // mean Earth radius in km
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(h))
        }
    }
}
