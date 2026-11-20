package app.contrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.contrail.core.model.Flight
import app.contrail.core.stats.Trip
import app.contrail.core.stats.Trips
import app.contrail.core.timeline.Timeline
import java.time.Instant

@Composable
fun TripsScreen(flights: List<Flight>, now: Instant, onOpen: (Flight) -> Unit, modifier: Modifier = Modifier) {
    val (past, upcoming) = flights.partition { it.isPast(now) }
    val upcomingTrips = Trips.group(upcoming)
    val pastTrips = Trips.group(past).reversed()

    if (flights.isEmpty()) {
        EmptyState(modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (upcomingTrips.isNotEmpty()) item { SectionLabel("UPCOMING") }
        upcomingTrips.forEach { trip -> tripItems(trip, now, onOpen) }
        if (pastTrips.isNotEmpty()) item { SectionLabel("PAST", Modifier.padding(top = 12.dp)) }
        pastTrips.forEach { trip -> tripItems(trip, now, onOpen, dim = true) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.tripItems(trip: Trip, now: Instant, onOpen: (Flight) -> Unit, dim: Boolean = false) {
    item(key = "trip-" + trip.flights.first().id) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(trip.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                if (trip.start == trip.end) trip.start.day() else "${trip.start.day()} to ${trip.end.day()}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(trip.flights, key = { it.id }) { f -> FlightCard(f, now, dim) { onOpen(f) } }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun FlightCard(f: Flight, now: Instant, dim: Boolean = false, onClick: () -> Unit) {
    val dep = f.departure; val arr = f.arrival
    val delay = dep.delayMinutes
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dim) 0.dp else 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.designator, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("  ·  ${f.date.day()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                StatusPill(f)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportBlock(dep.airport.code, dep.bestUtc.localAt(dep.airport), Alignment.Start)
                Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { RouteLine(Timeline.progress(f, now)) }
                AirportBlock(arr.airport.code, arr.bestUtc.localAt(arr.airport), Alignment.End)
            }
            val gateLine = listOfNotNull(dep.terminal?.let { "Terminal $it" }, dep.gate?.let { "Gate $it" }).joinToString("  ·  ")
            val countdown = if (!f.isPast(now)) relative(dep.bestUtc, now) else null
            if (gateLine.isNotEmpty() || countdown != null || (delay ?: 0) >= 5) {
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(gateLine, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    countdown?.let { Text("Departs $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun AirportBlock(code: String, time: String, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(code, style = MaterialTheme.typography.displaySmall)
        Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A dashed track with a dot that moves along it while the flight is in the air. */
@Composable
fun RouteLine(progress: Float?) {
    val track = MaterialTheme.colorScheme.outline
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(track))
        val p = progress ?: 0f
        Box(Modifier.fillMaxWidth(p).height(2.dp).background(Ember))
        Box(Modifier.offset(x = (maxWidth - 10.dp) * p).size(10.dp).clip(CircleShape).background(Ember))
    }
}

@Composable
fun StatusPill(f: Flight) {
    val delay = f.departure.delayMinutes
    val c = statusColor(f.status, delay)
    Text(
        statusLabel(f.status, delay),
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.FlightTakeoff, null, Modifier.size(48.dp), tint = Ember)
        Spacer(Modifier.height(16.dp))
        Text("No flights yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap + to scan your phone calendar, open an .ics file from Outlook or an airline email, or add a flight number by hand.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
