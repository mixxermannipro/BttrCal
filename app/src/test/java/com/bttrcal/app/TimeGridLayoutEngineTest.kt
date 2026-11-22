package com.bttrcal.app

import com.bttrcal.app.domain.engine.TimeGridLayoutEngine
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.EventOccurrence
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeGridLayoutEngineTest {

    private fun occ(id: String, startHour: Int, endHour: Int): EventOccurrence {
        val base = 1_700_000_000_000L
        val s = base + startHour * 3_600_000L
        val e = base + endHour * 3_600_000L
        val ev = CalendarEvent(
            id = id,
            uid = id,
            calendarId = "c1",
            title = id,
            startMillis = s,
            endMillis = e
        )
        return EventOccurrence(ev, 0xFF0A84FF.toInt(), "Cal", s, e, false)
    }

    @Test
    fun overlappingClusterGetsSeparateColumnsWhileDisjointGetsSingleColumn() {
        val e1 = occ("A", 9, 11)
        val e2 = occ("B", 10, 12)
        val e3 = occ("C", 10, 11)
        val e4 = occ("D", 14, 15)

        val placed = TimeGridLayoutEngine.layoutDayEvents(listOf(e1, e2, e3, e4))
        assertEquals(4, placed.size)

        val cluster = placed.filter { it.occurrence.event.id in setOf("A", "B", "C") }
        assertEquals(setOf(3), cluster.map { it.totalColumns }.toSet())
        assertEquals(setOf(0, 1, 2), cluster.map { it.column }.toSet())

        val solo = placed.first { it.occurrence.event.id == "D" }
        assertEquals(0, solo.column)
        assertEquals(1, solo.totalColumns)
    }
}
