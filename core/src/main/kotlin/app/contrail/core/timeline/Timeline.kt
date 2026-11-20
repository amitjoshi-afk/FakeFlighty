package app.contrail.core.timeline

import app.contrail.core.model.Flight
import app.contrail.core.model.FlightStatus
import java.time.Duration
import java.time.Instant

data class TimelineStep(
    val label: String,
    val time: Instant?,
    /** Scheduled time, shown struck through when it differs from [time]. */
    val scheduled: Instant? = null,
    val done: Boolean,
    val isEstimate: Boolean = false,
    val detail: String? = null,
)

object Timeline {
    /** Typical boarding lead time. Shown as an estimate because AeroDataBox does not report boarding. */
    val BOARDING_LEAD: Duration = Duration.ofMinutes(40)

    fun steps(f: Flight, now: Instant): List<TimelineStep> {
        val dep = f.departure
        val arr = f.arrival
        val depTime = dep.bestUtc
        val arrTime = arr.bestUtc
        val departed = f.status.isAirborne || f.status == FlightStatus.Arrived || (dep.runwayUtc?.isBefore(now) == true)
        val arrived = f.status == FlightStatus.Arrived

        val steps = mutableListOf<TimelineStep>()
        if (f.status == FlightStatus.Canceled || f.status == FlightStatus.CanceledUncertain) {
            steps += TimelineStep("Flight canceled", f.lastUpdatedUtc, done = true)
            return steps
        }
        depTime?.let {
            val boarding = it.minus(BOARDING_LEAD)
            steps += TimelineStep(
                "Boarding", boarding, done = departed || f.status == FlightStatus.Boarding || f.status == FlightStatus.GateClosed || boarding.isBefore(now),
                isEstimate = true, detail = dep.gate?.let { g -> "Gate $g" },
            )
        }
        steps += TimelineStep(
            "Leaves gate", depTime, scheduled = dep.scheduledUtc,
            done = departed || (depTime?.isBefore(now) == true && f.status != FlightStatus.Delayed),
            isEstimate = dep.revisedUtc == null && dep.predictedUtc != null,
            detail = listOfNotNull(dep.terminal?.let { "Terminal $it" }, dep.gate?.let { "Gate $it" }).joinToString(" · ").ifEmpty { null },
        )
        dep.runwayUtc?.let { steps += TimelineStep("Takeoff", it, done = it.isBefore(now)) }
        arr.runwayUtc?.let { steps += TimelineStep("Landing", it, done = it.isBefore(now)) }
        steps += TimelineStep(
            if (f.status == FlightStatus.Diverted) "Diverted" else "Arrives at gate", arrTime, scheduled = arr.scheduledUtc,
            done = arrived || (arrTime?.isBefore(now) == true && departed),
            isEstimate = arr.revisedUtc == null && arr.predictedUtc != null,
            detail = listOfNotNull(arr.terminal?.let { "Terminal $it" }, arr.gate?.let { "Gate $it" }).joinToString(" · ").ifEmpty { null },
        )
        arr.baggageBelt?.let { steps += TimelineStep("Baggage", null, done = arrived, detail = "Belt $it") }
        return steps
    }

    /** Fraction of the trip flown, 0..1, based on gate times. Null before departure data exists. */
    fun progress(f: Flight, now: Instant): Float? {
        val a = f.departure.bestUtc ?: return null
        val b = f.arrival.bestUtc ?: return null
        if (!b.isAfter(a)) return null
        if (f.status == FlightStatus.Arrived) return 1f
        val p = Duration.between(a, now).toMillis().toFloat() / Duration.between(a, b).toMillis()
        return p.coerceIn(0f, 1f)
    }
}
