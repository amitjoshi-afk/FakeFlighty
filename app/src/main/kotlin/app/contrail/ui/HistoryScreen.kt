package app.contrail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.contrail.core.airports.AirportDb
import app.contrail.core.airports.Airlines
import app.contrail.core.model.Flight
import app.contrail.core.stats.Trip
import app.contrail.core.stats.Trips

/** All flights, newest first, grouped by year and then into trips. */
@Composable
fun HistoryScreen(flights: List<Flight>, db: AirportDb, onOpen: (Flight) -> Unit, modifier: Modifier = Modifier) {
    if (flights.isEmpty()) {
        EmptyState(modifier)
        return
    }
    val byYear = Trips.group(flights).reversed().groupBy { it.start.year }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        byYear.forEach { (year, trips) ->
            item(key = "year-$year") {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("$year", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    val n = trips.sumOf { it.flights.size }
                    Text("$n flight${if (n == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trips.forEach { tripItems(it, db, onOpen) }
        }
    }
}

private fun LazyListScope.tripItems(trip: Trip, db: AirportDb, onOpen: (Flight) -> Unit) {
    item(key = "trip-" + trip.flights.first().id) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(Trips.title(trip, db), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                if (trip.start == trip.end) trip.start.day() else "${trip.start.day()} to ${trip.end.day()}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(trip.flights.reversed(), key = { it.id }) { f -> FlightCard(f, db) { onOpen(f) } }
}

@Composable
fun FlightCard(f: Flight, db: AirportDb, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.designator, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("  ·  ${Airlines.name(f.airlineCode)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(f.date.day(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportBlock(f.origin, db[f.origin]?.city, f.departureUtc.clock(f.departureZone), Alignment.Start)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RouteLine()
                    val sub = listOfNotNull(f.duration?.hm(), db.distanceKm(f.origin, f.destination)?.let(::km)).joinToString("  ·  ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AirportBlock(f.destination, db[f.destination]?.city, f.arrivalUtc.clock(f.arrivalZone), Alignment.End)
            }
            if (!f.hasRoute) {
                Spacer(Modifier.height(8.dp))
                Text("Tap to add the airports", color = Ember, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AirportBlock(code: String?, city: String?, time: String, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(code ?: "???", style = MaterialTheme.typography.displaySmall)
        Text(listOfNotNull(city?.takeIf { it.isNotBlank() }, time.takeIf { it != "--:--" }).joinToString(" · ").ifEmpty { " " },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.History, null, Modifier.size(48.dp), tint = Ember)
        Spacer(Modifier.height(16.dp))
        Text("Your flight history", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap + to scan your phone calendar for past flights, open an .ics file exported from Outlook, or add a flight by hand.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
