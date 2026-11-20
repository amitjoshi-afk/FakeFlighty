package app.contrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.contrail.data.DeviceCalendarSource
import app.contrail.data.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    calendars: List<DeviceCalendarSource.DeviceCalendar>,
    hasCalendarPermission: Boolean,
    flightCount: Int,
    onRequestCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picked by remember(calendars) { mutableStateOf(settings.calendarIds) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel("CALENDARS TO SCAN", Modifier.fillMaxWidth()) {
            if (!hasCalendarPermission) {
                Text("Allow calendar access so Contrail can find past flights that Outlook has synced to this phone.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRequestCalendar) { Text("Allow calendar access") }
            } else if (calendars.isEmpty()) {
                Text(
                    "No calendars found. In the Outlook app, open Settings, tap your account, and turn on Sync calendars. " +
                        "If your company has turned that off, export an .ics file from Outlook instead.",
                )
            } else calendars.forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = c.id in picked, onCheckedChange = { on ->
                        picked = if (on) picked + c.id else picked - c.id
                        settings.calendarIds = picked
                    })
                    Column {
                        Text(c.name)
                        Text(c.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Scans the last 15 years. Flights you've already added are skipped.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Panel("PRIVACY", Modifier.fillMaxWidth()) {
            Text("Contrail has no internet access. Your $flightCount flights stay in this app's private storage on your phone.")
        }

        Panel("ABOUT", Modifier.fillMaxWidth()) {
            Text("Airport data: OurAirports (ourairports.com), public domain.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
