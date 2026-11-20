package app.contrail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.contrail.core.importer.Confidence
import app.contrail.core.model.Flight
import app.contrail.ui.*
import java.time.Instant
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { ContrailTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** An .ics file opened or shared from email, Files or Outlook. */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        uri?.let(vm::importFromIcs)
    }
}

private enum class Tab(val label: String) { History("History"), Stats("Stats"), Settings("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel) {
    val context = LocalContext.current
    val book by vm.book.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val db = vm.airports

    var tab by rememberSaveable { mutableStateOf(Tab.History) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Flight?>(null) }
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

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
    val pickIcs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFromIcs) }

    LaunchedEffect(Unit) { if (hasCalendar) vm.loadCalendars() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val now = Instant.now()
    val today = LocalDate.now()
    val flown = book.flights.filter { it.isPast(now, today) }
    val selected = openId?.let { id -> book.flights.firstOrNull { it.id == id } }
    BackHandler(enabled = selected != null) { openId = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.route ?: if (tab == Tab.History) "Contrail" else tab.label) },
                navigationIcon = {
                    if (selected != null) IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = { if (busy) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp) },
            )
        },
        bottomBar = {
            if (selected == null) NavigationBar {
                NavigationBarItem(tab == Tab.History, { tab = Tab.History }, { Icon(Icons.Filled.History, null) }, label = { Text("History") })
                NavigationBarItem(tab == Tab.Stats, { tab = Tab.Stats }, { Icon(Icons.Filled.BarChart, null) }, label = { Text("Stats") })
                NavigationBarItem(tab == Tab.Settings, { tab = Tab.Settings }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        },
        floatingActionButton = {
            if (selected == null && tab == Tab.History) Box {
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
                    DropdownMenuItem(text = { Text("Add a flight by hand") }, onClick = { showAddMenu = false; adding = true })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val m = Modifier.padding(pad)
        when {
            selected != null -> FlightDetailScreen(
                selected, db, onEdit = { editing = selected },
                onDelete = { vm.remove(selected.id); openId = null }, modifier = m,
            )
            tab == Tab.History -> HistoryScreen(book.flights, db, onOpen = { openId = it.id }, modifier = m)
            tab == Tab.Stats -> StatsScreen(flown, db, modifier = m)
            else -> SettingsScreen(
                vm.repo.settings, calendars, hasCalendar, book.flights.size,
                onRequestCalendar = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }, modifier = m,
            )
        }
    }

    review?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissReview,
            title = { Text("${r.items.size} flights found in your ${r.source}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    r.items.forEachIndexed { i, (c, on) ->
                        Row(Modifier.fillMaxWidth().clickable { vm.toggle(i) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = { vm.toggle(i) })
                            Column {
                                Text("${c.designator}  ${listOfNotNull(c.origin, c.destination).joinToString(" → ").ifEmpty { "airports unknown" }}")
                                Text(
                                    c.date.dayYear() + if (c.confidence == Confidence.LOW) "  ·  check this one" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmReview) { Text("Add selected") } },
            dismissButton = { TextButton(onClick = vm::dismissReview) { Text("Cancel") } },
        )
    }

    if (adding) FlightEditor(null, db, onDismiss = { adding = false }) { f -> adding = false; vm.save(null, f) }
    editing?.let { e ->
        FlightEditor(e, db, onDismiss = { editing = null }) { f ->
            editing = null
            vm.save(e.id, f)
            openId = f.id
        }
    }
}
