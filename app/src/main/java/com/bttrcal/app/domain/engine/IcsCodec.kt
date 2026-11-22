package com.bttrcal.app.domain.engine

import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.domain.model.SyncState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object IcsCodec {

    private val UTC_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun parseIcs(
        icsContent: String,
        calendarId: String,
        defaultZoneId: ZoneId = ZoneId.systemDefault()
    ): List<CalendarEvent> {
        val unfolded = unfoldLines(icsContent)
        val events = mutableListOf<CalendarEvent>()

        var inEvent = false
        var inAlarm = false
        var uid = ""
        var summary = ""
        var description = ""
        var location = ""
        var url = ""
        var rrule: String? = null
        var transp = "OPAQUE"
        var startMillis: Long? = null
        var endMillis: Long? = null
        var durationMillis: Long? = null
        var allDay = false
        var tzId = defaultZoneId.id
        val reminders = mutableListOf<Int>()
        val exdates = mutableListOf<Long>()

        for (rawLine in unfolded) {
            val line = rawLine.trimEnd('\r')
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    inAlarm = false
                    uid = ""
                    summary = ""
                    description = ""
                    location = ""
                    url = ""
                    rrule = null
                    transp = "OPAQUE"
                    startMillis = null
                    endMillis = null
                    durationMillis = null
                    allDay = false
                    tzId = defaultZoneId.id
                    reminders.clear()
                    exdates.clear()
                }
                line.equals("END:VEVENT", ignoreCase = true) && inEvent -> {
                    inEvent = false
                    inAlarm = false
                    val s = startMillis
                    if (s != null) {
                        val e = endMillis ?: (s + (durationMillis ?: if (allDay) 86_400_000L else 3_600_000L))
                        val safeEnd = if (e <= s) s + (if (allDay) 86_400_000L else 1_800_000L) else e
                        val resolvedUid = uid.ifBlank { UUID.randomUUID().toString() }
                        val availability = when (transp.uppercase()) {
                            "TRANSPARENT" -> EventAvailability.FREE
                            else -> EventAvailability.BUSY
                        }
                        val now = System.currentTimeMillis()
                        events += CalendarEvent(
                            id = "${calendarId}_$resolvedUid",
                            uid = resolvedUid,
                            calendarId = calendarId,
                            title = summary.ifBlank { "Ohne Titel" },
                            description = description,
                            location = location,
                            url = url,
                            startMillis = s,
                            endMillis = safeEnd,
                            allDay = allDay,
                            timeZoneId = tzId,
                            rrule = rrule,
                            exdates = exdates.toList(),
                            reminderMinutes = reminders.distinct().ifEmpty { listOf(15) },
                            availability = availability,
                            syncState = SyncState.SYNCED,
                            updatedAt = now,
                            createdAt = now
                        )
                    }
                }
                !inEvent -> continue
                line.equals("BEGIN:VALARM", ignoreCase = true) -> inAlarm = true
                line.equals("END:VALARM", ignoreCase = true) -> inAlarm = false
                inAlarm -> {
                    if (line.startsWith("TRIGGER", ignoreCase = true)) {
                        val value = line.substringAfter(':', "")
                        parseTriggerMinutes(value)?.let { reminders += it }
                    }
                }
                else -> {
                    val colonIndex = line.indexOf(':')
                    if (colonIndex <= 0) continue
                    val left = line.substring(0, colonIndex)
                    val value = line.substring(colonIndex + 1)
                    val parts = left.split(';')
                    val propName = parts.first().uppercase()
                    val params = parts.drop(1).associate { param ->
                        val eq = param.indexOf('=')
                        if (eq > 0) {
                            param.substring(0, eq).uppercase() to param.substring(eq + 1).trim('"')
                        } else {
                            param.uppercase() to ""
                        }
                    }

                    when (propName) {
                        "UID" -> uid = unescapeText(value.trim())
                        "SUMMARY" -> summary = unescapeText(value)
                        "DESCRIPTION" -> description = unescapeText(value)
                        "LOCATION" -> location = unescapeText(value)
                        "URL" -> url = value.trim()
                        "RRULE" -> rrule = value.trim()
                        "TRANSP" -> transp = value.trim()
                        "DTSTART" -> {
                            val parsed = parseIcsDateTime(value.trim(), params, defaultZoneId)
                            if (parsed != null) {
                                startMillis = parsed.first
                                allDay = parsed.second
                                tzId = parsed.third
                            }
                        }
                        "DTEND" -> {
                            val parsed = parseIcsDateTime(value.trim(), params, defaultZoneId)
                            if (parsed != null) {
                                endMillis = parsed.first
                            }
                        }
                        "DURATION" -> durationMillis = parseIsoDurationMillis(value.trim())
                        "EXDATE" -> {
                            value.split(',').forEach { token ->
                                parseIcsDateTime(token.trim(), params, defaultZoneId)?.let {
                                    exdates += it.first
                                }
                            }
                        }
                    }
                }
            }
        }
        return events
    }

    fun exportToIcs(events: List<CalendarEvent>, calendarName: String = "BttrCal"): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//BttrCal//Android Calendar//DE\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("X-WR-CALNAME:${escapeText(calendarName)}\r\n")

        for (event in events) {
            val zone = runCatching { ZoneId.of(event.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:${escapeText(event.uid)}\r\n")
            sb.append("DTSTAMP:${Instant.ofEpochMilli(event.updatedAt).atZone(ZoneOffset.UTC).format(UTC_FORMAT)}\r\n")

            if (event.allDay) {
                val startDate = Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDate()
                val endDate = Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalDate()
                val safeEndDate = if (!endDate.isAfter(startDate)) startDate.plusDays(1) else endDate
                sb.append("DTSTART;VALUE=DATE:${startDate.format(DATE_FORMAT)}\r\n")
                sb.append("DTEND;VALUE=DATE:${safeEndDate.format(DATE_FORMAT)}\r\n")
            } else {
                val startUtc = Instant.ofEpochMilli(event.startMillis).atZone(ZoneOffset.UTC)
                val endUtc = Instant.ofEpochMilli(event.endMillis).atZone(ZoneOffset.UTC)
                sb.append("DTSTART:${startUtc.format(UTC_FORMAT)}\r\n")
                sb.append("DTEND:${endUtc.format(UTC_FORMAT)}\r\n")
            }

            sb.append("SUMMARY:${escapeText(event.title)}\r\n")
            if (event.description.isNotBlank()) {
                sb.append("DESCRIPTION:${escapeText(event.description)}\r\n")
            }
            if (event.location.isNotBlank()) {
                sb.append("LOCATION:${escapeText(event.location)}\r\n")
            }
            if (event.url.isNotBlank()) {
                sb.append("URL:${event.url.trim()}\r\n")
            }
            if (!event.rrule.isNullOrBlank()) {
                sb.append("RRULE:${event.rrule.removePrefix("RRULE:").trim()}\r\n")
            }
            val transp = if (event.availability == EventAvailability.FREE) "TRANSPARENT" else "OPAQUE"
            sb.append("TRANSP:$transp\r\n")

            event.reminderMinutes.filter { it >= 0 }.forEach { mins ->
                sb.append("BEGIN:VALARM\r\n")
                sb.append("ACTION:DISPLAY\r\n")
                sb.append("DESCRIPTION:Erinnerung\r\n")
                sb.append("TRIGGER:-PT${mins}M\r\n")
                sb.append("END:VALARM\r\n")
            }
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun unfoldLines(content: String): List<String> {
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split('\n')
        val out = mutableListOf<String>()
        for (line in lines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.drop(1)
            } else if (line.isNotEmpty()) {
                out += line
            }
        }
        return out
    }

    private fun parseIcsDateTime(
        raw: String,
        params: Map<String, String>,
        defaultZoneId: ZoneId
    ): Triple<Long, Boolean, String>? {
        val isDateOnly = params["VALUE"]?.equals("DATE", ignoreCase = true) == true ||
            (raw.length == 8 && raw.all { it.isDigit() })
        val paramTz = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        val zone = paramTz ?: defaultZoneId

        return runCatching {
            when {
                isDateOnly -> {
                    val date = LocalDate.parse(raw.take(8), DATE_FORMAT)
                    val millis = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    Triple(millis, true, zone.id)
                }
                raw.endsWith("Z", ignoreCase = true) -> {
                    val ldt = LocalDateTime.parse(raw.dropLast(1), LOCAL_FORMAT)
                    val millis = ldt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                    Triple(millis, false, defaultZoneId.id)
                }
                else -> {
                    val ldt = LocalDateTime.parse(raw, LOCAL_FORMAT)
                    val millis = ldt.atZone(zone).toInstant().toEpochMilli()
                    Triple(millis, false, zone.id)
                }
            }
        }.getOrNull()
    }

    private fun parseIsoDurationMillis(raw: String): Long? {
        return runCatching {
            val cleaned = raw.trim().uppercase()
            if (!cleaned.startsWith("P") && !cleaned.startsWith("-P")) return null
            if (cleaned.contains("T")) {
                Duration.parse(cleaned).toMillis().let { kotlin.math.abs(it) }
            } else if (cleaned.endsWith("D")) {
                val days = cleaned.removePrefix("P").removeSuffix("D").toLongOrNull() ?: 1L
                days * 86_400_000L
            } else {
                null
            }
        }.getOrNull()
    }

    private fun parseTriggerMinutes(raw: String): Int? {
        val trimmed = raw.trim().uppercase()
        if (!trimmed.startsWith("-P") && !trimmed.startsWith("P")) return null
        val positive = trimmed.removePrefix("-")
        return runCatching {
            when {
                positive == "PT0S" || positive == "PT0M" || positive == "P0D" -> 0
                positive.contains("T") -> {
                    val d = Duration.parse(positive)
                    d.toMinutes().toInt().coerceAtLeast(0)
                }
                positive.endsWith("D") -> {
                    val days = positive.removePrefix("P").removeSuffix("D").toIntOrNull() ?: 1
                    days * 24 * 60
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun escapeText(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

    private fun unescapeText(text: String): String = text
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}
