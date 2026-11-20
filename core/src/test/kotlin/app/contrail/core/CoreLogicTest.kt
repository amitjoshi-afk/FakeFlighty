package app.contrail.core

import app.contrail.core.aerodatabox.*
import app.contrail.core.model.*
import app.contrail.core.stats.Stats
import app.contrail.core.stats.Trips
import app.contrail.core.timeline.Timeline
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*

class CoreLogicTest {

    // Sample shaped like an AeroDataBox FlightContract. Values are made up for the test.
    private val sample = """
      [{
        "number": "SQ 638", "status": "Expected", "codeshareStatus": "IsOperator", "isCargo": false,
        "lastUpdatedUtc": "2026-12-03 00:40Z",
        "greatCircleDistance": {"meter": 5330000.0, "km": 5330.0, "mile": 3312.0},
        "airline": {"name": "Singapore Airlines", "iata": "SQ", "icao": "SIA"},
        "aircraft": {"reg": "9V-SMA", "model": "Airbus A350-900"},
        "departure": {
          "airport": {"icao": "WSSS", "iata": "SIN", "name": "Singapore Changi", "municipalityName": "Singapore",
                      "timeZone": "Asia/Singapore", "location": {"lat": 1.35, "lon": 103.99}},
          "scheduledTime": {"utc": "2026-12-03 15:15Z", "local": "2026-12-03 23:15+08:00"},
          "revisedTime": {"utc": "2026-12-03 15:45Z", "local": "2026-12-03 23:45+08:00"},
          "terminal": "3", "gate": "A12", "quality": ["Basic", "Live"]
        },
        "arrival": {
          "airport": {"icao": "RJAA", "iata": "NRT", "name": "Tokyo Narita", "municipalityName": "Tokyo",
                      "timeZone": "Asia/Tokyo", "location": {"lat": 35.76, "lon": 140.39}},
          "scheduledTime": {"utc": "2026-12-03 22:20Z", "local": "2026-12-04 07:20+09:00"},
          "terminal": "1", "quality": ["Basic"]
        },
        "someNewField": {"x": 1}
      }]
    """.trimIndent()

    private val date = LocalDate.parse("2026-12-03")

    private fun client(code: Int, body: String) = AeroDataBoxClient(AdbProvider.RapidApi, "key") { url, headers ->
        assertTrue(url.endsWith("/flights/number/SQ638/2026-12-03?withAircraftImage=false&withLocation=false"), url)
        assertEquals("key", headers["X-RapidAPI-Key"])
        HttpResult(code, body)
    }

    @Test fun mapsAeroDataBoxResponse() {
        val f = client(200, sample).status("SQ638", date, "SIN")!!
        assertEquals("SQ638@2026-12-03", f.id)
        assertEquals(FlightStatus.Expected, f.status)
        assertEquals("A12", f.departure.gate)
        assertEquals(30L, f.departure.delayMinutes)
        assertEquals(Instant.parse("2026-12-03T15:45:00Z"), f.departure.bestUtc)
        assertEquals("Tokyo", f.arrival.airport.displayName)
        assertEquals(5330.0, f.distanceKm)
    }

    @Test fun handlesNoContentAndBadKey() {
        assertNull(client(204, "").status("SQ638", date))
        assertFailsWith<AeroDataBoxException> { client(401, "").status("SQ638", date) }
    }

    @Test fun detectsGateChangeAndDelay() {
        val old = client(200, sample).status("SQ638", date)!!
        val new = old.copy(departure = old.departure.copy(gate = "B4", revisedUtc = Instant.parse("2026-12-03T16:30:00Z")))
        val titles = ChangeDetector.diff(old, new).map { it.title }
        assertEquals(listOf("SQ638 gate change", "SQ638 delayed"), titles)
        assertTrue(ChangeDetector.diff(old.copy(hasLiveData = false), new).isEmpty())
    }

    @Test fun timelineAndProgress() {
        val f = client(200, sample).status("SQ638", date)!!
        val now = Instant.parse("2026-12-03T19:00:00Z")
        val airborne = f.copy(status = FlightStatus.EnRoute)
        val steps = Timeline.steps(airborne, now)
        assertEquals(listOf("Boarding", "Leaves gate", "Arrives at gate"), steps.map { it.label })
        assertTrue(steps[1].done); assertFalse(steps[2].done)
        val p = Timeline.progress(airborne, now)!!
        assertTrue(p in 0.4f..0.6f, "progress $p")
    }

    @Test fun statsAndTrips() {
        val out = client(200, sample).status("SQ638", date)!!.copy(status = FlightStatus.Arrived)
        val back = out.copy(
            id = "SQ637@2026-12-08", designator = "SQ637", date = LocalDate.parse("2026-12-08"),
            departure = out.arrival.copy(scheduledUtc = Instant.parse("2026-12-08T02:00:00Z"), revisedUtc = null),
            arrival = out.departure.copy(scheduledUtc = Instant.parse("2026-12-08T09:30:00Z"), revisedUtc = null),
        )
        val s = Stats.compute(listOf(out, back), Instant.parse("2027-01-01T00:00:00Z"))
        assertEquals(2, s.flights); assertEquals(10660.0, s.distanceKm); assertEquals(2, s.airports)
        assertEquals(1, Trips.group(listOf(out)).size)
        assertEquals(2, Trips.group(listOf(back, out)).size) // five days apart
        // Haversine SIN-NRT from the sample coordinates lands near the reported 5,330 km.
        assertTrue(Stats.haversineKm(1.35, 103.99, 35.76, 140.39) in 5200.0..5450.0)
    }

    @Test fun flightBookRoundTrip() {
        val f = client(200, sample).status("SQ638", date)!!
        val book = FlightBook().upsert(f)
        assertEquals(book, FlightBook.fromJson(book.toJson()))
        assertEquals(FlightBook(), FlightBook.fromJson("not json"))
    }
}
