package app.contrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.contrail.core.model.Airport
import app.contrail.core.model.Flight
import app.contrail.core.model.Movement
import app.contrail.core.stats.Stats
import app.contrail.core.timeline.Timeline
import app.contrail.core.timeline.TimelineStep
import java.time.Duration
import java.time.Instant

@Composable
fun FlightDetailScreen(f: Flight, now: Instant, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(f.airlineName, f.designator).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                Text(f.date.dayYear(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(f)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.departure.airport.code, style = MaterialTheme.typography.displaySmall)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { RouteLine(Timeline.progress(f, now)) }
            Text(f.arrival.airport.code, style = MaterialTheme.typography.displaySmall)
        }
        Stats.duration(f)?.let { d ->
            val left = f.arrival.bestUtc?.let { Duration.between(now, it) }?.takeIf { f.status.isAirborne && !it.isNegative }
            Text(
                listOfNotNull("Flight time ${d.hm()}", left?.let { "${it.hm()} to go" }, Stats.distance(f)?.let(::km)).joinToString("  ·  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MovementCard("DEPART", f.departure, Modifier.weight(1f), showBelt = false)
            MovementCard("ARRIVE", f.arrival, Modifier.weight(1f), showBelt = true)
        }

        Panel("TIMELINE") { Timeline.steps(f, now).forEach { StepRow(it, f) } }

        Panel("AIRCRAFT") {
            Text(f.aircraftModel ?: "Not reported yet")
            f.aircraftReg?.let { Text("Registration $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        Text(
            if (!f.hasLiveData) "No live data yet. Pull refresh once your AeroDataBox key is set."
            else "Last update from AeroDataBox: " + (f.lastUpdatedUtc?.let { it.localAt(Airport(timeZone = null)) } ?: "unknown"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDelete) { Text("Remove flight", color = Ember) }
    }
}

@Composable
private fun MovementCard(label: String, m: Movement, modifier: Modifier, showBelt: Boolean) {
    Panel(label, modifier) {
        Text(m.airport.displayName, style = MaterialTheme.typography.titleMedium)
        m.airport.name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(8.dp))
        TimeWithSchedule(m.bestUtc, m.scheduledUtc, m.airport)
        m.delayMinutes?.takeIf { it >= 5 }?.let { Text("${it}m late", color = Amber, fontWeight = FontWeight.SemiBold) }
        m.delayMinutes?.takeIf { it <= -5 }?.let { Text("${-it}m early", color = Sage, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
        Fact("Terminal", m.terminal)
        Fact("Gate", m.gate)
        if (showBelt) Fact("Baggage", m.baggageBelt) else Fact("Check-in", m.checkInDesk)
    }
}

@Composable
private fun TimeWithSchedule(best: Instant?, scheduled: Instant?, a: Airport) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(best.localAt(a), style = MaterialTheme.typography.headlineSmall)
        if (scheduled != null && best != null && scheduled != best) {
            Spacer(Modifier.width(6.dp))
            Text(scheduled.localAt(a), textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Fact(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value ?: "–", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepRow(s: TimelineStep, f: Flight) {
    val dot = if (s.done) Ember else MaterialTheme.colorScheme.outline
    val zoneAirport = if (s.label.startsWith("Arriv") || s.label == "Landing" || s.label == "Baggage" || s.label == "Diverted") f.arrival.airport else f.departure.airport
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.label + if (s.isEstimate) " (est.)" else "", fontWeight = if (s.done) FontWeight.Normal else FontWeight.SemiBold)
            s.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        s.time?.let { TimeWithSchedule(it, s.scheduled, zoneAirport) }
    }
}

@Composable
fun Panel(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
    ) {
        SectionLabel(label)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
