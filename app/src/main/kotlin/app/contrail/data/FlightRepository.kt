package app.contrail.data

import android.content.Context
import app.contrail.core.aerodatabox.AeroDataBoxClient
import app.contrail.core.aerodatabox.HttpGet
import app.contrail.core.aerodatabox.HttpResult
import app.contrail.core.importer.CalendarEvent
import app.contrail.core.importer.FlightCandidate
import app.contrail.core.importer.FlightEventParser
import app.contrail.core.importer.IcsSource
import app.contrail.core.model.ChangeDetector
import app.contrail.core.model.Flight
import app.contrail.core.model.FlightBook
import app.contrail.core.model.FlightChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class FlightRepository(private val context: Context, val settings: Settings) {

    private val file = File(context.filesDir, "flights.json")
    private val lock = Mutex()
    private val _book = MutableStateFlow(load())
    val book: StateFlow<FlightBook> = _book.asStateFlow()

    val calendar = DeviceCalendarSource(context)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val transport = HttpGet { url, headers ->
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        http.newCall(req).execute().use { HttpResult(it.code, it.body?.string().orEmpty()) }
    }

    private fun client() = AeroDataBoxClient(settings.provider, settings.apiKey, transport)

    private fun load(): FlightBook =
        if (file.exists()) FlightBook.fromJson(file.readText()) else FlightBook()

    private suspend fun update(block: (FlightBook) -> FlightBook) = lock.withLock {
        val next = block(_book.value)
        withContext(Dispatchers.IO) { file.writeText(next.toJson()) }
        _book.value = next
    }

    // ---- Import ----

    suspend fun candidatesFromCalendar(): List<FlightCandidate> = withContext(Dispatchers.IO) {
        filterNew(FlightEventParser.parseAll(calendar.events(settings.calendarIds)))
    }

    suspend fun candidatesFromIcs(text: String): List<FlightCandidate> = withContext(Dispatchers.Default) {
        filterNew(FlightEventParser.parseAll(IcsSource.read(text)))
    }

    fun candidatesFromEvents(events: List<CalendarEvent>) = filterNew(FlightEventParser.parseAll(events))

    private fun filterNew(c: List<FlightCandidate>) = c.filterNot { _book.value.contains(it) }

    suspend fun add(candidates: List<FlightCandidate>) {
        update { b -> candidates.fold(b) { acc, c -> acc.upsert(FlightBook.placeholder(c)) } }
    }

    suspend fun remove(id: String) = update { it.remove(id) }

    // ---- Live status ----

    /** Refreshes one flight. Returns the changes worth alerting on. Throws on network or key errors. */
    suspend fun refresh(id: String): List<FlightChange> {
        val old = _book.value.flights.firstOrNull { it.id == id } ?: return emptyList()
        val fresh = withContext(Dispatchers.IO) {
            client().status(old.designator, old.date, old.departure.airport.iata)
        } ?: return emptyList()
        val merged = fresh.copy(sourceEventId = old.sourceEventId)
        update { it.upsert(merged) }
        return ChangeDetector.diff(old, merged)
    }

    /**
     * Flights worth polling now: not finished, and departing within the next 2 days
     * (or never looked up yet, so new imports fill in quickly).
     */
    fun flightsToWatch(now: Instant = Instant.now()): List<Flight> = _book.value.flights.filter { f ->
        if (f.isPast(now)) return@filter false
        val dep = f.departure.bestUtc ?: f.date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        !f.hasLiveData || Duration.between(now, dep) <= Duration.ofDays(2)
    }
}
