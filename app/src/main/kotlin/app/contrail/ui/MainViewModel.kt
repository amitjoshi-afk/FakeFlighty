package app.contrail.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.contrail.ContrailApp
import app.contrail.core.importer.Confidence
import app.contrail.core.importer.FlightCandidate
import app.contrail.core.model.Flight
import app.contrail.data.DeviceCalendarSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Candidates waiting for the user to tick which ones to import. */
data class ImportReview(val source: String, val items: List<Pair<FlightCandidate, Boolean>>)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ContrailApp).repository
    val book = repo.book
    val airports get() = repo.airports

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _review = MutableStateFlow<ImportReview?>(null)
    val review = _review.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _calendars = MutableStateFlow<List<DeviceCalendarSource.DeviceCalendar>>(emptyList())
    val calendars = _calendars.asStateFlow()

    init {
        // Warm the airport database off the main thread.
        viewModelScope.launch(Dispatchers.Default) { repo.airports.size }
    }

    fun consumeMessage() { _message.value = null }

    fun loadCalendars() = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { repo.calendar.calendars() }
        _calendars.value = list
        // First run: preselect calendars that look like Outlook ones, or all of them if none do.
        if (repo.settings.calendarIds.isEmpty()) {
            val outlook = list.filter { it.isOutlook }
            repo.settings.calendarIds = (outlook.ifEmpty { list }).map { it.id }.toSet()
        }
    }

    fun importFromCalendar() = viewModelScope.launch {
        if (repo.settings.calendarIds.isEmpty()) {
            _message.value = "Choose which calendars to scan in Settings first."
            return@launch
        }
        _busy.value = true
        val found = repo.candidatesFromCalendar()
        _busy.value = false
        showReview("calendar", found)
    }

    fun importFromIcs(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.reader().readText() } }.getOrNull()
        }
        if (text == null) { _message.value = "Couldn't read that file."; return@launch }
        showReview("calendar file", repo.candidatesFromIcs(text))
    }

    private fun showReview(source: String, found: List<FlightCandidate>) {
        if (found.isEmpty()) { _message.value = "No new flights found in your $source."; return }
        // Low-confidence matches start unticked so nothing odd gets imported silently.
        _review.value = ImportReview(source, found.sortedByDescending { it.date }.map { it to (it.confidence != Confidence.LOW) })
    }

    fun toggle(index: Int) {
        val r = _review.value ?: return
        _review.value = r.copy(items = r.items.mapIndexed { i, p -> if (i == index) p.first to !p.second else p })
    }

    fun dismissReview() { _review.value = null }

    fun confirmReview() = viewModelScope.launch {
        val picked = _review.value?.items?.filter { it.second }?.map { it.first }.orEmpty()
        _review.value = null
        if (picked.isEmpty()) return@launch
        repo.add(picked)
        val missing = picked.count { it.origin == null || it.destination == null }
        _message.value = "Added ${picked.size} flight${if (picked.size == 1) "" else "s"}." +
            if (missing > 0) " $missing need airports filled in." else ""
    }

    fun save(oldId: String?, f: Flight) = viewModelScope.launch { repo.save(oldId, f) }

    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }
}
