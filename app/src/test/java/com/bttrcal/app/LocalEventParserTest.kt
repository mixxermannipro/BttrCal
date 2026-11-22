package com.bttrcal.app

import com.bttrcal.app.domain.engine.LocalEventParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class LocalEventParserTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val fixedNow = LocalDateTime.of(2026, 11, 22, 10, 0) // Sonntag

    @Test
    fun parseGermanRelativeDateTimeAndDuration() {
        val draft = LocalEventParser.parse(
            input = "Morgen 14:30 Zahnarzt in Praxis Mitte für 45 Min",
            now = fixedNow,
            zoneId = zone
        )
        val start = Instant.ofEpochMilli(draft.startMillis).atZone(zone).toLocalDateTime()
        val end = Instant.ofEpochMilli(draft.endMillis).atZone(zone).toLocalDateTime()

        assertEquals(LocalDateTime.of(2026, 11, 23, 14, 30), start)
        assertEquals(LocalDateTime.of(2026, 11, 23, 15, 15), end)
        assertEquals("Praxis Mitte", draft.location)
        assertTrue(draft.title.contains("Zahnarzt", ignoreCase = true))
    }

    @Test
    fun parseRecurringWeekdayAndTimeRange() {
        val draft = LocalEventParser.parse(
            input = "Jeden Montag von 09:00 bis 10:30 Team-Sync",
            now = fixedNow,
            zoneId = zone
        )
        val start = Instant.ofEpochMilli(draft.startMillis).atZone(zone).toLocalDateTime()
        val end = Instant.ofEpochMilli(draft.endMillis).atZone(zone).toLocalDateTime()

        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 0), start)
        assertEquals(LocalDateTime.of(2026, 11, 23, 10, 30), end)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", draft.rrule)
        assertEquals("Team-Sync", draft.title)
    }

    @Test
    fun parseExplicitDateAndAllDay() {
        val draft = LocalEventParser.parse(
            input = "24.12.2026 Weihnachten ganztägig",
            now = fixedNow,
            zoneId = zone
        )
        val start = Instant.ofEpochMilli(draft.startMillis).atZone(zone).toLocalDate()
        assertTrue(draft.allDay)
        assertEquals(24, start.dayOfMonth)
        assertEquals(12, start.monthValue)
        assertEquals("Weihnachten", draft.title)
    }

    @Test
    fun parseConversationalGermanSentenceWithSpelledMonth() {
        val sentence = "Ich möchte gerne um 17 Uhr einen Termin haben, und zwar zum Pokern spielen am 23. November."
        val draft = LocalEventParser.parse(
            input = sentence,
            now = fixedNow,
            zoneId = zone
        )
        val start = Instant.ofEpochMilli(draft.startMillis).atZone(zone).toLocalDateTime()
        val end = Instant.ofEpochMilli(draft.endMillis).atZone(zone).toLocalDateTime()

        assertEquals("Pokern spielen", draft.title)
        assertEquals(LocalDateTime.of(2026, 11, 23, 17, 0), start)
        assertEquals(LocalDateTime.of(2026, 11, 23, 18, 0), end)
        assertEquals(sentence, draft.description)
    }
}
