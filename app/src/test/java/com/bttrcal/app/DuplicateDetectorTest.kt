package com.bttrcal.app

import com.bttrcal.app.domain.engine.DuplicateDetector
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.EventOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DuplicateDetectorTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun occ(
        id: String,
        calId: String,
        calName: String,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Boolean = false,
        description: String = "",
        location: String = "",
        reminders: List<Int> = listOf(15)
    ): EventOccurrence {
        val s = start.atZone(zone).toInstant().toEpochMilli()
        val e = end.atZone(zone).toInstant().toEpochMilli()
        return EventOccurrence(
            event = CalendarEvent(
                id = id,
                uid = id,
                calendarId = calId,
                title = title,
                description = description,
                location = location,
                startMillis = s,
                endMillis = e,
                allDay = allDay,
                reminderMinutes = reminders
            ),
            calendarColorArgb = 0xFF0A84FF.toInt(),
            calendarName = calName,
            occurrenceStartMillis = s,
            occurrenceEndMillis = e,
            isRecurringInstance = false
        )
    }

    @Test
    fun detectsGermanBirthdayVariationsAcrossThreeCalendars() {
        val day = LocalDate.of(2026, 11, 23)
        val e1 = occ(
            id = "e1", calId = "google", calName = "Google",
            title = "Manuel Geburtstag",
            start = day.atStartOfDay(), end = day.plusDays(1).atStartOfDay(), allDay = true
        )
        val e2 = occ(
            id = "e2", calId = "honor", calName = "Honor",
            title = "Manuel 18. Geburtstag",
            start = day.atStartOfDay(), end = day.plusDays(1).atStartOfDay(), allDay = true,
            description = "Geschenk besorgen"
        )
        val e3 = occ(
            id = "e3", calId = "icloud", calName = "iCloud",
            title = "Manuels 18. Geburtstag Nachname",
            start = day.atTime(9, 0), end = day.atTime(10, 0), allDay = false,
            location = "München", reminders = listOf(30, 60)
        )

        val clusters = DuplicateDetector.detectDuplicates(listOf(e1, e2, e3), zone)
        assertEquals(1, clusters.size)
        val cluster = clusters.first()
        assertEquals(3, cluster.occurrences.size)
        assertTrue(cluster.spansMultipleCalendars)
        assertEquals("Manuels 18. Geburtstag Nachname", cluster.suggestedTitle)

        // Merge into Google calendar should preserve existing id e1 and mark e2, e3 for deletion
        val plan = DuplicateDetector.buildMergePlan(cluster, "google", "Manuels 18. Geburtstag")
        assertEquals("e1", plan.mergedEvent.id)
        assertEquals("google", plan.mergedEvent.calendarId)
        assertEquals("Manuels 18. Geburtstag", plan.mergedEvent.title)
        assertEquals("München", plan.mergedEvent.location)
        assertTrue(plan.mergedEvent.description.contains("Geschenk besorgen"))
        assertEquals(listOf(15, 30, 60), plan.mergedEvent.reminderMinutes)
        assertEquals(setOf("e2", "e3"), plan.eventIdsToDelete.toSet())
    }

    @Test
    fun detectsCloseTimedDuplicatesWithTyposAndAvoidsDistinctEvents() {
        val day = LocalDate.of(2026, 11, 25)
        val e1 = occ(
            id = "t1", calId = "c1", calName = "Privat",
            title = "Zahnarzt Termin",
            start = day.atTime(14, 0), end = day.atTime(15, 0)
        )
        val e2 = occ(
            id = "t2", calId = "c2", calName = "Arbeit",
            title = "Zahnarzttermin",
            start = day.atTime(14, 15), end = day.atTime(15, 0)
        )
        val distinct1 = occ(
            id = "d1", calId = "c1", calName = "Privat",
            title = "Workshop Teil 1",
            start = day.atTime(16, 0), end = day.atTime(17, 0)
        )
        val distinct2 = occ(
            id = "d2", calId = "c2", calName = "Arbeit",
            title = "Workshop Teil 2",
            start = day.atTime(16, 0), end = day.atTime(17, 0)
        )

        val clusters = DuplicateDetector.detectDuplicates(listOf(e1, e2, distinct1, distinct2), zone)
        assertEquals(1, clusters.size)
        assertEquals(setOf("t1", "t2"), clusters.first().occurrences.map { it.event.id }.toSet())
    }

    @Test
    fun doesNotMatchSameTitleOnDifferentDaysOrGenericMeetings() {
        val day1 = LocalDate.of(2026, 11, 26)
        val day2 = LocalDate.of(2026, 11, 27)
        val e1 = occ("a1", "c1", "Cal1", "Team Meeting", day1.atTime(10, 0), day1.atTime(11, 0))
        val e2 = occ("a2", "c2", "Cal2", "Design Meeting", day1.atTime(10, 0), day1.atTime(11, 0))
        val e3 = occ("a3", "c2", "Cal2", "Team Meeting", day2.atTime(10, 0), day2.atTime(11, 0))

        val clusters = DuplicateDetector.detectDuplicates(listOf(e1, e2, e3), zone)
        assertFalse(clusters.isNotEmpty())
    }
}
