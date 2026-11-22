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

    @Test
    fun detectsTravelBuffersMilestonesAndFocusMetrics() {
        val day = LocalDate.of(2026, 11, 24)
        val loc1 = occ("Büro Meeting", day.atTime(9, 0), day.atTime(10, 0)).let {
            it.copy(event = it.event.copy(location = "Büro Mitte"))
        }
        val loc2 = occ("Kundentermin", day.atTime(10, 10), day.atTime(11, 0)).let {
            it.copy(event = it.event.copy(location = "HafenCity"))
        }
        val bday = occ("Lukas Geburtstag", day.plusDays(2).atStartOfDay(), day.plusDays(3).atStartOfDay()).let {
            it.copy(event = it.event.copy(allDay = true))
        }

        val travel = ScheduleAnalyzer.findTravelBufferWarnings(listOf(loc1, loc2), zoneId = zone)
        assertEquals(1, travel.size)
        assertEquals(10L, travel.first().gapMinutes)

        val milestones = ScheduleAnalyzer.findUpcomingMilestones(listOf(bday, loc1), fromDate = day, zoneId = zone)
        assertEquals(1, milestones.size)
        assertEquals("In 2 Tagen", milestones.first().labelDe)

        val focus = ScheduleAnalyzer.calculateDailyFocusMetrics(listOf(loc1, loc2), date = day, zoneId = zone)
        assertEquals(110L, focus.busyMinutes) // 09:00..10:00 (60m) + 10:10..11:00 (50m)
        assertTrue(focus.focusScore in 5..100)
        assertEquals(540L, focus.longestFreeBlockMinutes) // 11:00..20:00 = 9h = 540m
    }
}
