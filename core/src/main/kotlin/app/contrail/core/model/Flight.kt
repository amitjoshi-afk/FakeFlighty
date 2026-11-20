package app.contrail.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Serializable
data class Airport(
    val iata: String? = null,
    val icao: String? = null,
    val name: String? = null,
    val city: String? = null,
    /** IANA zone id such as "Asia/Singapore", used to show local times. */
    val timeZone: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val code: String get() = iata ?: icao ?: "???"
    val displayName: String get() = city ?: name ?: code
}

@Serializable
data class Movement(
    val airport: Airport = Airport(),
    @Serializable(InstantSerializer::class) val scheduledUtc: Instant? = null,
    @Serializable(InstantSerializer::class) val revisedUtc: Instant? = null,
    @Serializable(InstantSerializer::class) val predictedUtc: Instant? = null,
    @Serializable(InstantSerializer::class) val runwayUtc: Instant? = null,
    val terminal: String? = null,
    val gate: String? = null,
    val checkInDesk: String? = null,
    val baggageBelt: String? = null,
) {
    /** Best gate time known: revised (actual or airline estimate), then runway, predicted, scheduled. */
    val bestUtc: Instant? get() = revisedUtc ?: runwayUtc ?: predictedUtc ?: scheduledUtc

    /** Minutes late against schedule (negative when early), or null if either time is unknown. */
    val delayMinutes: Long?
        get() {
            val s = scheduledUtc ?: return null
            val b = revisedUtc ?: predictedUtc ?: return null
            return Duration.between(s, b).toMinutes()
        }
}

@Serializable
enum class FlightStatus {
    Unknown, Expected, CheckIn, Boarding, GateClosed, Departed, EnRoute, Delayed,
    Approaching, Arrived, Canceled, CanceledUncertain, Diverted;

    val isFinal get() = this == Arrived || this == Canceled || this == Diverted
    val isAirborne get() = this == Departed || this == EnRoute || this == Approaching

    companion object {
        fun parse(raw: String?): FlightStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Unknown
    }
}

@Serializable
data class Flight(
    /** Stable key: designator plus local departure date, e.g. "SQ638@2026-12-03". */
    val id: String,
    val designator: String,
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val airlineName: String? = null,
    val departure: Movement = Movement(),
    val arrival: Movement = Movement(),
    val status: FlightStatus = FlightStatus.Unknown,
    val aircraftModel: String? = null,
    val aircraftReg: String? = null,
    val distanceKm: Double? = null,
    @Serializable(InstantSerializer::class) val lastUpdatedUtc: Instant? = null,
    /** Calendar event this flight was imported from, if any. */
    val sourceEventId: String? = null,
    /** False until a live status lookup has filled in the details. */
    val hasLiveData: Boolean = false,
) {
    val route: String get() = "${departure.airport.code} → ${arrival.airport.code}"

    /** Whether the flight is over, judged by status or by arrival time. */
    fun isPast(now: Instant): Boolean {
        if (status.isFinal) return true
        val arr = arrival.bestUtc ?: departure.bestUtc?.plus(Duration.ofHours(18))
        return arr != null && arr.isBefore(now)
    }

    companion object {
        fun idFor(designator: String, date: LocalDate) = "${designator.uppercase()}@$date"
    }
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
