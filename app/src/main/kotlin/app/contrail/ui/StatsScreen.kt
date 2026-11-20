package app.contrail.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.contrail.core.model.Flight
import app.contrail.core.stats.Stats
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(flights: List<Flight>, now: Instant, modifier: Modifier = Modifier) {
    var year by remember { mutableStateOf<Int?>(null) }
    val years = Stats.years(flights.filter { it.isPast(now) })
    val s = Stats.compute(flights, now, year)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = year == null, onClick = { year = null }, label = { Text("All time") })
            years.forEach { y -> FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") }) }
        }
        if (s.flights == 0) {
            Text("Stats appear once you have flights that have landed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber("FLIGHTS", "${s.flights}", null, Modifier.weight(1f))
            BigNumber("IN THE AIR", s.timeInAir.hm(), null, Modifier.weight(1f))
        }
        BigNumber(
            "DISTANCE", km(s.distanceKm),
            String.format(Locale.US, "%.2f times around the Earth", s.timesAroundEarth), Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber("AIRPORTS", "${s.airports}", null, Modifier.weight(1f))
            BigNumber("AIRLINES", "${s.airlines}", null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber("ON TIME", s.onTimeShare?.let { "${(it * 100).toInt()}%" } ?: "–", "Arrived ≤ ${Stats.ON_TIME_MIN} min late", Modifier.weight(1f))
            BigNumber("AVG DELAY", s.averageArrivalDelayMin?.let { "${it.toInt()}m" } ?: "–", "At arrival", Modifier.weight(1f))
        }
        Panel("HIGHLIGHTS", Modifier.fillMaxWidth()) {
            s.longest?.let { Text("Longest: ${it.route}, ${Stats.distance(it)?.let(::km) ?: "distance unknown"}") }
            s.topRoute?.let { Text("Most flown route: ${it.first} (${it.second}×)") }
            s.topAirport?.let { Text("Busiest airport for you: ${it.first} (${it.second} visits)") }
        }
    }
}

@Composable
private fun BigNumber(label: String, value: String, sub: String?, modifier: Modifier) {
    Panel(label, modifier) {
        Text(value, style = MaterialTheme.typography.displaySmall)
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
