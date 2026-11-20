package app.contrail.core.aerodatabox

import app.contrail.core.model.Airport
import app.contrail.core.model.Flight
import app.contrail.core.model.FlightStatus
import app.contrail.core.model.Movement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * AeroDataBox flight status lookup:
 *   GET {base}/flights/number/{flightNumber}/{dateLocal}
 * The response is a JSON array of flights (several entries for codeshares or multi-leg flights),
 * or HTTP 204 when nothing matches.
 *
 * AeroDataBox is sold through two marketplaces with different hosts and key headers.
 */
enum class AdbProvider(val label: String, val baseUrl: String) {
    RapidApi("RapidAPI", "https://aerodatabox.p.rapidapi.com"),
    ApiMarket("API.market", "https://prod.api.market/api/v1/aedbx/aerodatabox");

    fun headers(key: String): Map<String, String> = when (this) {
        RapidApi -> mapOf("X-RapidAPI-Key" to key, "X-RapidAPI-Host" to "aerodatabox.p.rapidapi.com")
        ApiMarket -> mapOf("x-api-market-key" to key)
    }
}

data class HttpResult(val code: Int, val body: String)

/** Kept as an interface so the core module has no HTTP library; the app plugs in OkHttp. */
fun interface HttpGet {
    fun get(url: String, headers: Map<String, String>): HttpResult
}

class AeroDataBoxException(message: String) : Exception(message)

class AeroDataBoxClient(
    private val provider: AdbProvider,
    private val apiKey: String,
    private val http: HttpGet,
) {
    /** Returns all legs AeroDataBox knows for this flight number on this local date. */
    fun lookup(designator: String, date: LocalDate): List<Flight> {
        if (apiKey.isBlank()) throw AeroDataBoxException("Add your AeroDataBox key in Settings first.")
        val number = URLEncoder.encode(designator.replace(" ", ""), "UTF-8")
        val url = "${provider.baseUrl}/flights/number/$number/$date?withAircraftImage=false&withLocation=false"
        val res = http.get(url, provider.headers(apiKey) + ("Accept" to "application/json"))
        return when (res.code) {
            200 -> AdbMapper.parse(res.body).map { AdbMapper.toFlight(it, designator, date) }
            204 -> emptyList()
            401, 403 -> throw AeroDataBoxException("AeroDataBox rejected the key (HTTP ${res.code}). Check the key and provider in Settings.")
            429 -> throw AeroDataBoxException("AeroDataBox rate limit reached. Try again later.")
            else -> throw AeroDataBoxException("AeroDataBox returned HTTP ${res.code}.")
        }
    }

    /** Picks the leg that matches the expected origin, falling back to the first leg. */
    fun status(designator: String, date: LocalDate, expectedOrigin: String? = null): Flight? {
        val legs = lookup(designator, date)
        return legs.firstOrNull { expectedOrigin != null && it.departure.airport.iata == expectedOrigin } ?: legs.firstOrNull()
    }
}

// ---- Wire format. Every field is optional so schema additions or gaps never break parsing. ----

@Serializable data class AdbTime(val utc: String? = null, val local: String? = null)
@Serializable data class AdbLocation(val lat: Double? = null, val lon: Double? = null)
@Serializable data class AdbAirport(
    val icao: String? = null, val iata: String? = null, val name: String? = null,
    val shortName: String? = null, val municipalityName: String? = null,
    val timeZone: String? = null, val location: AdbLocation? = null,
)
@Serializable data class AdbMovement(
    val airport: AdbAirport? = null,
    val scheduledTime: AdbTime? = null, val revisedTime: AdbTime? = null,
    val predictedTime: AdbTime? = null, val runwayTime: AdbTime? = null,
    val terminal: String? = null, val checkInDesk: String? = null,
    val gate: String? = null, val baggageBelt: String? = null,
)
@Serializable data class AdbAircraft(val reg: String? = null, val model: String? = null)
@Serializable data class AdbAirline(val name: String? = null, val iata: String? = null, val icao: String? = null)
@Serializable data class AdbDistance(val km: Double? = null)
@Serializable data class AdbFlight(
    val number: String? = null, val status: String? = null,
    val departure: AdbMovement? = null, val arrival: AdbMovement? = null,
    val aircraft: AdbAircraft? = null, val airline: AdbAirline? = null,
    val greatCircleDistance: AdbDistance? = null, val lastUpdatedUtc: String? = null,
    val codeshareStatus: String? = null,
)

object AdbMapper {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(body: String): List<AdbFlight> = json.decodeFromString(body)

    /** Accepts "2026-12-03 09:15Z", "2026-12-03T09:15:00Z", "2026-12-03 17:15+08:00" and similar. */
    fun parseTime(raw: String?): Instant? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T') ?: return null
        return runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(s) }.getOrNull()
    }

    private fun AdbAirport?.toAirport() = Airport(
        iata = this?.iata, icao = this?.icao, name = this?.name,
        city = this?.municipalityName ?: this?.shortName, timeZone = this?.timeZone,
        lat = this?.location?.lat, lon = this?.location?.lon,
    )

    private fun AdbMovement?.toMovement() = Movement(
        airport = this?.airport.toAirport(),
        scheduledUtc = parseTime(this?.scheduledTime?.utc),
        revisedUtc = parseTime(this?.revisedTime?.utc),
        predictedUtc = parseTime(this?.predictedTime?.utc),
        runwayUtc = parseTime(this?.runwayTime?.utc),
        terminal = this?.terminal, gate = this?.gate,
        checkInDesk = this?.checkInDesk, baggageBelt = this?.baggageBelt,
    )

    fun toFlight(a: AdbFlight, designator: String, date: LocalDate): Flight {
        val code = designator.replace(" ", "").uppercase()
        return Flight(
            id = Flight.idFor(code, date),
            designator = code,
            date = date,
            airlineName = a.airline?.name,
            departure = a.departure.toMovement(),
            arrival = a.arrival.toMovement(),
            status = FlightStatus.parse(a.status),
            aircraftModel = a.aircraft?.model,
            aircraftReg = a.aircraft?.reg,
            distanceKm = a.greatCircleDistance?.km,
            lastUpdatedUtc = parseTime(a.lastUpdatedUtc),
            hasLiveData = true,
        )
    }
}
