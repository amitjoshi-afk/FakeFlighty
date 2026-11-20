package app.contrail.core

import app.contrail.core.importer.*
import java.time.ZoneId
import kotlin.test.*
class IcsSourceTest {
  val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:abc-1\r\nSUMMARY:Flight to Tokyo (SQ 638)\r\nDTSTART;TZID=\"Singapore Standard Time\":20261203T091500\r\nDTEND:20261203T081500Z\r\nLOCATION:Changi Airport T3\r\nDESCRIPTION:Singapore (SIN) to Tokyo Narita (NRT)\\nBooking ref ABC12\r\n 3\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:abc-2\r\nSUMMARY:Dentist\r\nDTSTART;VALUE=DATE:20261205\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
  @Test fun readsAndParses() {
    val ev = IcsSource.read(ics, ZoneId.of("Asia/Singapore"))
    assertEquals(2, ev.size)
    assertEquals("2026-12-03T09:15+08:00", ev[0].start.toString())
    assertTrue(ev[0].body.endsWith("ABC123"))
    val f = FlightEventParser.parseAll(ev)
    assertEquals(1, f.size); assertEquals("SQ638", f[0].designator); assertEquals("NRT", f[0].destination); assertEquals(Confidence.HIGH, f[0].confidence)
  }
}
