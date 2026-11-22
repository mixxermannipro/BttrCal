package com.bttrcal.app.domain.model

import java.time.DayOfWeek

enum class CalendarAccountType(val labelDe: String) {
    LOCAL("Lokal (Gerät)"),
    ANDROID_PROVIDER("Google / System-Kalender"),
    GOOGLE_API("Google Calendar API"),
    CALDAV("CalDAV (Nextcloud / iCloud / WebDAV)"),
    ICS_SUBSCRIPTION("ICS / WebCal-Abo")
}

enum class EventAvailability(val labelDe: String) {
    BUSY("Beschäftigt"),
    FREE("Frei"),
    TENTATIVE("Unter Vorbehalt")
}

enum class SyncState {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE
}

enum class ThemeMode(val labelDe: String) {
    LIGHT("Hell"),
    DARK("Dunkel"),
    LIQUID_GLASS("Liquid Glass")
}

enum class GlassVariant(val labelDe: String) {
    LIGHT_GLASS("Helles Glas"),
    DARK_GLASS("Dunkles Glas")
}

enum class CalendarViewMode(val shortLabelDe: String, val labelDe: String) {
    DAY("Tag", "Tagesansicht"),
    THREE_DAY("3T", "3-Tage-Ansicht"),
    WEEK("Woche", "Wochenansicht"),
    MONTH("Monat", "Monatsansicht"),
    YEAR("Jahr", "Jahresansicht"),
    AGENDA("Agenda", "Agenda")
}

enum class SyncIntervalOption(val minutes: Long, val labelDe: String) {
    MANUAL(0L, "Aus (nur manuell)"),
    MIN_15(15L, "Alle 15 Minuten"),
    MIN_30(30L, "Alle 30 Minuten"),
    HOUR_1(60L, "Jede Stunde"),
    HOUR_2(120L, "Alle 2 Stunden"),
    HOUR_4(240L, "Alle 4 Stunden"),
    HOUR_6(360L, "Alle 6 Stunden"),
    HOUR_12(720L, "Alle 12 Stunden"),
    HOUR_24(1440L, "Alle 24 Stunden");

    companion object {
        fun fromMinutes(minutes: Long): SyncIntervalOption =
            entries.firstOrNull { it.minutes == minutes } ?: MIN_30
    }
}

enum class AiProviderType(
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String
) {
    OPENAI("OpenAI", "gpt-4o-mini", "https://api.openai.com/v1"),
    GEMINI("Google Gemini", "gemini-2.5-flash", "https://generativelanguage.googleapis.com/v1beta"),
    ANTHROPIC("Anthropic Claude", "claude-3-5-haiku-latest", "https://api.anthropic.com/v1"),
    GROQ("Groq", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1"),
    CUSTOM_OPENAI_COMPATIBLE("OpenAI-Kompatibel / Lokal", "llama3.2", "http://127.0.0.1:11434/v1")
}

data class CalendarAccount(
    val id: String,
    val name: String,
    val emailOrUser: String,
    val accountType: CalendarAccountType,
    val serverUrl: String = "",
    val lastSyncedAt: Long = 0L,
    val enabled: Boolean = true,
    val syncStatusMessage: String = ""
)

data class CalendarSource(
    val id: String,
    val accountId: String,
    val name: String,
    val colorArgb: Int,
    val accountType: CalendarAccountType,
    val isVisible: Boolean = true,
    val isReadOnly: Boolean = false,
    val isDefault: Boolean = false,
    val androidCalendarId: Long? = null,
    val remoteId: String? = null,
    val syncUrl: String? = null,
    val accountName: String? = null,
    val accountOwnerType: String? = null
)

data class CalendarEvent(
    val id: String,
    val uid: String,
    val calendarId: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val url: String = "",
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val timeZoneId: String = "Europe/Berlin",
    val rrule: String? = null,
    val exdates: List<Long> = emptyList(),
    val reminderMinutes: List<Int> = listOf(15),
    val availability: EventAvailability = EventAvailability.BUSY,
    val colorOverrideArgb: Int? = null,
    val attendeesSummary: String = "",
    val androidEventId: Long? = null,
    val remoteId: String? = null,
    val etag: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val effectiveDurationMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(15L * 60L * 1000L)
}

data class EventOccurrence(
    val event: CalendarEvent,
    val calendarColorArgb: Int,
    val calendarName: String,
    val occurrenceStartMillis: Long,
    val occurrenceEndMillis: Long,
    val isRecurringInstance: Boolean
) {
    val displayColorArgb: Int
        get() = event.colorOverrideArgb ?: calendarColorArgb
}

data class SyncLogEntry(
    val id: String,
    val timestamp: Long,
    val sourceLabel: String,
    val success: Boolean,
    val pulledCount: Int,
    val pushedCount: Int,
    val message: String
)

data class AiConfig(
    val provider: AiProviderType = AiProviderType.GEMINI,
    val model: String = AiProviderType.GEMINI.defaultModel,
    val baseUrl: String = AiProviderType.GEMINI.defaultBaseUrl,
    val enabled: Boolean = true,
    val hasStoredKey: Boolean = false
)

data class ParsedEventDraft(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val location: String = "",
    val description: String = "",
    val rrule: String? = null,
    val reminderMinutes: List<Int> = listOf(15),
    val suggestedCalendarId: String? = null,
    val confidenceNote: String = "",
    val usedAi: Boolean = false
)

data class ScheduleConflict(
    val first: EventOccurrence,
    val second: EventOccurrence,
    val overlapMinutes: Long
)

data class TravelBufferWarning(
    val first: EventOccurrence,
    val second: EventOccurrence,
    val gapMinutes: Long,
    val messageDe: String
)

data class UpcomingMilestone(
    val occurrence: EventOccurrence,
    val daysUntil: Long,
    val labelDe: String
)

data class DailyFocusMetrics(
    val focusScore: Int,
    val busyMinutes: Long,
    val freeMinutes: Long,
    val longestFreeBlockMinutes: Long,
    val summaryDe: String
)

data class FreeSlotSuggestion(
    val startMillis: Long,
    val endMillis: Long,
    val durationMinutes: Int,
    val scoreLabel: String,
    val reason: String
)

data class ScheduleBriefing(
    val periodTitle: String,
    val summary: String,
    val highlights: List<String>,
    val conflictsCount: Int,
    val totalEventsCount: Int,
    val freeMinutes: Long,
    val busyMinutes: Long,
    val usedAi: Boolean
)

data class WidgetStyleConfig(
    val themeMode: ThemeMode = ThemeMode.LIQUID_GLASS,
    val glassVariant: GlassVariant = GlassVariant.DARK_GLASS,
    val glassOpacity: Float = 0.78f,
    val showWeekNumbers: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val compactMode: Boolean = false
)

enum class AppTab(val labelDe: String) {
    CALENDAR("Kalender"),
    AI("KI"),
    SYNC("Konten")
}

object CalendarColors {
    val palette: List<Int> = listOf(
        0xFF0A84FF.toInt(), // Apple Blue
        0xFF34C759.toInt(), // Emerald Green
        0xFFFF9500.toInt(), // Warm Amber
        0xFFFF3B30.toInt(), // Coral Red
        0xFFAF52DE.toInt(), // Royal Purple
        0xFF30B0C7.toInt(), // Liquid Teal
        0xFFFF2D55.toInt(), // Berry Pink
        0xFF5E5CE6.toInt(), // Indigo
        0xFF64D2FF.toInt(), // Sky Cyan
        0xFF8E8E93.toInt()  // Slate Neutral
    )
}
