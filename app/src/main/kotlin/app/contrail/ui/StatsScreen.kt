package app.contrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.contrail.core.airports.AirportDb
import app.contrail.core.airports.Airlines
import app.contrail.core.model.Flight
import app.contrail.core.stats.Stats
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(flown: List<Flight>, db: AirportDb, modifier: Modifier = Modifier) {
    var year by remember { mutableStateOf<Int?>(null) }
    val years = Stats.years(flown)
    val s = remember(flown, year) { Stats.compute(flown, db, year) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = year == null, onClick = { year = null }, label = { Text("All time") })
            years.forEach { y -> FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") }) }
        }
        if (s.flights == 0) {
            Text("Stats appear once you've added past flights.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber("FLIGHTS", "${s.flights}", "${s.international} international", Modifier.weight(1f))
            BigNumber(
                "IN THE AIR", s.timeInAir.dh(),
                if (s.flightsWithTime < s.flights) "${s.flightsWithTime} of ${s.flights} flights have times" else "From calendar times",
                Modifier.weight(1f),
            )
        }
        BigNumber(
            "DISTANCE", km(s.distanceKm),
            String.format(Locale.US, "%.1f× around the Earth · %.0f%% of the way to the Moon", s.timesAroundEarth, s.shareToMoon * 100) +
                if (s.flightsWithDistance < s.flights) "\n${s.flights - s.flightsWithDistance} flights missing airports" else "",
            Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber("AIRPORTS", "${s.airports}", null, Modifier.weight(1f))
            BigNumber("COUNTRIES", "${s.countries.size}", null, Modifier.weight(1f))
            BigNumber("AIRLINES", "${s.airlines}", null, Modifier.weight(1f))
        }

        if (year == null && s.perYear.size > 1) Panel("FLIGHTS PER YEAR", Modifier.fillMaxWidth()) { YearBars(s.perYear) }

        if (s.topRoutes.isNotEmpty()) Panel("TOP ROUTES", Modifier.fillMaxWidth()) {
            val max = s.topRoutes.first().count
            s.topRoutes.forEach { r ->
                val codes = r.key.split(" ⇄ ")
                RankBar(r.key, codes.mapNotNull { db[it]?.city }.joinToString(" ⇄ ").ifEmpty { null }, r.count, max)
            }
        }
        Panel("TOP AIRPORTS", Modifier.fillMaxWidth()) {
            val max = s.topAirports.firstOrNull()?.count ?: 1
            s.topAirports.forEach { a -> RankBar(a.key, db[a.key]?.let { "${it.city}, ${it.countryName}" }, a.count, max) }
        }
        Panel("TOP AIRLINES", Modifier.fillMaxWidth()) {
            val max = s.topAirlines.firstOrNull()?.count ?: 1
            s.topAirlines.forEach { a -> RankBar(Airlines.name(a.key), a.key, a.count, max) }
        }
        Panel("RECORDS", Modifier.fillMaxWidth()) {
            s.longest?.let { (f, d) -> Fact("Longest", "${f.origin}–${f.destination}, ${km(d)}") }
            s.shortest?.let { (f, d) -> Fact("Shortest", "${f.origin}–${f.destination}, ${km(d)}") }
            s.firstFlight?.let { Fact(if (year == null) "First flight on record" else "First flight of $year", "${it.designator}, ${it.date.dayYear()}") }
        }
        if (s.countries.isNotEmpty()) Panel("COUNTRIES", Modifier.fillMaxWidth()) {
            Text(s.countries.joinToString(" · "))
        }
        Text(
            "Distances are great-circle distances between airports, so actual flown distance is a little longer. Time in the air uses the start and end of each calendar entry.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BigNumber(label: String, value: String, sub: String?, modifier: Modifier) {
    Panel(label, modifier) {
        Text(value, style = MaterialTheme.typography.displaySmall)
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun YearBars(perYear: List<Pair<Int, Int>>) {
    val max = perYear.maxOf { it.second }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        perYear.forEach { (y, n) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                Text("$n", style = MaterialTheme.typography.bodySmall)
                Box(Modifier.width(28.dp).height((90f * n / max).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Ember))
                Text("'" + "$y".takeLast(2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
