package com.bttrcal.app

import com.bttrcal.app.domain.engine.ScheduleAnalyzer
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.domain.model.EventOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleAnalyzerTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun occ(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        availability: EventAvailability = EventAvailability.BUSY
    ): EventOccurrence {
        val s = start.atZone(zone).toInstant().toEpochMilli()
        val e = end.atZone(zone).toInstant().toEpochMilli()
        return EventOccurrence(
            event = CalendarEvent(
                id = title,
                uid = title,
                calendarId = "c1",
                title = title,
                startMillis = s,
                endMillis = e,
                availability = availability
            ),
            calendarColorArgb = 0xFF0A84FF.toInt(),
            calendarName = "Work",
            occurrenceStartMillis = s,
            occurrenceEndMillis = e,
            isRecurringInstance = false
        )
    }

    @Test
    fun detectsConflictsAndCalculatesMergedBusyMinutes() {
        val day = LocalDate.of(2026, 11, 24)
        val o1 = occ("M1", day.atTime(9, 0), day.atTime(10, 30))
        val o2 = occ("M2", day.atTime(10, 0), day.atTime(11, 0))
        val o3 = occ("FreeNote", day.atTime(10, 0), day.atTime(12, 0), EventAvailability.FREE)

        val conflicts = ScheduleAnalyzer.findConflicts(listOf(o1, o2, o3))
        assertEquals(1, conflicts.size)
        assertEquals(30L, conflicts.first().overlapMinutes)

        val busy = ScheduleAnalyzer.calculateBusyMinutes(
            listOf(o1, o2, o3),
            day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
        assertEquals(120L, busy) // 09:00..11:00 merged = 2 hours
    }

    @Test
    fun findsFreeSlotsAroundBusyBlocks() {
        val day = LocalDate.of(2026, 11, 24)
        val o1 = occ("Morning", day.atTime(8, 0), day.atTime(12, 0))
        val slots = ScheduleAnalyzer.findFreeSlots(
            occurrences = listOf(o1),
            startDate = day,
            days = 1,
            durationMinutes = 60,
            zoneId = zone,
            nowMillis = day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        )
        assertTrue(slots.isNotEmpty())
        val firstStart = java.time.Instant.ofEpochMilli(slots.first().startMillis).atZone(zone).toLocalTime()
        assertEquals(12, firstStart.hour)
    }
}
