package app.contrail.core

import app.contrail.core.importer.*
import java.time.OffsetDateTime
import kotlin.test.*
class FlightEventParserTest {
  val t = OffsetDateTime.parse("2026-12-03T09:15:00+08:00")
  fun ev(s: String, b: String = "", l: String? = null) = CalendarEvent("1", s, b, l, t, t.plusHours(7))
  @Test fun subjectWithRoute() { val c = FlightEventParser.parse(ev("SQ638 SIN-NRT", "Flight departs Terminal 3"))!!
    assertEquals("SQ638", c.designator); assertEquals("SIN", c.origin); assertEquals("NRT", c.destination); assertEquals(Confidence.HIGH, c.confidence) }
  @Test fun spacedNumber() { val c = FlightEventParser.parse(ev("Flight to Tokyo (SQ 012)", "Singapore (SIN) to Tokyo Narita (NRT)"))!!
    assertEquals("SQ12", c.designator); assertEquals("SIN", c.origin) }
  @Test fun meeting() { assertNull(FlightEventParser.parse(ev("Team sync", "Room 4 at 10 AM"))) }
  @Test fun amTime() { assertNull(FlightEventParser.parse(ev("Lunch at 12 PM"))) }
  @Test fun digitLetterAirline() { assertEquals("3K521", FlightEventParser.parse(ev("Flight 3K 521 SIN to BKK"))!!.designator) }
}
