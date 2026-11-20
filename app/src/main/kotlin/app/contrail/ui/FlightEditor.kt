package app.contrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.contrail.core.airports.AirportDb
import app.contrail.core.model.Flight
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Add or edit a flight by hand. Times are optional and entered as local clock times at each airport;
 * when both airports' time zones are unknown, the phone's zone is used.
 */
@Composable
fun FlightEditor(existing: Flight?, db: AirportDb, onDismiss: () -> Unit, onSave: (Flight) -> Unit) {
    var code by remember { mutableStateOf(existing?.designator ?: "") }
    var date by remember { mutableStateOf((existing?.date ?: LocalDate.now()).toString()) }
    var from by remember { mutableStateOf(existing?.origin ?: "") }
    var to by remember { mutableStateOf(existing?.destination ?: "") }
    var dep by remember { mutableStateOf(existing?.departureUtc?.let { it.clock(existing.departureZone) } ?: "") }
    var arr by remember { mutableStateOf(existing?.arrivalUtc?.let { it.clock(existing.arrivalZone) } ?: "") }

    val codeOk = Regex("""^[A-Z0-9]{2}\d{1,4}$""").matches(code.replace(" ", "").uppercase())
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    fun airportOk(s: String) = s.isBlank() || db.contains(s.trim())
    fun timeOf(s: String) = if (s.isBlank()) null else runCatching { LocalTime.parse(s.trim()) }.getOrNull()
    val timesOk = (dep.isBlank() || timeOf(dep) != null) && (arr.isBlank() || timeOf(arr) != null)
    val valid = codeOk && parsedDate != null && airportOk(from) && airportOk(to) && timesOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a flight" else "Edit flight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Flight number, e.g. SQ638") }, singleLine = true,
                    isError = code.isNotBlank() && !codeOk, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = parsedDate == null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it.uppercase().take(3) }, label = { Text("From") }, singleLine = true,
                        isError = !airportOk(from), modifier = Modifier.weight(1f),
                        supportingText = { db[from]?.city?.let { Text(it) } })
                    OutlinedTextField(to, { to = it.uppercase().take(3) }, label = { Text("To") }, singleLine = true,
                        isError = !airportOk(to), modifier = Modifier.weight(1f),
                        supportingText = { db[to]?.city?.let { Text(it) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(dep, { dep = it }, label = { Text("Departs (HH:mm)") }, singleLine = true,
                        isError = dep.isNotBlank() && timeOf(dep) == null, modifier = Modifier.weight(1f))
                    OutlinedTextField(arr, { arr = it }, label = { Text("Arrives (HH:mm)") }, singleLine = true,
                        isError = arr.isNotBlank() && timeOf(arr) == null, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val d = parsedDate!!
                val designator = code.replace(" ", "").uppercase()
                // Keep the zones from the calendar if the times weren't touched; otherwise use the phone's zone.
                val depZone = existing?.departureZone ?: ZoneId.systemDefault().id
                val arrZone = existing?.arrivalZone ?: depZone
                val depAt = timeOf(dep)?.atDate(d)?.atZone(zoneOrDefault(depZone))?.toInstant()
                var arrAt = timeOf(arr)?.atDate(d)?.atZone(zoneOrDefault(arrZone))?.toInstant()
                if (depAt != null && arrAt != null && arrAt.isBefore(depAt)) arrAt = arrAt.plusSeconds(86_400) // overnight
                onSave(
                    (existing ?: Flight(id = "", designator = designator, date = d, sourceEventId = "manual")).copy(
                        id = Flight.idFor(designator, d), designator = designator, date = d,
                        origin = from.trim().ifBlank { null }, destination = to.trim().ifBlank { null },
                        departureUtc = depAt, arrivalUtc = arrAt, departureZone = depZone, arrivalZone = arrZone,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
