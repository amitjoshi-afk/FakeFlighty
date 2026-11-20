package app.contrail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.contrail.core.aerodatabox.AdbProvider
import app.contrail.data.DeviceCalendarSource
import app.contrail.data.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    calendars: List<DeviceCalendarSource.DeviceCalendar>,
    hasCalendarPermission: Boolean,
    onRequestCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var key by remember { mutableStateOf(settings.apiKey) }
    var provider by remember { mutableStateOf(settings.provider) }
    var picked by remember(calendars) { mutableStateOf(settings.calendarIds) }
    var alerts by remember { mutableStateOf(settings.alertsEnabled) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel("LIVE FLIGHT DATA", Modifier.fillMaxWidth()) {
            Text("Contrail uses AeroDataBox for status, gates and delays. Paste the key from the marketplace where you subscribed.")
            Spacer(Modifier.height(8.dp))
            AdbProvider.entries.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = provider == p, onClick = { provider = p; settings.provider = p }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = provider == p, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(p.label)
                }
            }
            OutlinedTextField(
                value = key, onValueChange = { key = it; settings.apiKey = it },
                label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Panel("CALENDARS TO SCAN", Modifier.fillMaxWidth()) {
            if (!hasCalendarPermission) {
                Text("Allow calendar access so Contrail can find flights that Outlook has synced to this phone.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRequestCalendar) { Text("Allow calendar access") }
            } else if (calendars.isEmpty()) {
                Text(
                    "No calendars found. In the Outlook app, open Settings, tap your account, and turn on Sync calendars. " +
                        "If your company has turned that off, use .ics import instead.",
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
        }

        Panel("ALERTS", Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gate, delay and status changes", Modifier.weight(1f))
                Switch(checked = alerts, onCheckedChange = { alerts = it; settings.alertsEnabled = it })
            }
            Text(
                "Checked about every 30 minutes for flights in the next two days. Android may stretch this when the battery is low.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
