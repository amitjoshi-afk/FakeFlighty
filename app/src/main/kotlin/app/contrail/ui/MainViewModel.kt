package app.contrail.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.contrail.ContrailApp
import app.contrail.core.importer.Confidence
import app.contrail.core.importer.FlightCandidate
import app.contrail.data.DeviceCalendarSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Candidates waiting for the user to tick which ones to import. */
data class ImportReview(val source: String, val items: List<Pair<FlightCandidate, Boolean>>)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ContrailApp).repository
    val book = repo.book

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _review = MutableStateFlow<ImportReview?>(null)
    val review = _review.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _calendars = MutableStateFlow<List<DeviceCalendarSource.DeviceCalendar>>(emptyList())
    val calendars = _calendars.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun loadCalendars() = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { repo.calendar.calendars() }
        _calendars.value = list
        // First run: preselect calendars that look like Outlook ones.
        if (repo.settings.calendarIds.isEmpty()) repo.settings.calendarIds = list.filter { it.isOutlook }.map { it.id }.toSet()
    }

    fun importFromCalendar() = viewModelScope.launch {
        if (repo.settings.calendarIds.isEmpty()) {
            _message.value = "Choose which calendars to scan in Settings first."
            return@launch
        }
        showReview("phone calendar", repo.candidatesFromCalendar())
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
        _review.value = ImportReview(source, found.map { it to (it.confidence != Confidence.LOW) })
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
        _message.value = "Added ${picked.size} flight${if (picked.size == 1) "" else "s"}."
        refreshAll()
    }

    fun addManual(designator: String, date: LocalDate) = viewModelScope.launch {
        val code = designator.replace(" ", "").uppercase()
        val m = Regex("""^([A-Z0-9]{2})(\d{1,4})$""").find(code)
        if (m == null) { _message.value = "Enter a flight number like SQ638."; return@launch }
        val c = FlightCandidate("manual", m.groupValues[1], m.groupValues[2].trimStart('0').ifEmpty { "0" }, date, null, null, Confidence.HIGH)
        repo.add(listOf(c))
        refreshAll()
    }

    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }

    fun refreshAll() = viewModelScope.launch {
        if (repo.settings.apiKey.isBlank()) {
            _message.value = "Add your AeroDataBox key in Settings to get live status."
            return@launch
        }
        _refreshing.value = true
        val errors = mutableListOf<String>()
        for (f in repo.flightsToWatch()) {
            runCatching { repo.refresh(f.id) }.onFailure { errors += it.message ?: "Lookup failed" }
        }
        _refreshing.value = false
        errors.firstOrNull()?.let { _message.value = it }
    }

    fun refreshOne(id: String) = viewModelScope.launch {
        _refreshing.value = true
        runCatching { repo.refresh(id) }.onFailure { _message.value = it.message }
        _refreshing.value = false
    }
}
