package app.contrail.data

import android.content.Context
import app.contrail.core.airports.AirportDb
import app.contrail.core.importer.FlightCandidate
import app.contrail.core.importer.FlightEventParser
import app.contrail.core.importer.IcsSource
import app.contrail.core.model.Flight
import app.contrail.core.model.FlightBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Flights live in a JSON file in app-private storage. Nothing leaves the phone. */
class FlightRepository(private val context: Context, val settings: Settings) {

    private val file = File(context.filesDir, "flights.json")
    private val lock = Mutex()
    private val _book = MutableStateFlow(load())
    val book: StateFlow<FlightBook> = _book.asStateFlow()

    val calendar = DeviceCalendarSource(context)

    /** Loaded once, lazily, from assets/airports.csv (about 400 KB, 5,400 airports). */
    val airports: AirportDb by lazy {
        runCatching { context.assets.open("airports.csv").bufferedReader().use { AirportDb.parse(it.readText()) } }
            .getOrDefault(AirportDb.EMPTY)
    }

    private fun load(): FlightBook =
        if (file.exists()) FlightBook.fromJson(file.readText()) else FlightBook()

    private suspend fun update(block: (FlightBook) -> FlightBook) = lock.withLock {
        val next = block(_book.value)
        withContext(Dispatchers.IO) { file.writeText(next.toJson()) }
        _book.value = next
    }

    // ---- Import ----

    suspend fun candidatesFromCalendar(): List<FlightCandidate> = withContext(Dispatchers.IO) {
        filterNew(FlightEventParser.parseAll(calendar.events(settings.calendarIds), airports::contains))
    }

    suspend fun candidatesFromIcs(text: String): List<FlightCandidate> = withContext(Dispatchers.Default) {
        filterNew(FlightEventParser.parseAll(IcsSource.read(text), airports::contains))
    }

    private fun filterNew(c: List<FlightCandidate>) = c.filterNot { _book.value.contains(it) }

    suspend fun add(candidates: List<FlightCandidate>) {
        update { b -> candidates.fold(b) { acc, c -> acc.upsert(FlightBook.fromCandidate(c)) } }
    }

    suspend fun save(oldId: String?, f: Flight) = update { if (oldId == null) it.upsert(f) else it.replace(oldId, f) }

    suspend fun remove(id: String) = update { it.remove(id) }
}
