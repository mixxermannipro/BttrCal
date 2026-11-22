package com.bttrcal.app

import com.bttrcal.app.domain.engine.IcsCodec
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.EventAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class IcsCodecTest {

    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun parseAndExportRoundtrip_preservesFieldsAndEscapes() {
        val start = LocalDateTime.of(2026, 11, 25, 14, 0).atZone(zone).toInstant().toEpochMilli()
        val ev = CalendarEvent(
            id = "e1",
            uid = "uid-123@bttrcal.app",
            calendarId = "cal1",
            title = "Team Sync, Q4; Wichtig",
            description = "Agenda:\nPunkt 1\nPunkt 2",
            location = "Raum 42, Berlin",
            startMillis = start,
            endMillis = start + 3_600_000L,
            timeZoneId = zone.id,
            rrule = "FREQ=WEEKLY;BYDAY=WE",
            reminderMinutes = listOf(15, 30),
            availability = EventAvailability.BUSY
        )

        val ics = IcsCodec.exportToIcs(listOf(ev), "Arbeit")
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;BYDAY=WE"))

        val parsed = IcsCodec.parseIcs(ics, "cal1", zone)
        assertEquals(1, parsed.size)
        val p = parsed.first()
        assertEquals("uid-123@bttrcal.app", p.uid)
        assertEquals("Team Sync, Q4; Wichtig", p.title)
        assertEquals("Agenda:\nPunkt 1\nPunkt 2", p.description)
        assertEquals("Raum 42, Berlin", p.location)
        assertEquals("FREQ=WEEKLY;BYDAY=WE", p.rrule)
        assertEquals(listOf(15, 30), p.reminderMinutes)
    }

    @Test
    fun parseIcs_handlesFoldedLinesAndAllDayEvents() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:all-day-1
            DTSTART;VALUE=DATE:20261224
            DTEND;VALUE=DATE:20261225
            SUMMARY:Heiligabend mit
             Familie
            TRANSP:TRANSPARENT
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsCodec.parseIcs(raw, "c_local", zone)
        assertEquals(1, events.size)
        val ev = events.first()
        assertTrue(ev.allDay)
        assertEquals("Heiligabend mitFamilie", ev.title)
        assertEquals(EventAvailability.FREE, ev.availability)
    }
}
