package com.bttrcal.app.domain.engine

import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.FreeSlotSuggestion
import com.bttrcal.app.domain.model.ScheduleBriefing
import com.bttrcal.app.domain.model.ScheduleConflict
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScheduleAnalyzer {

    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    private val DAY_TIME_FMT = DateTimeFormatter.ofPattern("EEE, dd.MM. · HH:mm", Locale.GERMAN)

    fun findConflicts(occurrences: List<EventOccurrence>): List<ScheduleConflict> {
        val timed = occurrences
            .filter { !it.event.allDay && it.event.availability != EventAvailability.FREE }
            .sortedBy { it.occurrenceStartMillis }

        val conflicts = mutableListOf<ScheduleConflict>()
        for (i in timed.indices) {
            val a = timed[i]
            for (j in (i + 1) until timed.size) {
                val b = timed[j]
                if (b.occurrenceStartMillis >= a.occurrenceEndMillis) break
                val overlapEnd = minOf(a.occurrenceEndMillis, b.occurrenceEndMillis)
                val overlapMin = (overlapEnd - b.occurrenceStartMillis) / 60_000L
                if (overlapMin > 0) {
                    conflicts += ScheduleConflict(a, b, overlapMin)
                }
            }
        }
        return conflicts
    }

    fun calculateBusyMinutes(
        occurrences: List<EventOccurrence>,
        rangeStartMillis: Long,
        rangeEndMillis: Long
    ): Long {
        val intervals = occurrences
            .filter { !it.event.allDay && it.event.availability != EventAvailability.FREE }
            .map {
                maxOf(it.occurrenceStartMillis, rangeStartMillis) to
                    minOf(it.occurrenceEndMillis, rangeEndMillis)
            }
            .filter { it.second > it.first }
            .sortedBy { it.first }

        if (intervals.isEmpty()) return 0L

        var totalMillis = 0L
        var curStart = intervals.first().first
        var curEnd = intervals.first().second

        for (i in 1 until intervals.size) {
            val (s, e) = intervals[i]
            if (s <= curEnd) {
                curEnd = maxOf(curEnd, e)
            } else {
                totalMillis += (curEnd - curStart)
                curStart = s
                curEnd = e
            }
        }
        totalMillis += (curEnd - curStart)
        return totalMillis / 60_000L
    }

    fun findFreeSlots(
        occurrences: List<EventOccurrence>,
        startDate: LocalDate,
        days: Int = 5,
        durationMinutes: Int = 60,
        workStart: LocalTime = LocalTime.of(8, 0),
        workEnd: LocalTime = LocalTime.of(20, 0),
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
        maxResults: Int = 8
    ): List<FreeSlotSuggestion> {
        val requiredMillis = durationMinutes * 60_000L
        val results = mutableListOf<FreeSlotSuggestion>()

        for (offset in 0 until days) {
            val date = startDate.plusDays(offset.toLong())
            val windowStart = maxOf(
                ZonedDateTime.of(date, workStart, zoneId).toInstant().toEpochMilli(),
                nowMillis
            )
            val windowEnd = ZonedDateTime.of(date, workEnd, zoneId).toInstant().toEpochMilli()
            if (windowEnd - windowStart < requiredMillis) continue

            val busy = occurrences
                .filter { !it.event.allDay && it.event.availability != EventAvailability.FREE }
                .map { maxOf(it.occurrenceStartMillis, windowStart) to minOf(it.occurrenceEndMillis, windowEnd) }
                .filter { it.second > it.first }
                .sortedBy { it.first }

            var cursor = windowStart
            for ((bStart, bEnd) in busy) {
                if (bStart - cursor >= requiredMillis) {
                    results += buildSlot(cursor, bStart, durationMinutes, zoneId)
                    if (results.size >= maxResults) return results
                }
                cursor = maxOf(cursor, bEnd)
            }
            if (windowEnd - cursor >= requiredMillis) {
                results += buildSlot(cursor, windowEnd, durationMinutes, zoneId)
                if (results.size >= maxResults) return results
            }
        }
        return results
    }

    private fun buildSlot(
        freeStart: Long,
        freeEnd: Long,
        requestedDurationMinutes: Int,
        zoneId: ZoneId
    ): FreeSlotSuggestion {
        val slotEnd = freeStart + requestedDurationMinutes * 60_000L
        val startZdt = Instant.ofEpochMilli(freeStart).atZone(zoneId)
        val totalWindowMin = ((freeEnd - freeStart) / 60_000L).toInt()
        val timeOfDayLabel = when {
            startZdt.hour < 12 -> "Vormittags-Fokus"
            startZdt.hour < 17 -> "Nachmittags-Slot"
            else -> "Abend-Fenster"
        }
        val reason = "${startZdt.format(DAY_TIME_FMT)} (${totalWindowMin} Min am Stück frei)"
        return FreeSlotSuggestion(
            startMillis = freeStart,
            endMillis = slotEnd,
            durationMinutes = requestedDurationMinutes,
            scoreLabel = timeOfDayLabel,
            reason = reason
        )
    }

    fun buildLocalBriefing(
        periodTitle: String,
        occurrences: List<EventOccurrence>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ScheduleBriefing {
        val sorted = occurrences.sortedBy { it.occurrenceStartMillis }
        val conflicts = findConflicts(sorted)
        val busyMinutes = calculateBusyMinutes(sorted, rangeStartMillis, rangeEndMillis)
        val totalWindowMinutes = ((rangeEndMillis - rangeStartMillis) / 60_000L).coerceAtLeast(busyMinutes)
        val freeMinutes = (totalWindowMinutes - busyMinutes).coerceAtLeast(0L)

        val highlights = mutableListOf<String>()
        if (sorted.isEmpty()) {
            highlights += "Keine Termine eingetragen – komplett freier Zeitraum für Fokus oder Erholung."
        } else {
            val first = sorted.first()
            val firstZdt = Instant.ofEpochMilli(first.occurrenceStartMillis).atZone(zoneId)
            val timeStr = if (first.event.allDay) "Ganztägig" else firstZdt.format(TIME_FMT)
            highlights += "Start: ${first.event.title} ($timeStr)"

            if (conflicts.isNotEmpty()) {
                val c = conflicts.first()
                highlights += "Achtung: ${conflicts.size} Überschneidung(en), z. B. ${c.first.event.title} & ${c.second.event.title}"
            } else {
                highlights += "Keine Terminkonflikte erkannt."
            }

            val withLocation = sorted.firstOrNull { it.event.location.isNotBlank() }
            if (withLocation != null) {
                highlights += "Unterwegs: ${withLocation.event.title} (${withLocation.event.location})"
            }
        }

        val hoursBusy = busyMinutes / 60
        val minsBusy = busyMinutes % 60
        val summary = if (sorted.isEmpty()) {
            "$periodTitle sieht herrlich entspannt aus: keine Termine geplant."
        } else {
            "$periodTitle: ${sorted.size} Termin(e), insgesamt ${hoursBusy} Std ${minsBusy} Min verplant" +
                if (conflicts.isNotEmpty()) " mit ${conflicts.size} Konflikt(en)." else " ohne Überschneidungen."
        }

        return ScheduleBriefing(
            periodTitle = periodTitle,
            summary = summary,
            highlights = highlights,
            conflictsCount = conflicts.size,
            totalEventsCount = sorted.size,
            freeMinutes = freeMinutes,
            busyMinutes = busyMinutes,
            usedAi = false
        )
    }
}
