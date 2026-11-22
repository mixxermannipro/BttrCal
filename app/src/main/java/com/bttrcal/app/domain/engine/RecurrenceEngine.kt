package com.bttrcal.app.domain.engine

import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.EventOccurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class RecurrenceFreq {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

data class ParsedRRule(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val count: Int? = null,
    val untilMillis: Long? = null,
    val byDays: Set<DayOfWeek> = emptySet()
) {
    fun toRRuleString(): String {
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval > 1) parts += "INTERVAL=$interval"
        if (count != null && count > 0) parts += "COUNT=$count"
        if (untilMillis != null) {
            val zdt = Instant.ofEpochMilli(untilMillis).atZone(ZoneOffset.UTC)
            parts += "UNTIL=${zdt.format(UTC_FORMATTER)}"
        }
        if (byDays.isNotEmpty()) {
            val ordered = DayOfWeek.entries.filter { it in byDays }.joinToString(",") { dayToIcs(it) }
            parts += "BYDAY=$ordered"
        }
        return parts.joinToString(";")
    }

    fun toGermanSummary(): String {
        val base = when (freq) {
            RecurrenceFreq.DAILY -> if (interval == 1) "Täglich" else "Alle $interval Tage"
            RecurrenceFreq.WEEKLY -> {
                val prefix = if (interval == 1) "Wöchentlich" else "Alle $interval Wochen"
                if (byDays.isEmpty()) prefix else "$prefix (${byDays.joinToString(", ") { dayToShortDe(it) }})"
            }
            RecurrenceFreq.MONTHLY -> if (interval == 1) "Monatlich" else "Alle $interval Monate"
            RecurrenceFreq.YEARLY -> if (interval == 1) "Jährlich" else "Alle $interval Jahre"
        }
        return when {
            count != null -> "$base · $count×"
            untilMillis != null -> {
                val date = Instant.ofEpochMilli(untilMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                "$base · bis ${date.dayOfMonth}.${date.monthValue}.${date.year}"
            }
            else -> base
        }
    }

    companion object {
        private val UTC_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        fun dayToIcs(day: DayOfWeek): String = when (day) {
            DayOfWeek.MONDAY -> "MO"
            DayOfWeek.TUESDAY -> "TU"
            DayOfWeek.WEDNESDAY -> "WE"
            DayOfWeek.THURSDAY -> "TH"
            DayOfWeek.FRIDAY -> "FR"
            DayOfWeek.SATURDAY -> "SA"
            DayOfWeek.SUNDAY -> "SU"
        }

        fun icsToDay(code: String): DayOfWeek? = when (code.trim().uppercase().takeLast(2)) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }

        fun dayToShortDe(day: DayOfWeek): String = when (day) {
            DayOfWeek.MONDAY -> "Mo"
            DayOfWeek.TUESDAY -> "Di"
            DayOfWeek.WEDNESDAY -> "Mi"
            DayOfWeek.THURSDAY -> "Do"
            DayOfWeek.FRIDAY -> "Fr"
            DayOfWeek.SATURDAY -> "Sa"
            DayOfWeek.SUNDAY -> "So"
        }
    }
}

object RecurrenceEngine {

    private const val MAX_EXPANSION_ITERATIONS = 2000

    fun parseRRule(raw: String?): ParsedRRule? {
        val cleaned = raw?.trim()?.removePrefix("RRULE:") ?: return null
        if (cleaned.isEmpty()) return null

        var freq: RecurrenceFreq? = null
        var interval = 1
        var count: Int? = null
        var untilMillis: Long? = null
        val byDays = linkedSetOf<DayOfWeek>()

        cleaned.split(";").forEach { token ->
            val idx = token.indexOf('=')
            if (idx <= 0) return@forEach
            val key = token.substring(0, idx).trim().uppercase()
            val value = token.substring(idx + 1).trim()
            when (key) {
                "FREQ" -> freq = runCatching { RecurrenceFreq.valueOf(value.uppercase()) }.getOrNull()
                "INTERVAL" -> interval = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
                "COUNT" -> count = value.toIntOrNull()?.coerceAtLeast(1)
                "UNTIL" -> untilMillis = parseUntil(value)
                "BYDAY" -> value.split(",").mapNotNull { ParsedRRule.icsToDay(it) }.forEach { byDays += it }
            }
        }

        val resolvedFreq = freq ?: return null
        return ParsedRRule(
            freq = resolvedFreq,
            interval = interval,
            count = count,
            untilMillis = untilMillis,
            byDays = byDays
        )
    }

    fun expandOccurrences(
        event: CalendarEvent,
        calendarColorArgb: Int,
        calendarName: String,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        defaultZoneId: ZoneId = ZoneId.systemDefault()
    ): List<EventOccurrence> {
        val duration = (event.endMillis - event.startMillis).coerceAtLeast(1L)
        val parsedRule = parseRRule(event.rrule)

        if (parsedRule == null) {
            if (event.endMillis <= rangeStartMillis || event.startMillis >= rangeEndMillis) {
                return emptyList()
            }
            return listOf(
                EventOccurrence(
                    event = event,
                    calendarColorArgb = calendarColorArgb,
                    calendarName = calendarName,
                    occurrenceStartMillis = event.startMillis,
                    occurrenceEndMillis = event.endMillis,
                    isRecurringInstance = false
                )
            )
        }

        val zone = runCatching { ZoneId.of(event.timeZoneId) }.getOrElse { defaultZoneId }
        val startZdt = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        val localTime = startZdt.toLocalTime()
        val results = mutableListOf<EventOccurrence>()
        val excludedSet = event.exdates.toHashSet()

        fun isExcluded(occStart: Long): Boolean {
            if (occStart in excludedSet) return true
            val occDate = Instant.ofEpochMilli(occStart).atZone(zone).toLocalDate()
            return event.exdates.any { ex ->
                Instant.ofEpochMilli(ex).atZone(zone).toLocalDate() == occDate && event.allDay
            }
        }

        when (parsedRule.freq) {
            RecurrenceFreq.DAILY -> {
                var currentDate = startZdt.toLocalDate()
                var emitted = 0
                var iterations = 0
                val step = parsedRule.interval.toLong()
                val rangeStartDate = Instant.ofEpochMilli(rangeStartMillis).atZone(zone).toLocalDate()
                if (parsedRule.count == null && ChronoUnit.DAYS.between(currentDate, rangeStartDate) > step * 2) {
                    val jumps = ((ChronoUnit.DAYS.between(currentDate, rangeStartDate) - 1) / step)
                    currentDate = currentDate.plusDays(jumps * step)
                }

                while (iterations++ < MAX_EXPANSION_ITERATIONS) {
                    val occStart = ZonedDateTime.of(currentDate, localTime, zone).toInstant().toEpochMilli()
                    val occEnd = occStart + duration
                    if (parsedRule.untilMillis != null && occStart > parsedRule.untilMillis) break
                    if (occStart >= rangeEndMillis) break
                    emitted++
                    if (parsedRule.count != null && emitted > parsedRule.count) break
                    if (occEnd > rangeStartMillis && !isExcluded(occStart)) {
                        results += EventOccurrence(event, calendarColorArgb, calendarName, occStart, occEnd, true)
                    }
                    currentDate = currentDate.plusDays(step)
                }
            }

            RecurrenceFreq.WEEKLY -> {
                val targetDays = parsedRule.byDays.ifEmpty { setOf(startZdt.dayOfWeek) }
                val orderedDays = DayOfWeek.entries.filter { it in targetDays }
                val startDate = startZdt.toLocalDate()
                var weekStart = startDate.with(DayOfWeek.MONDAY)
                var emitted = 0
                var iterations = 0
                val stepWeeks = parsedRule.interval.toLong()

                while (iterations++ < MAX_EXPANSION_ITERATIONS) {
                    var shouldStop = false
                    for (dow in orderedDays) {
                        val candidateDate = weekStart.with(dow)
                        if (candidateDate.isBefore(startDate)) continue
                        val occStart = ZonedDateTime.of(candidateDate, localTime, zone).toInstant().toEpochMilli()
                        val occEnd = occStart + duration
                        if (parsedRule.untilMillis != null && occStart > parsedRule.untilMillis) {
                            shouldStop = true
                            break
                        }
                        if (occStart >= rangeEndMillis) {
                            shouldStop = true
                            break
                        }
                        emitted++
                        if (parsedRule.count != null && emitted > parsedRule.count) {
                            shouldStop = true
                            break
                        }
                        if (occEnd > rangeStartMillis && !isExcluded(occStart)) {
                            results += EventOccurrence(event, calendarColorArgb, calendarName, occStart, occEnd, true)
                        }
                    }
                    if (shouldStop) break
                    weekStart = weekStart.plusWeeks(stepWeeks)
                }
            }

            RecurrenceFreq.MONTHLY -> {
                val anchorDay = startZdt.dayOfMonth
                var ym = YearMonth.from(startZdt.toLocalDate())
                var emitted = 0
                var iterations = 0
                val stepMonths = parsedRule.interval.toLong()

                while (iterations++ < MAX_EXPANSION_ITERATIONS) {
                    val safeDay = anchorDay.coerceAtMost(ym.lengthOfMonth())
                    val candidateDate = ym.atDay(safeDay)
                    val occStart = ZonedDateTime.of(candidateDate, localTime, zone).toInstant().toEpochMilli()
                    val occEnd = occStart + duration
                    if (parsedRule.untilMillis != null && occStart > parsedRule.untilMillis) break
                    if (occStart >= rangeEndMillis) break
                    emitted++
                    if (parsedRule.count != null && emitted > parsedRule.count) break
                    if (occEnd > rangeStartMillis && !isExcluded(occStart)) {
                        results += EventOccurrence(event, calendarColorArgb, calendarName, occStart, occEnd, true)
                    }
                    ym = ym.plusMonths(stepMonths)
                }
            }

            RecurrenceFreq.YEARLY -> {
                val anchorMonth = startZdt.month
                val anchorDay = startZdt.dayOfMonth
                var year = startZdt.year
                var emitted = 0
                var iterations = 0
                val stepYears = parsedRule.interval

                while (iterations++ < MAX_EXPANSION_ITERATIONS) {
                    val ym = YearMonth.of(year, anchorMonth)
                    val safeDay = anchorDay.coerceAtMost(ym.lengthOfMonth())
                    val candidateDate = LocalDate.of(year, anchorMonth, safeDay)
                    val occStart = ZonedDateTime.of(candidateDate, localTime, zone).toInstant().toEpochMilli()
                    val occEnd = occStart + duration
                    if (parsedRule.untilMillis != null && occStart > parsedRule.untilMillis) break
                    if (occStart >= rangeEndMillis) break
                    emitted++
                    if (parsedRule.count != null && emitted > parsedRule.count) break
                    if (occEnd > rangeStartMillis && !isExcluded(occStart)) {
                        results += EventOccurrence(event, calendarColorArgb, calendarName, occStart, occEnd, true)
                    }
                    year += stepYears
                }
            }
        }

        return results
    }

    private fun parseUntil(raw: String): Long? {
        val clean = raw.trim()
        return runCatching {
            when {
                clean.length == 8 && clean.all { it.isDigit() } -> {
                    val date = LocalDate.parse(clean, DateTimeFormatter.BASIC_ISO_DATE)
                    date.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
                clean.endsWith("Z") -> {
                    val ldt = LocalDateTime.parse(clean.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
                clean.contains("T") -> {
                    val ldt = LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                else -> null
            }
        }.getOrNull()
    }
}
