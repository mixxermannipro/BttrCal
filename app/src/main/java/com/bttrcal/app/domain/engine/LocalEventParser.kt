package com.bttrcal.app.domain.engine

import com.bttrcal.app.domain.model.ParsedEventDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object LocalEventParser {

    private val DATE_DMY_REGEX = Regex("""\b(\d{1,2})\.(\d{1,2})\.(?:(\d{2,4}))?\b""")
    private val TIME_RANGE_REGEX = Regex("""\b(?:von\s+)?(\d{1,2})(?::(\d{2}))?\s*(?:-|bis|to)\s*(\d{1,2})(?::(\d{2}))?\s*(?:uhr)?\b""", RegexOption.IGNORE_CASE)
    private val TIME_24H_REGEX = Regex("""\b(?:um\s+|at\s+)?(\d{1,2}):(\d{2})\s*(?:uhr)?\b""", RegexOption.IGNORE_CASE)
    private val TIME_UHR_REGEX = Regex("""\b(?:um\s+)?(\d{1,2})\s*uhr\b""", RegexOption.IGNORE_CASE)
    private val TIME_AMPM_REGEX = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""", RegexOption.IGNORE_CASE)
    private val DURATION_REGEX = Regex("""\b(?:für|for|dauer)?\s*(\d{1,3})\s*(min(?:uten)?|m|std|stunden?|h)\b""", RegexOption.IGNORE_CASE)
    private val REMINDER_REGEX = Regex("""\b(?:erinnerung|reminder)\s*(\d{1,3})\s*(min|std|h)\b""", RegexOption.IGNORE_CASE)
    private val LOCATION_REGEX = Regex("""(?:@|ort:|in\s+(?=der\s+|dem\s+|den\s+|[A-ZÄÖÜ]))([^\n,;]+?)(?=\s+(?:um|am|heute|morgen|übermorgen|von|für|erinnerung|täglich|wöchentlich|monatlich|$))""", RegexOption.IGNORE_CASE)

    fun parse(
        input: String,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        defaultDurationMinutes: Int = 60,
        defaultReminderMinutes: Int = 15
    ): ParsedEventDraft {
        var working = input.trim()
        val lower = working.lowercase()

        var targetDate = now.toLocalDate()
        var dateDetected = false

        when {
            lower.contains("übermorgen") || lower.contains("uebermorgen") -> {
                targetDate = now.toLocalDate().plusDays(2)
                working = working.replace(Regex("""\b(übermorgen|uebermorgen)\b""", RegexOption.IGNORE_CASE), "")
                dateDetected = true
            }
            lower.contains("morgen") || lower.contains("tomorrow") -> {
                targetDate = now.toLocalDate().plusDays(1)
                working = working.replace(Regex("""\b(morgen|tomorrow)\b""", RegexOption.IGNORE_CASE), "")
                dateDetected = true
            }
            lower.contains("heute") || lower.contains("today") -> {
                targetDate = now.toLocalDate()
                working = working.replace(Regex("""\b(heute|today)\b""", RegexOption.IGNORE_CASE), "")
                dateDetected = true
            }
        }

        val weekdayMap = mapOf(
            "montag" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
            "dienstag" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
            "mittwoch" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
            "donnerstag" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
            "freitag" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
            "samstag" to DayOfWeek.SATURDAY, "sonnabend" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
            "sonntag" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY
        )

        var rrule: String? = null
        when {
            lower.contains("täglich") || lower.contains("daily") || lower.contains("jeden tag") -> {
                rrule = "FREQ=DAILY"
                working = working.replace(Regex("""\b(täglich|daily|jeden\s+tag)\b""", RegexOption.IGNORE_CASE), "")
            }
            lower.contains("wöchentlich") || lower.contains("weekly") || lower.contains("jede woche") -> {
                rrule = "FREQ=WEEKLY"
                working = working.replace(Regex("""\b(wöchentlich|weekly|jede\s+woche)\b""", RegexOption.IGNORE_CASE), "")
            }
            lower.contains("monatlich") || lower.contains("monthly") || lower.contains("jeden monat") -> {
                rrule = "FREQ=MONTHLY"
                working = working.replace(Regex("""\b(monatlich|monthly|jeden\s+monat)\b""", RegexOption.IGNORE_CASE), "")
            }
            lower.contains("jährlich") || lower.contains("yearly") -> {
                rrule = "FREQ=YEARLY"
                working = working.replace(Regex("""\b(jährlich|yearly)\b""", RegexOption.IGNORE_CASE), "")
            }
        }

        if (!dateDetected) {
            val dmyMatch = DATE_DMY_REGEX.find(working)
            if (dmyMatch != null) {
                val day = dmyMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 31) ?: now.dayOfMonth
                val month = dmyMatch.groupValues[2].toIntOrNull()?.coerceIn(1, 12) ?: now.monthValue
                val rawYear = dmyMatch.groupValues[3]
                val year = when {
                    rawYear.isBlank() -> now.year
                    rawYear.length == 2 -> 2000 + rawYear.toInt()
                    else -> rawYear.toInt()
                }
                runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let {
                    targetDate = if (rawYear.isBlank() && it.isBefore(now.toLocalDate())) it.plusYears(1) else it
                    dateDetected = true
                }
                working = working.removeRange(dmyMatch.range)
            }
        }

        if (!dateDetected) {
            for ((word, dow) in weekdayMap) {
                val regex = Regex("""\b(?:am\s+|nächsten\s+|jeden\s+|on\s+|next\s+)?$word\b""", RegexOption.IGNORE_CASE)
                val match = regex.find(working)
                if (match != null) {
                    val isRepeating = match.value.lowercase().contains("jeden")
                    targetDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dow))
                    if (isRepeating && rrule == null) {
                        rrule = "FREQ=WEEKLY;BYDAY=${ParsedRRule.dayToIcs(dow)}"
                    }
                    working = working.removeRange(match.range)
                    dateDetected = true
                    break
                }
            }
        }

        val allDay = lower.contains("ganztägig") || lower.contains("ganztag") || lower.contains("all day")
        if (allDay) {
            working = working.replace(Regex("""\b(ganztägig|ganztag|all\s+day)\b""", RegexOption.IGNORE_CASE), "")
        }

        var startTime: LocalTime? = null
        var endTime: LocalTime? = null
        var durationMinutes = defaultDurationMinutes

        val rangeMatch = TIME_RANGE_REGEX.find(working)
        if (rangeMatch != null && !allDay) {
            val sh = rangeMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
            val sm = rangeMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val eh = rangeMatch.groupValues[3].toIntOrNull()?.coerceIn(0, 23) ?: (sh + 1).coerceAtMost(23)
            val em = rangeMatch.groupValues[4].toIntOrNull()?.coerceIn(0, 59) ?: 0
            startTime = LocalTime.of(sh, sm)
            endTime = LocalTime.of(eh, em)
            working = working.removeRange(rangeMatch.range)
        } else if (!allDay) {
            val ampmMatch = TIME_AMPM_REGEX.find(working)
            val h24Match = TIME_24H_REGEX.find(working)
            val uhrMatch = TIME_UHR_REGEX.find(working)
            when {
                h24Match != null -> {
                    val h = h24Match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val m = h24Match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
                    startTime = LocalTime.of(h, m)
                    working = working.removeRange(h24Match.range)
                }
                uhrMatch != null -> {
                    val h = uhrMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
                    startTime = LocalTime.of(h, 0)
                    working = working.removeRange(uhrMatch.range)
                }
                ampmMatch != null -> {
                    var h = ampmMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: 9
                    val m = ampmMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val suffix = ampmMatch.groupValues[3].lowercase()
                    if (suffix == "pm" && h < 12) h += 12
                    if (suffix == "am" && h == 12) h = 0
                    startTime = LocalTime.of(h, m)
                    working = working.removeRange(ampmMatch.range)
                }
            }
        }

        val durMatch = DURATION_REGEX.find(working)
        if (durMatch != null && endTime == null && !allDay) {
            val qty = durMatch.groupValues[1].toIntOrNull() ?: defaultDurationMinutes
            val unit = durMatch.groupValues[2].lowercase()
            durationMinutes = if (unit.startsWith("h") || unit.startsWith("st")) qty * 60 else qty
            working = working.removeRange(durMatch.range)
        }

        var reminderMins = defaultReminderMinutes
        val remMatch = REMINDER_REGEX.find(working)
        if (remMatch != null) {
            val qty = remMatch.groupValues[1].toIntOrNull() ?: defaultReminderMinutes
            val unit = remMatch.groupValues[2].lowercase()
            reminderMins = if (unit.startsWith("h") || unit.startsWith("st")) qty * 60 else qty
            working = working.removeRange(remMatch.range)
        }

        var location = ""
        val locMatch = LOCATION_REGEX.find(working)
        if (locMatch != null) {
            location = locMatch.groupValues[1].trim().trimEnd('.', ',', ';')
            working = working.removeRange(locMatch.range)
        }

        val cleanedTitle = working
            .replace(Regex("""\b(am|um|at|on|mit)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim('-', ',', ';')
            .trim()
            .ifBlank { input.trim() }

        val resolvedStart = if (allDay) {
            targetDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else {
            val fallbackTime = LocalTime.of(((now.hour + 1) % 24), 0)
            val sTime = startTime ?: fallbackTime
            LocalDateTime.of(targetDate, sTime).atZone(zoneId).toInstant().toEpochMilli()
        }

        val resolvedEnd = if (allDay) {
            targetDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } else if (endTime != null) {
            var endDt = LocalDateTime.of(targetDate, endTime)
            if (startTime != null && !endTime.isAfter(startTime)) {
                endDt = endDt.plusDays(1)
            }
            endDt.atZone(zoneId).toInstant().toEpochMilli()
        } else {
            resolvedStart + durationMinutes * 60_000L
        }

        return ParsedEventDraft(
            title = cleanedTitle,
            startMillis = resolvedStart,
            endMillis = resolvedEnd,
            allDay = allDay,
            location = location,
            description = "",
            rrule = rrule,
            reminderMinutes = listOf(reminderMins),
            confidenceNote = "Lokal auf dem Gerät erkannt",
            usedAi = false
        )
    }
}
