package app.contrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.contrail.core.airports.AirportDb
import app.contrail.core.airports.Airlines
import app.contrail.core.model.Flight
import java.time.Instant

@Composable
fun FlightDetailScreen(f: Flight, db: AirportDb, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val from = db[f.origin]
    val to = db[f.destination]
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("${Airlines.name(f.airlineCode)} ${f.designator}", style = MaterialTheme.typography.titleMedium)
            Text(f.date.dayYear(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.origin ?: "???", style = MaterialTheme.typography.displaySmall)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) { RouteLine() }
            Text(f.destination ?: "???", style = MaterialTheme.typography.displaySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EndCard("FROM", from?.city, from?.name, from?.countryName, f.departureUtc, f.departureZone, Modifier.weight(1f))
            EndCard("TO", to?.city, to?.name, to?.countryName, f.arrivalUtc, f.arrivalZone, Modifier.weight(1f))
        }

        Panel("FLIGHT", Modifier.fillMaxWidth()) {
            Fact("Duration (from calendar)", f.duration?.hm())
            Fact("Great-circle distance", db.distanceKm(f.origin, f.destination)?.let(::km))
            Fact(
                "Type",
                if (from != null && to != null) (if (from.country == to.country) "Domestic" else "International") else null,
            )
            Fact("Source", if (f.sourceEventId == null || f.sourceEventId == "manual") "Added by hand" else "Imported from calendar")
        }

        if (!f.hasRoute) Text(
            "The calendar entry didn't name both airports. Tap Edit and add the three-letter codes so this flight counts toward distance and country stats.",
            color = Ember,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Remove", color = Ember) }
        }
    }
}

@Composable
private fun EndCard(label: String, city: String?, name: String?, country: String?, time: Instant?, zone: String?, modifier: Modifier) {
    Panel(label, modifier) {
        Text(city?.takeIf { it.isNotBlank() } ?: "Unknown airport", style = MaterialTheme.typography.titleMedium)
        name?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        country?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(8.dp))
        Text(time.clock(zone), style = MaterialTheme.typography.headlineSmall)
        if (time != null) Text(zoneOrDefault(zone).id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
