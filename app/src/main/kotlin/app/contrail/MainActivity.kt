package app.contrail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.contrail.ui.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val openFlight = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { ContrailTheme { App(vm, openFlight) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_FLIGHT_ID)?.let { openFlight.value = it }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        uri?.let(vm::importFromIcs)
    }

    companion object { const val EXTRA_FLIGHT_ID = "flightId" }
}

private enum class Tab(val label: String) { Trips("Trips"), Stats("Stats"), Settings("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel, openFlight: MutableState<String?>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val book by vm.book.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Trips) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Tick once a minute so countdowns and progress stay current.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }

    fun calendarGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    var hasCalendar by remember { mutableStateOf(calendarGranted()) }
    var importAfterGrant by remember { mutableStateOf(false) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasCalendar = ok
        if (ok) {
            vm.loadCalendars()
            if (importAfterGrant) vm.importFromCalendar()
        }
        importAfterGrant = false
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pickIcs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFromIcs) }

    LaunchedEffect(Unit) {
        if (hasCalendar) vm.loadCalendars()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.refreshAll()
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val selected = openFlight.value?.let { id -> book.flights.firstOrNull { it.id == id } }
    BackHandler(enabled = selected != null) { openFlight.value = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.route ?: if (tab == Tab.Trips) "Contrail" else tab.label) },
                navigationIcon = {
                    if (selected != null) IconButton(onClick = { openFlight.value = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    else if (tab == Tab.Trips) IconButton(onClick = { selected?.let { vm.refreshOne(it.id) } ?: vm.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            if (selected == null) NavigationBar {
                NavigationBarItem(tab == Tab.Trips, { tab = Tab.Trips }, { Icon(Icons.Filled.Flight, null) }, label = { Text("Trips") })
                NavigationBarItem(tab == Tab.Stats, { tab = Tab.Stats }, { Icon(Icons.Filled.BarChart, null) }, label = { Text("Stats") })
                NavigationBarItem(tab == Tab.Settings, { tab = Tab.Settings }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        },
        floatingActionButton = {
            if (selected == null && tab == Tab.Trips) Box {
                FloatingActionButton(onClick = { showAddMenu = true }, containerColor = Ember, contentColor = Paper) {
                    Icon(Icons.Filled.Add, "Add flights")
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(text = { Text("Scan phone calendar") }, onClick = {
                        showAddMenu = false
                        if (hasCalendar) vm.importFromCalendar()
                        else { importAfterGrant = true; calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
                    })
                    DropdownMenuItem(text = { Text("Open .ics file") }, onClick = {
                        showAddMenu = false
                        pickIcs.launch(arrayOf("text/calendar", "application/ics", "text/x-vcalendar", "*/*"))
                    })
                    DropdownMenuItem(text = { Text("Add flight number") }, onClick = { showAddMenu = false; showManual = true })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val m = Modifier.padding(pad)
        when {
            selected != null -> FlightDetailScreen(selected, now, onDelete = { vm.remove(selected.id); openFlight.value = null }, modifier = m)
            tab == Tab.Trips -> TripsScreen(book.flights, now, onOpen = { openFlight.value = it.id }, modifier = m)
            tab == Tab.Stats -> StatsScreen(book.flights, now, modifier = m)
            else -> SettingsScreen(
                vm.repo.settings, calendars, hasCalendar,
                onRequestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }, modifier = m,
            )
        }
    }

    review?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissReview,
            title = { Text("Flights found in your ${r.source}") },
            text = {
                Column {
                    r.items.forEachIndexed { i, (c, on) ->
                        Row(Modifier.fillMaxWidth().clickable { vm.toggle(i) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = { vm.toggle(i) })
                            Column {
                                Text("${c.designator}  ${listOfNotNull(c.origin, c.destination).joinToString(" → ")}")
                                Text(c.date.day() + if (c.confidence.name == "LOW") "  ·  check this one" else "", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmReview) { Text("Add") } },
            dismissButton = { TextButton(onClick = vm::dismissReview) { Text("Cancel") } },
        )
    }

    if (showManual) ManualAddDialog(onDismiss = { showManual = false }) { code, date -> showManual = false; vm.addManual(code, date) }
}

@Composable
private fun ManualAddDialog(onDismiss: () -> Unit, onAdd: (String, LocalDate) -> Unit) {
    var code by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val parsed = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a flight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it }, label = { Text("Flight number, e.g. SQ638") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Local departure date (YYYY-MM-DD)") }, singleLine = true, isError = parsed == null)
            }
        },
        confirmButton = { TextButton(enabled = code.isNotBlank() && parsed != null, onClick = { onAdd(code, parsed!!) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
