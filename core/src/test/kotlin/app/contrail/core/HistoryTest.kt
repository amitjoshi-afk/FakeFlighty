package app.contrail.core

import app.contrail.core.airports.AirportDb
import app.contrail.core.importer.FlightEventParser
import app.contrail.core.importer.IcsSource
import app.contrail.core.model.Flight
import app.contrail.core.model.FlightBook
import app.contrail.core.stats.Stats
import app.contrail.core.stats.Trips
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class HistoryTest {

    // Loads the real bundled airport file so the tests also check the shipped data.
    private val db: AirportDb = AirportDb.parse(
        listOf("../app/src/main/assets/airports.csv", "app/src/main/assets/airports.csv")
            .map(::File).first { it.exists() }.readText(),
    )

    private fun flight(code: String, date: String, from: String, to: String, dep: String? = null, hours: Long = 0) = Flight(
        id = Flight.idFor(code, LocalDate.parse(date)), designator = code, date = LocalDate.parse(date),
        origin = from, destination = to,
        departureUtc = dep?.let(Instant::parse), arrivalUtc = dep?.let { Instant.parse(it).plus(Duration.ofHours(hours)) },
    )

    @Test fun airportDatabaseHasKeyAirports() {
        assertTrue(db.size > 4000, "size ${db.size}")
        assertEquals("Singapore", db["SIN"]!!.countryName)
        assertEquals("JP", db["nrt"]!!.country)
        assertEquals("Zürich Airport", db["ZRH"]!!.name)
        // SIN to NRT great-circle distance is roughly 5,300 km.
        assertTrue(db.distanceKm("SIN", "NRT")!! in 5250.0..5400.0)
    }

    @Test fun csvSplitterHandlesQuotes() {
        assertEquals(listOf("A", "B, C", "say \"hi\"", ""), AirportDb.splitCsv("A,\"B, C\",\"say \"\"hi\"\"\","))
    }

    @Test fun calendarTimesAndOutlookZonesCarryThrough() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:x1
            SUMMARY:Flight SQ 322 to London
            DTSTART;TZID=Singapore Standard Time:20250310T232500
            DTEND;TZID=GMT Standard Time:20250311T060000
            DESCRIPTION:Singapore (SIN) to London Heathrow (LHR)
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val ev = IcsSource.read(ics, ZoneId.of("UTC"))
        val c = FlightEventParser.parseAll(ev, db::contains).single()
        assertEquals("SQ322", c.designator)
        assertEquals("SIN" to "LHR", c.origin to c.destination)
        assertEquals("Asia/Singapore", c.departureZone)
        assertEquals("Europe/London", c.arrivalZone)
        val f = FlightBook.fromCandidate(c)
        assertEquals(Duration.ofMinutes(14 * 60 + 35), f.duration) // 23:25 SGT to 06:00 GMT next day
    }

    @Test fun airportCheckRejectsFakeRoutes() {
        val ev = IcsSource.read(
            "BEGIN:VEVENT\nUID:2\nSUMMARY:LH 400 ABC-XQZ\nDTSTART:20250101T100000Z\nEND:VEVENT", ZoneId.of("UTC"),
        )
        val c = FlightEventParser.parseAll(ev, db::contains).single()
        assertNull(c.origin)
    }

    @Test fun statsFromHistory() {
        val flights = listOf(
            flight("SQ638", "2025-03-01", "SIN", "NRT", "2025-03-01T15:15:00Z", 7),
            flight("SQ637", "2025-03-08", "NRT", "SIN", "2025-03-08T02:00:00Z", 7),
            flight("SQ322", "2024-06-10", "SIN", "LHR", "2024-06-10T15:25:00Z", 14),
            flight("BA2", "2024-06-12", "LHR", "JFK"), // no times
        )
        val s = Stats.compute(flights, db)
        assertEquals(4, s.flights)
        assertEquals(4, s.flightsWithDistance)
        assertEquals(3, s.flightsWithTime)
        assertEquals(Duration.ofHours(28), s.timeInAir)
        assertEquals(listOf("Japan", "Singapore", "United Kingdom", "United States"), s.countries)
        assertEquals(4, s.international)
        assertEquals("NRT ⇄ SIN", s.topRoutes.first().key)
        assertEquals(2, s.topRoutes.first().count)
        assertEquals("SIN", s.topAirports.first().key)
        assertEquals(listOf(2024 to 2, 2025 to 2), s.perYear)
        assertEquals("SQ322", s.longest!!.first.designator)

        val only2025 = Stats.compute(flights, db, 2025)
        assertEquals(2, only2025.flights)

        val trips = Trips.group(flights)
        assertEquals(3, trips.size) // London trip, then Tokyo out and back a week apart
        assertEquals("Trip to New York", Trips.title(trips[0], db))
    }

    @Test fun pastDetectionWorksWithoutTimes() {
        val f = flight("BA2", "2024-06-12", "LHR", "JFK")
        assertTrue(f.isPast(Instant.parse("2024-06-13T00:00:00Z"), LocalDate.parse("2024-06-13")))
        assertFalse(f.isPast(Instant.parse("2024-06-11T00:00:00Z"), LocalDate.parse("2024-06-11")))
    }

    @Test fun bookRoundTrip() {
        val book = FlightBook().upsert(flight("SQ638", "2025-03-01", "SIN", "NRT", "2025-03-01T15:15:00Z", 7))
        assertEquals(book, FlightBook.fromJson(book.toJson()))
        val edited = book.flights[0].copy(id = Flight.idFor("SQ639", LocalDate.parse("2025-03-01")), designator = "SQ639")
        assertEquals(listOf("SQ639"), book.replace(book.flights[0].id, edited).flights.map { it.designator })
    }
}
