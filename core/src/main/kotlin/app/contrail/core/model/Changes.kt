package app.contrail.core.model

import kotlin.math.abs

/** A change worth a notification, found by comparing two snapshots of the same flight. */
data class FlightChange(val flightId: String, val title: String, val message: String)

object ChangeDetector {
    /** Only delay shifts of at least this many minutes trigger an alert, to avoid noise. */
    const val DELAY_THRESHOLD_MIN = 10L

    fun diff(old: Flight, new: Flight): List<FlightChange> {
        if (!old.hasLiveData) return emptyList() // first lookup is not a "change"
        val out = mutableListOf<FlightChange>()
        fun add(title: String, msg: String) { out += FlightChange(new.id, "${new.designator} $title", msg) }

        if (old.status != new.status) when (new.status) {
            FlightStatus.Canceled -> add("canceled", "${new.route} has been canceled.")
            FlightStatus.Diverted -> add("diverted", "${new.route} has been diverted.")
            FlightStatus.Boarding -> add("is boarding", new.departure.gate?.let { "Boarding at gate $it." } ?: "Boarding has started.")
            FlightStatus.Departed, FlightStatus.EnRoute ->
                if (!old.status.isAirborne) add("departed", "On the way to ${new.arrival.airport.displayName}.")
            FlightStatus.Arrived -> add("arrived", "Landed at ${new.arrival.airport.displayName}.")
            else -> {}
        }
        if (old.departure.terminal != new.departure.terminal && new.departure.terminal != null)
            add("terminal change", "Departs from terminal ${new.departure.terminal}" + (old.departure.terminal?.let { " (was $it)" } ?: "") + ".")
        if (old.departure.gate != new.departure.gate && new.departure.gate != null)
            add(if (old.departure.gate == null) "gate assigned" else "gate change",
                "Departure gate ${new.departure.gate}" + (old.departure.gate?.let { " (was $it)" } ?: "") + ".")
        val od = old.departure.delayMinutes ?: 0
        val nd = new.departure.delayMinutes ?: 0
        if (!new.status.isAirborne && new.status != FlightStatus.Arrived && abs(nd - od) >= DELAY_THRESHOLD_MIN)
            add(if (nd > od) "delayed" else "earlier", if (nd > 0) "Now leaving $nd min late." else "Back on schedule.")
        if (old.arrival.baggageBelt == null && new.arrival.baggageBelt != null)
            add("baggage belt", "Bags on belt ${new.arrival.baggageBelt}.")
        return out
    }
}
