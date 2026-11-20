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

/**
 * One flight in the user's history. Everything comes from their own calendar or manual entry;
 * airport names, countries and distance are looked up offline in [app.contrail.core.airports.AirportDb].
 */
@Serializable
data class Flight(
    /** Stable key: designator plus departure date, e.g. "SQ638@2026-12-03". */
    val id: String,
    val designator: String,
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    /** IATA airport codes. Null when the calendar entry didn't name them; the user can fill them in. */
    val origin: String? = null,
    val destination: String? = null,
    /** From the calendar event's start and end. Null for all-day entries or manual entries without times. */
    @Serializable(InstantSerializer::class) val departureUtc: Instant? = null,
    @Serializable(InstantSerializer::class) val arrivalUtc: Instant? = null,
    /** IANA zone ids from the calendar event, used to show times as they appeared on the boarding pass. */
    val departureZone: String? = null,
    val arrivalZone: String? = null,
    val sourceEventId: String? = null,
) {
    val airlineCode: String get() = designator.take(2)
    val route: String get() = "${origin ?: "???"} → ${destination ?: "???"}"
    val hasRoute: Boolean get() = origin != null && destination != null

    /** Gate-to-gate time from the calendar entry. Ignores implausible values (negative or over 22 hours). */
    val duration: Duration?
        get() {
            val a = departureUtc ?: return null
            val b = arrivalUtc ?: return null
            return Duration.between(a, b).takeIf { !it.isNegative && !it.isZero && it.toHours() <= 22 }
        }

    fun isPast(now: Instant, today: LocalDate): Boolean =
        (arrivalUtc ?: departureUtc)?.isBefore(now) ?: date.isBefore(today)

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
