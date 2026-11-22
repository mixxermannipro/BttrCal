package com.bttrcal.app.domain.engine

import com.bttrcal.app.domain.model.DailyFocusMetrics
import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.FreeSlotSuggestion
import com.bttrcal.app.domain.model.ScheduleBriefing
import com.bttrcal.app.domain.model.ScheduleConflict
import com.bttrcal.app.domain.model.TravelBufferWarning
import com.bttrcal.app.domain.model.UpcomingMilestone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

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

    fun findTravelBufferWarnings(
        occurrences: List<EventOccurrence>,
        maxGapMinutes: Long = 15L,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<TravelBufferWarning> {
        val timedByDay = occurrences
            .filter { !it.event.allDay && it.event.availability != EventAvailability.FREE && it.event.id.isNotBlank() }
            .sortedBy { it.occurrenceStartMillis }
            .groupBy { Instant.ofEpochMilli(it.occurrenceStartMillis).atZone(zoneId).toLocalDate() }

        val warnings = mutableListOf<TravelBufferWarning>()
        for (dayList in timedByDay.values) {
            for (i in 0 until (dayList.size - 1)) {
                val first = dayList[i]
                val second = dayList[i + 1]
                if (first.event.id == second.event.id) continue
                val loc1 = first.event.location.trim()
                val loc2 = second.event.location.trim()
                if (loc1.isBlank() || loc2.isBlank()) continue
                if (loc1.equals(loc2, ignoreCase = true)) continue

                val gapMin = (second.occurrenceStartMillis - first.occurrenceEndMillis) / 60_000L
                if (gapMin in 0L..maxGapMinutes) {
                    val gapStr = if (gapMin == 0L) "Nahtloser Übergang (0 Min Puffer)" else "Nur $gapMin Min Wechselzeit"
                    warnings += TravelBufferWarning(
                        first = first,
                        second = second,
                        gapMinutes = gapMin,
                        messageDe = "$gapStr zwischen „$loc1“ und „$loc2“"
                    )
                }
            }
        }
        return warnings.sortedBy { it.first.occurrenceStartMillis }
    }

    private val MILESTONE_KEYWORDS = listOf(
        "geburtstag", "birthday", "bday", "jahrestag", "hochzeitstag", "jubiläum", "jubilaeum", "🎂"
    )

    fun findUpcomingMilestones(
        occurrences: List<EventOccurrence>,
        fromDate: LocalDate = LocalDate.now(),
        horizonDays: Long = 30L,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<UpcomingMilestone> {
        val endDate = fromDate.plusDays(horizonDays)
        val seenKeys = hashSetOf<String>()
        val results = mutableListOf<UpcomingMilestone>()

        for (occ in occurrences.sortedBy { it.occurrenceStartMillis }) {
            val lower = occ.event.title.lowercase(Locale.GERMAN)
            if (MILESTONE_KEYWORDS.none { lower.contains(it) }) continue
            val occDate = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zoneId).toLocalDate()
            if (occDate.isBefore(fromDate) || occDate.isAfter(endDate)) continue

            val dedupKey = "${occDate}_${DuplicateDetector.normalizeTitle(occ.event.title).concatenated}"
            if (!seenKeys.add(dedupKey)) continue

            val daysUntil = ChronoUnit.DAYS.between(fromDate, occDate)
            val label = when (daysUntil) {
                0L -> "Heute"
                1L -> "Morgen"
                in 2L..6L -> "In $daysUntil Tagen"
                else -> "Am ${occDate.format(DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN))}"
            }
            results += UpcomingMilestone(
                occurrence = occ,
                daysUntil = daysUntil,
                labelDe = label
            )
        }
        return results
    }

    fun calculateDailyFocusMetrics(
        occurrences: List<EventOccurrence>,
        date: LocalDate = LocalDate.now(),
        workStart: LocalTime = LocalTime.of(8, 0),
        workEnd: LocalTime = LocalTime.of(20, 0),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DailyFocusMetrics {
        val windowStart = ZonedDateTime.of(date, workStart, zoneId).toInstant().toEpochMilli()
        val windowEnd = ZonedDateTime.of(date, workEnd, zoneId).toInstant().toEpochMilli()
        val totalWindowMinutes = ((windowEnd - windowStart) / 60_000L).coerceAtLeast(60L)

        val busyIntervals = occurrences
            .filter { !it.event.allDay && it.event.availability != EventAvailability.FREE }
            .map { maxOf(it.occurrenceStartMillis, windowStart) to minOf(it.occurrenceEndMillis, windowEnd) }
            .filter { it.second > it.first }
            .sortedBy { it.first }

        val mergedIntervals = mutableListOf<Pair<Long, Long>>()
        for (iv in busyIntervals) {
            if (mergedIntervals.isEmpty() || iv.first > mergedIntervals.last().second) {
                mergedIntervals += iv
            } else {
                val prev = mergedIntervals.removeAt(mergedIntervals.lastIndex)
                mergedIntervals += prev.first to maxOf(prev.second, iv.second)
            }
        }

        val busyMin = mergedIntervals.sumOf { (it.second - it.first) / 60_000L }
        val freeMin = (totalWindowMinutes - busyMin).coerceAtLeast(0L)

        var longestFree = 0L
        var cursor = windowStart
        for ((s, e) in mergedIntervals) {
            longestFree = maxOf(longestFree, (s - cursor) / 60_000L)
            cursor = maxOf(cursor, e)
        }
        longestFree = maxOf(longestFree, (windowEnd - cursor) / 60_000L)

        val freeRatio = freeMin.toDouble() / totalWindowMinutes.toDouble()
        val deepBlockBonus = (longestFree.coerceAtMost(240L) / 240.0) * 0.35
        val score = ((freeRatio * 0.65 + deepBlockBonus) * 100.0).roundToInt().coerceIn(5, 100)

        val summary = when {
            busyMin == 0L -> "Volle Fokus-Freiheit (${freeMin / 60} Std frei)"
            longestFree >= 120L -> "${longestFree / 60} Std ${longestFree % 60} Min Deep-Work-Block verfügbar"
            freeMin >= 180L -> "${freeMin / 60} Std freie Zeit über den Tag verteilt"
            else -> "Dicht getakteter Tag – kurze Pausen bewusst einplanen"
        }

        return DailyFocusMetrics(
            focusScore = score,
            busyMinutes = busyMin,
            freeMinutes = freeMin,
            longestFreeBlockMinutes = longestFree,
            summaryDe = summary
        )
    }
}
