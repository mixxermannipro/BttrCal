package com.bttrcal.app.data.ai

import com.bttrcal.app.data.local.PreferencesRepository
import com.bttrcal.app.domain.engine.LocalEventParser
import com.bttrcal.app.domain.engine.ScheduleAnalyzer
import com.bttrcal.app.domain.model.AiProviderType
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.FreeSlotSuggestion
import com.bttrcal.app.domain.model.ParsedEventDraft
import com.bttrcal.app.domain.model.ScheduleBriefing
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AiCalendarService(
    private val aiClient: AiClient,
    private val preferences: PreferencesRepository
) {

    private fun canUseRemoteAi(): Boolean {
        val cfg = preferences.settings.value.aiConfig
        if (!cfg.enabled) return false
        return cfg.provider == AiProviderType.CUSTOM_OPENAI_COMPATIBLE ||
            !preferences.getActiveAiKey(cfg.provider).isNullOrBlank()
    }

    suspend fun parseNaturalLanguageEvent(
        input: String,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        calendars: List<CalendarSource>
    ): ParsedEventDraft {
        val settings = preferences.settings.value
        val localDraft = LocalEventParser.parse(
            input = input,
            now = now,
            zoneId = zoneId,
            defaultDurationMinutes = settings.defaultEventDurationMinutes,
            defaultReminderMinutes = settings.defaultReminderMinutes
        )
        if (!canUseRemoteAi() || input.isBlank()) return localDraft

        val calOptions = calendars.joinToString(", ") { "${it.id}:${it.name}" }
        val systemPrompt = """
            Du bist ein präziser Kalender-Parser. Extrahiere exakt ein JSON-Objekt ohne Markdown-Block:
            {
              "title": "kurzer Titel",
              "startIso": "YYYY-MM-DDTHH:MM:SS",
              "endIso": "YYYY-MM-DDTHH:MM:SS",
              "allDay": false,
              "location": "",
              "description": "",
              "rrule": null,
              "reminderMinutes": [15],
              "calendarId": null
            }
            Regeln:
            - Aktuelle Ortszeit: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} (Zeitzone: ${zoneId.id})
            - Verfügbare Kalender (id:name): $calOptions
            - Falls Dauer fehlt, nutze ${settings.defaultEventDurationMinutes} Minuten.
            - rrule nur im RFC-5545-Format ohne "RRULE:"-Präfix, z.B. "FREQ=WEEKLY;BYDAY=MO" oder null.
        """.trimIndent()

        val apiKey = preferences.getActiveAiKey(settings.aiConfig.provider)
        val reply = aiClient.complete(settings.aiConfig, apiKey, systemPrompt, input).getOrNull()
            ?: return localDraft

        return parseSingleJsonDraft(reply, zoneId, calendars, localDraft)
    }

    suspend fun extractEventsFromText(
        rawText: String,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        calendars: List<CalendarSource>
    ): List<ParsedEventDraft> {
        if (rawText.isBlank()) return emptyList()
        val settings = preferences.settings.value

        if (!canUseRemoteAi()) {
            return rawText.lines()
                .map { it.trim() }
                .filter { it.length >= 4 }
                .take(10)
                .map { line ->
                    LocalEventParser.parse(
                        input = line,
                        now = now,
                        zoneId = zoneId,
                        defaultDurationMinutes = settings.defaultEventDurationMinutes,
                        defaultReminderMinutes = settings.defaultReminderMinutes
                    )
                }
        }

        val systemPrompt = """
            Extrahiere alle Termine aus dem Text als reines JSON-Array ohne Markdown:
            [{"title":"...","startIso":"YYYY-MM-DDTHH:MM:SS","endIso":"YYYY-MM-DDTHH:MM:SS","allDay":false,"location":"","description":"","rrule":null,"reminderMinutes":[15]}]
            Aktuelle Ortszeit: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} (${zoneId.id}).
            Wenn keine Termine enthalten sind, antworte mit [].
        """.trimIndent()

        val apiKey = preferences.getActiveAiKey(settings.aiConfig.provider)
        val reply = aiClient.complete(settings.aiConfig, apiKey, systemPrompt, rawText, maxTokens = 1200).getOrNull()
        if (reply == null) {
            return listOf(LocalEventParser.parse(rawText.take(240), now, zoneId))
        }

        val extracted = parseArrayJsonDrafts(reply, zoneId, calendars)
        return extracted.ifEmpty {
            listOf(LocalEventParser.parse(rawText.take(240), now, zoneId))
        }
    }

    suspend fun generateBriefing(
        periodTitle: String,
        occurrences: List<EventOccurrence>,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ScheduleBriefing {
        val local = ScheduleAnalyzer.buildLocalBriefing(
            periodTitle = periodTitle,
            occurrences = occurrences,
            rangeStartMillis = rangeStartMillis,
            rangeEndMillis = rangeEndMillis,
            zoneId = zoneId
        )
        if (!canUseRemoteAi() || occurrences.isEmpty()) return local

        val timeFmt = DateTimeFormatter.ofPattern("EEE dd.MM HH:mm")
        val digest = occurrences.take(30).joinToString("\n") { occ ->
            val s = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zoneId).format(timeFmt)
            val e = Instant.ofEpochMilli(occ.occurrenceEndMillis).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))
            "- ${occ.event.title} ($s-$e, Kalender: ${occ.calendarName}, Ort: ${occ.event.location.ifBlank { "-" }})"
        }

        val systemPrompt = """
            Du bist ein minimalistischer persönlicher Kalender-Assistent im Apple-Stil.
            Antworte ausschließlich mit gültigem JSON ohne Markdown:
            {"summary":"2 prägnante Sätze auf Deutsch","highlights":["Punkt 1","Punkt 2","Punkt 3"]}
            Erwähne Überschneidungen, Reisezeiten und Fokus-Blöcke hilfreich und kurz.
        """.trimIndent()

        val userPrompt = "Zeitraum: $periodTitle\nKonflikte: ${local.conflictsCount}\nTermine:\n$digest"
        val settings = preferences.settings.value
        val apiKey = preferences.getActiveAiKey(settings.aiConfig.provider)
        val reply = aiClient.complete(settings.aiConfig, apiKey, systemPrompt, userPrompt, maxTokens = 450).getOrNull()
            ?: return local

        return runCatching {
            val obj = JSONObject(stripCodeFences(reply))
            val summary = obj.optString("summary", local.summary).ifBlank { local.summary }
            val hlArr = obj.optJSONArray("highlights")
            val highlights = mutableListOf<String>()
            if (hlArr != null) {
                for (i in 0 until hlArr.length()) {
                    val text = hlArr.optString(i).trim()
                    if (text.isNotEmpty()) highlights += text
                }
            }
            local.copy(
                summary = summary,
                highlights = highlights.ifEmpty { local.highlights },
                usedAi = true
            )
        }.getOrElse { local }
    }

    suspend fun suggestSmartSlots(
        goalText: String,
        occurrences: List<EventOccurrence>,
        startDate: LocalDate,
        durationMinutes: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<FreeSlotSuggestion> {
        val localSlots = ScheduleAnalyzer.findFreeSlots(
            occurrences = occurrences,
            startDate = startDate,
            days = 7,
            durationMinutes = durationMinutes,
            zoneId = zoneId
        )
        if (!canUseRemoteAi() || localSlots.isEmpty() || goalText.isBlank()) return localSlots

        val fmt = DateTimeFormatter.ofPattern("EEE dd.MM HH:mm")
        val slotsDigest = localSlots.mapIndexed { idx, s ->
            val st = Instant.ofEpochMilli(s.startMillis).atZone(zoneId).format(fmt)
            "$idx: $st (${s.durationMinutes} Min) - ${s.reason}"
        }.joinToString("\n")

        val systemPrompt = """
            Wähle aus den freien Zeitfenstern die 3 besten für das Ziel des Nutzers aus.
            Antworte nur mit JSON-Array: [{"index":0,"label":"Optimal","reason":"kurze Begründung"}]
        """.trimIndent()

        val settings = preferences.settings.value
        val apiKey = preferences.getActiveAiKey(settings.aiConfig.provider)
        val reply = aiClient.complete(
            settings.aiConfig,
            apiKey,
            systemPrompt,
            "Ziel: $goalText\nFreie Slots:\n$slotsDigest",
            maxTokens = 350
        ).getOrNull() ?: return localSlots

        return runCatching {
            val arr = JSONArray(stripCodeFences(reply))
            val enhanced = mutableListOf<FreeSlotSuggestion>()
            val usedIndices = mutableSetOf<Int>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idx = obj.optInt("index", -1)
                if (idx in localSlots.indices && idx !in usedIndices) {
                    usedIndices += idx
                    val base = localSlots[idx]
                    enhanced += base.copy(
                        scoreLabel = obj.optString("label", base.scoreLabel).ifBlank { base.scoreLabel },
                        reason = obj.optString("reason", base.reason).ifBlank { base.reason }
                    )
                }
            }
            localSlots.forEachIndexed { idx, slot ->
                if (idx !in usedIndices) enhanced += slot
            }
            enhanced
        }.getOrElse { localSlots }
    }

    private fun parseSingleJsonDraft(
        rawReply: String,
        zoneId: ZoneId,
        calendars: List<CalendarSource>,
        fallback: ParsedEventDraft
    ): ParsedEventDraft {
        return runCatching {
            val obj = JSONObject(stripCodeFences(rawReply))
            jsonToDraft(obj, zoneId, calendars, usedAi = true) ?: fallback
        }.getOrElse { fallback }
    }

    private fun parseArrayJsonDrafts(
        rawReply: String,
        zoneId: ZoneId,
        calendars: List<CalendarSource>
    ): List<ParsedEventDraft> {
        return runCatching {
            val arr = JSONArray(stripCodeFences(rawReply))
            (0 until arr.length()).mapNotNull { i ->
                jsonToDraft(arr.getJSONObject(i), zoneId, calendars, usedAi = true)
            }
        }.getOrDefault(emptyList())
    }

    private fun jsonToDraft(
        obj: JSONObject,
        zoneId: ZoneId,
        calendars: List<CalendarSource>,
        usedAi: Boolean
    ): ParsedEventDraft? {
        val title = obj.optString("title", "").trim()
        val startStr = obj.optString("startIso", "").trim()
        if (title.isBlank() || startStr.isBlank()) return null

        val allDay = obj.optBoolean("allDay", false)
        val startMillis = parseIsoToMillis(startStr, zoneId) ?: return null
        val endStr = obj.optString("endIso", "").trim()
        val parsedEnd = parseIsoToMillis(endStr, zoneId)
        val endMillis = if (parsedEnd != null && parsedEnd > startMillis) {
            parsedEnd
        } else {
            startMillis + if (allDay) 86_400_000L else 3_600_000L
        }

        val remList = mutableListOf<Int>()
        val remArr = obj.optJSONArray("reminderMinutes")
        if (remArr != null) {
            for (i in 0 until remArr.length()) {
                val m = remArr.optInt(i, -1)
                if (m >= 0) remList += m
            }
        }

        val rawCalId = obj.optString("calendarId", "").takeIf { it.isNotBlank() && it != "null" }
        val validCalId = calendars.firstOrNull { it.id == rawCalId && !it.isReadOnly }?.id
        val rruleRaw = obj.optString("rrule", "").takeIf { it.isNotBlank() && it != "null" }

        return ParsedEventDraft(
            title = title,
            startMillis = startMillis,
            endMillis = endMillis,
            allDay = allDay,
            location = obj.optString("location", "").takeIf { it != "null" }.orEmpty(),
            description = obj.optString("description", "").takeIf { it != "null" }.orEmpty(),
            rrule = rruleRaw?.removePrefix("RRULE:")?.trim(),
            reminderMinutes = remList.ifEmpty { listOf(15) },
            suggestedCalendarId = validCalId,
            confidenceNote = if (usedAi) "Erstellt via BYOK-KI" else "Lokal erkannt",
            usedAi = usedAi
        )
    }

    private fun parseIsoToMillis(raw: String, zoneId: ZoneId): Long? {
        if (raw.isBlank()) return null
        return runCatching {
            when {
                raw.length == 10 -> LocalDate.parse(raw).atStartOfDay(zoneId).toInstant().toEpochMilli()
                raw.endsWith("Z") || raw.contains("+") -> OffsetDateTime.parse(raw).toInstant().toEpochMilli()
                else -> LocalDateTime.parse(raw.take(19)).atZone(zoneId).toInstant().toEpochMilli()
            }
        }.getOrNull()
    }

    private fun stripCodeFences(raw: String): String {
        val trimmed = raw.trim()
        val withoutStart = if (trimmed.startsWith("```")) {
            trimmed.substringAfter('\n')
        } else {
            trimmed
        }
        return withoutStart.removeSuffix("```").trim()
    }
}
