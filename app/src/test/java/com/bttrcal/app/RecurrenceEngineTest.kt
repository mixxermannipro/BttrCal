package com.bttrcal.app

import com.bttrcal.app.domain.engine.RecurrenceEngine
import com.bttrcal.app.domain.engine.RecurrenceFreq
import com.bttrcal.app.domain.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceEngineTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun eventAt(
        start: LocalDateTime,
        durationMinutes: Long = 60L,
        rrule: String? = null,
        exdates: List<Long> = emptyList()
    ): CalendarEvent {
        val s = start.atZone(zone).toInstant().toEpochMilli()
        return CalendarEvent(
            id = "e1",
            uid = "u1",
            calendarId = "c1",
            title = "Test",
            startMillis = s,
            endMillis = s + durationMinutes * 60_000L,
            timeZoneId = zone.id,
            rrule = rrule,
            exdates = exdates
        )
    }

    @Test
    fun parseRRule_parsesWeeklyWithIntervalAndByDay() {
        val parsed = RecurrenceEngine.parseRRule("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;COUNT=6")
        assertNotNull(parsed)
        assertEquals(RecurrenceFreq.WEEKLY, parsed!!.freq)
        assertEquals(2, parsed.interval)
        assertEquals(6, parsed.count)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), parsed.byDays)
    }

    @Test
    fun expandDaily_respectsCountAndExdates() {
        val start = LocalDateTime.of(2026, 11, 2, 9, 0)
        val excluded = LocalDateTime.of(2026, 11, 4, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val ev = eventAt(start, rrule = "FREQ=DAILY;COUNT=4", exdates = listOf(excluded))

        val rangeStart = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.of(2026, 11, 10).atStartOfDay(zone).toInstant().toEpochMilli()

        val occs = RecurrenceEngine.expandOccurrences(ev, 0xFF0A84FF.toInt(), "Test", rangeStart, rangeEnd, zone)
        assertEquals(3, occs.size) // 2nd, 3rd, 5th (4th excluded)
    }

    @Test
    fun expandMonthly_clampsEndOfMonthSafely() {
        val start = LocalDateTime.of(2026, 1, 31, 10, 0)
        val ev = eventAt(start, rrule = "FREQ=MONTHLY;COUNT=3")

        val rangeStart = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.of(2026, 5, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val occs = RecurrenceEngine.expandOccurrences(ev, 0xFF0A84FF.toInt(), "Test", rangeStart, rangeEnd, zone)
        assertEquals(3, occs.size)
        val febDate = java.time.Instant.ofEpochMilli(occs[1].occurrenceStartMillis).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 2, 28), febDate)
    }
}
