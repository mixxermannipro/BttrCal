package com.bttrcal.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.MainActivity
import com.bttrcal.app.data.ai.AiTestResult
import com.bttrcal.app.data.local.AppSettings
import com.bttrcal.app.data.sync.SyncScheduler
import com.bttrcal.app.domain.engine.ScheduleAnalyzer
import com.bttrcal.app.domain.model.AiProviderType
import com.bttrcal.app.domain.model.AppTab
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.CalendarViewMode
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.FreeSlotSuggestion
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.ParsedEventDraft
import com.bttrcal.app.domain.model.ScheduleBriefing
import com.bttrcal.app.domain.model.SyncIntervalOption
import com.bttrcal.app.domain.model.ThemeMode
import com.bttrcal.app.domain.model.WidgetStyleConfig
import com.bttrcal.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class EditorState(
    val isOpen: Boolean = false,
    val existingEvent: CalendarEvent? = null,
    val prefillTitle: String = "",
    val prefillLocation: String = "",
    val prefillDescription: String = "",
    val prefillStartMillis: Long = System.currentTimeMillis(),
    val prefillEndMillis: Long = System.currentTimeMillis() + 3_600_000L,
    val prefillAllDay: Boolean = false,
    val prefillRRule: String? = null,
    val prefillReminders: List<Int> = listOf(15),
    val prefillCalendarId: String? = null
)

data class AiUiState(
    val quickInput: String = "",
    val isParsingQuick: Boolean = false,
    val quickDraft: ParsedEventDraft? = null,
    val briefingPeriodDays: Int = 1,
    val isGeneratingBriefing: Boolean = false,
    val briefing: ScheduleBriefing? = null,
    val extractRawText: String = "",
    val isExtracting: Boolean = false,
    val extractedDrafts: List<ParsedEventDraft> = emptyList(),
    val slotGoal: String = "Fokuszeit / Meeting",
    val slotDurationMinutes: Int = 60,
    val isFindingSlots: Boolean = false,
    val freeSlots: List<FreeSlotSuggestion> = emptyList(),
    val isTestingConnection: Boolean = false,
    val testResult: AiTestResult? = null
)

class MainViewModel(private val app: BttrCalApplication) : ViewModel() {

    val settings: StateFlow<AppSettings> = app.preferences.settings
    val calendars = app.repository.calendars
    val accounts = app.repository.accounts
    val syncLogs = app.repository.syncLogs
    val dataRevision = app.repository.dataRevision
    val isSyncing = app.syncOrchestrator.isSyncing

    private val _selectedTab = MutableStateFlow(AppTab.CALENDAR)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _viewMode = MutableStateFlow(app.preferences.settings.value.defaultViewMode)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _detailOccurrence = MutableStateFlow<EventOccurrence?>(null)
    val detailOccurrence: StateFlow<EventOccurrence?> = _detailOccurrence.asStateFlow()

    private val _editorState = MutableStateFlow(EditorState())
    val editorState: StateFlow<EditorState> = _editorState.asStateFlow()

    private val _quickAddOpen = MutableStateFlow(false)
    val quickAddOpen: StateFlow<Boolean> = _quickAddOpen.asStateFlow()

    private val _aiState = MutableStateFlow(AiUiState())
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        if (settings.value.syncOnLaunch) {
            triggerSyncNow(silent = true)
        }
        refreshAiBriefing()
        findSmartSlots()
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
        app.preferences.setDefaultViewMode(mode)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
    }

    fun setVisibleMonth(ym: YearMonth) {
        _visibleMonth.value = ym
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        _visibleMonth.value = YearMonth.from(today)
    }

    fun toggleSearch(active: Boolean) {
        _searchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun getOccurrencesForRange(startMillis: Long, endMillis: Long): List<EventOccurrence> =
        app.repository.getOccurrencesInRange(startMillis, endMillis, onlyVisible = true)

    fun searchEvents(query: String): List<EventOccurrence> =
        app.repository.searchEvents(query)

    fun openEventDetail(occ: EventOccurrence) {
        _detailOccurrence.value = occ
    }

    fun closeEventDetail() {
        _detailOccurrence.value = null
    }

    fun openNewEventEditor(
        date: LocalDate = _selectedDate.value,
        hour: Int? = null
    ) {
        val zone = ZoneId.systemDefault()
        val now = LocalTime.now()
        val startHour = hour ?: ((now.hour + 1) % 24)
        val startDt = LocalDateTime.of(date, LocalTime.of(startHour, 0))
        val dur = settings.value.defaultEventDurationMinutes
        val startMillis = startDt.atZone(zone).toInstant().toEpochMilli()
        val endMillis = startMillis + dur * 60_000L

        _editorState.value = EditorState(
            isOpen = true,
            existingEvent = null,
            prefillStartMillis = startMillis,
            prefillEndMillis = endMillis,
            prefillReminders = listOf(settings.value.defaultReminderMinutes),
            prefillCalendarId = app.repository.defaultWritableCalendarId()
        )
    }

    fun openEditEventEditor(event: CalendarEvent) {
        _detailOccurrence.value = null
        _editorState.value = EditorState(
            isOpen = true,
            existingEvent = event,
            prefillTitle = event.title,
            prefillLocation = event.location,
            prefillDescription = event.description,
            prefillStartMillis = event.startMillis,
            prefillEndMillis = event.endMillis,
            prefillAllDay = event.allDay,
            prefillRRule = event.rrule,
            prefillReminders = event.reminderMinutes,
            prefillCalendarId = event.calendarId
        )
    }

    fun openEditorFromDraft(draft: ParsedEventDraft) {
        _quickAddOpen.value = false
        _editorState.value = EditorState(
            isOpen = true,
            existingEvent = null,
            prefillTitle = draft.title,
            prefillLocation = draft.location,
            prefillDescription = draft.description,
            prefillStartMillis = draft.startMillis,
            prefillEndMillis = draft.endMillis,
            prefillAllDay = draft.allDay,
            prefillRRule = draft.rrule,
            prefillReminders = draft.reminderMinutes,
            prefillCalendarId = draft.suggestedCalendarId ?: app.repository.defaultWritableCalendarId()
        )
    }

    fun closeEditor() {
        _editorState.value = EditorState(isOpen = false)
    }

    fun openQuickAdd() {
        _quickAddOpen.value = true
    }

    fun closeQuickAdd() {
        _quickAddOpen.value = false
    }

    fun saveEvent(event: CalendarEvent) {
        viewModelScope.launch {
            app.repository.saveEvent(event)
            _editorState.value = EditorState(isOpen = false)
            _statusMessage.value = "Termin gespeichert"
        }
    }

    fun duplicateEvent(event: CalendarEvent) {
        viewModelScope.launch {
            val copy = event.copy(
                id = "evt_${UUID.randomUUID()}",
                uid = UUID.randomUUID().toString(),
                title = "${event.title} (Kopie)",
                androidEventId = null,
                remoteId = null,
                etag = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            app.repository.saveEvent(copy)
            _detailOccurrence.value = null
            _statusMessage.value = "Termin dupliziert"
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            app.repository.deleteEvent(eventId)
            _detailOccurrence.value = null
            _editorState.value = EditorState(isOpen = false)
            _statusMessage.value = "Termin gelöscht"
        }
    }

    fun saveDraftDirectly(draft: ParsedEventDraft) {
        viewModelScope.launch {
            val calId = draft.suggestedCalendarId ?: app.repository.defaultWritableCalendarId()
            val now = System.currentTimeMillis()
            val ev = CalendarEvent(
                id = "evt_${UUID.randomUUID()}",
                uid = UUID.randomUUID().toString(),
                calendarId = calId,
                title = draft.title,
                description = draft.description,
                location = draft.location,
                startMillis = draft.startMillis,
                endMillis = draft.endMillis,
                allDay = draft.allDay,
                timeZoneId = ZoneId.systemDefault().id,
                rrule = draft.rrule,
                reminderMinutes = draft.reminderMinutes,
                createdAt = now,
                updatedAt = now
            )
            app.repository.saveEvent(ev)
            _quickAddOpen.value = false
            _statusMessage.value = "„${draft.title}“ eingetragen"
        }
    }

    // --- Intent handling ---

    fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val dateMillis = intent.getLongExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, -1L)
        if (dateMillis > 0L) {
            val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            selectDate(date)
        }
        val eventId = intent.getStringExtra(MainActivity.EXTRA_OPEN_EVENT_ID)
        if (!eventId.isNullOrBlank()) {
            val ev = app.repository.getEventById(eventId)
            if (ev != null) {
                val cal = calendars.value.firstOrNull { it.id == ev.calendarId }
                openEventDetail(
                    EventOccurrence(
                        event = ev,
                        calendarColorArgb = cal?.colorArgb ?: 0xFF0A84FF.toInt(),
                        calendarName = cal?.name ?: "Kalender",
                        occurrenceStartMillis = if (dateMillis > 0L) dateMillis else ev.startMillis,
                        occurrenceEndMillis = if (dateMillis > 0L) dateMillis + ev.effectiveDurationMillis else ev.endMillis,
                        isRecurringInstance = !ev.rrule.isNullOrBlank()
                    )
                )
            }
        }
        if (intent.getBooleanExtra(MainActivity.EXTRA_QUICK_ADD, false)) {
            _selectedTab.value = AppTab.CALENDAR
            openNewEventEditor()
        }
    }

    // --- Theme & Preferences ---

    fun setThemeMode(mode: ThemeMode) {
        app.preferences.setThemeMode(mode)
    }

    fun setGlassVariant(variant: GlassVariant) {
        app.preferences.setGlassVariant(variant)
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        app.preferences.setFirstDayOfWeek(day)
        WidgetUpdater.updateAllWidgets(app)
    }

    fun setShowWeekNumbers(show: Boolean) {
        app.preferences.setShowWeekNumbers(show)
        WidgetUpdater.updateAllWidgets(app)
    }

    fun setDefaultReminderMinutes(minutes: Int) {
        app.preferences.setDefaultReminderMinutes(minutes)
    }

    fun setDefaultDurationMinutes(minutes: Int) {
        app.preferences.setDefaultEventDurationMinutes(minutes)
    }

    fun updateWidgetStyle(style: WidgetStyleConfig) {
        app.preferences.updateWidgetStyle(style)
        WidgetUpdater.updateAllWidgets(app)
    }

    // --- Sync ---

    fun setSyncInterval(option: SyncIntervalOption) {
        app.preferences.setSyncInterval(option)
        SyncScheduler.schedulePeriodicSync(app, option, settings.value.wifiOnlySync)
        _statusMessage.value = "Auto-Sync: ${option.labelDe}"
    }

    fun setWifiOnlySync(wifiOnly: Boolean) {
        app.preferences.setWifiOnlySync(wifiOnly)
        SyncScheduler.schedulePeriodicSync(app, settings.value.syncInterval, wifiOnly)
    }

    fun setSyncOnLaunch(enabled: Boolean) {
        app.preferences.setSyncOnLaunch(enabled)
    }

    fun setSyncAfterEdit(enabled: Boolean) {
        app.preferences.setSyncAfterEdit(enabled)
    }

    fun triggerSyncNow(silent: Boolean = false) {
        viewModelScope.launch {
            val results = app.syncOrchestrator.syncAll()
            if (!silent) {
                val ok = results.count { it.success }
                _statusMessage.value = if (results.isEmpty()) {
                    "Lokale Kalender aktualisiert"
                } else {
                    "Sync abgeschlossen ($ok/${results.size} Quellen)"
                }
            }
        }
    }

    fun createLocalCalendar(name: String, colorArgb: Int) {
        viewModelScope.launch {
            app.repository.createLocalCalendar(name, colorArgb)
            _statusMessage.value = "Kalender „$name“ erstellt"
        }
    }

    fun toggleCalendarVisibility(calendarId: String, visible: Boolean) {
        viewModelScope.launch {
            app.repository.setCalendarVisibility(calendarId, visible)
        }
    }

    fun addCalDavAccount(name: String, url: String, user: String, password: String, colorArgb: Int) {
        viewModelScope.launch {
            app.repository.addCalDavAccount(name, url, user, password, colorArgb)
            triggerSyncNow()
        }
    }

    fun addIcsSubscription(name: String, url: String, colorArgb: Int) {
        viewModelScope.launch {
            app.repository.addIcsSubscription(name, url, colorArgb)
            triggerSyncNow()
        }
    }

    fun addGoogleApiAccount(name: String, email: String, token: String) {
        viewModelScope.launch {
            app.repository.addGoogleApiAccount(name, email, token)
            triggerSyncNow()
        }
    }

    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            app.repository.removeAccount(accountId)
            _statusMessage.value = "Konto entfernt"
        }
    }

    fun importIcsText(icsContent: String, calendarId: String) {
        viewModelScope.launch {
            val count = app.repository.importIcsIntoCalendar(icsContent, calendarId)
            _statusMessage.value = if (count > 0) "$count Termin(e) importiert" else "Keine gültigen Termine in Datei"
        }
    }

    fun shareCalendarIcs(context: Context, calendarId: String, calendarName: String) {
        viewModelScope.launch {
            val ics = app.repository.exportCalendarToIcs(calendarId)
            shareIcsContent(context, "$calendarName.ics", ics)
        }
    }

    fun shareEventIcs(context: Context, event: CalendarEvent) {
        val ics = com.bttrcal.app.domain.engine.IcsCodec.exportToIcs(listOf(event), "BttrCal")
        val safeName = event.title.replace("[^A-Za-z0-9_-]".toRegex(), "_").take(32).ifBlank { "termin" }
        shareIcsContent(context, "$safeName.ics", ics)
    }

    private fun shareIcsContent(context: Context, fileName: String, content: String) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "ICS teilen"))
        }.onFailure {
            _statusMessage.value = "Export fehlgeschlagen: ${it.message}"
        }
    }

    // --- AI Actions ---

    fun updateAiQuickInput(text: String) {
        _aiState.value = _aiState.value.copy(quickInput = text)
    }

    fun parseQuickEvent(inputOverride: String? = null) {
        val input = (inputOverride ?: _aiState.value.quickInput).trim()
        if (input.isBlank()) return
        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isParsingQuick = true)
            val draft = app.aiService.parseNaturalLanguageEvent(
                input = input,
                calendars = calendars.value
            )
            _aiState.value = _aiState.value.copy(
                isParsingQuick = false,
                quickDraft = draft
            )
        }
    }

    fun clearQuickDraft() {
        _aiState.value = _aiState.value.copy(quickDraft = null)
    }

    fun setBriefingPeriodDays(days: Int) {
        _aiState.value = _aiState.value.copy(briefingPeriodDays = days)
        refreshAiBriefing(days)
    }

    fun refreshAiBriefing(days: Int = _aiState.value.briefingPeriodDays) {
        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isGeneratingBriefing = true)
            val zone = ZoneId.systemDefault()
            val startDate = LocalDate.now(zone)
            val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = startDate.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val occs = app.repository.getOccurrencesInRange(startMillis, endMillis, onlyVisible = true, zoneId = zone)
            val title = if (days <= 1) "Heute" else "Nächste $days Tage"
            val briefing = app.aiService.generateBriefing(title, occs, startMillis, endMillis, zone)
            _aiState.value = _aiState.value.copy(
                isGeneratingBriefing = false,
                briefing = briefing
            )
        }
    }

    fun updateExtractRawText(text: String) {
        _aiState.value = _aiState.value.copy(extractRawText = text)
    }

    fun extractEventsFromRawText() {
        val raw = _aiState.value.extractRawText.trim()
        if (raw.isBlank()) return
        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isExtracting = true)
            val drafts = app.aiService.extractEventsFromText(raw, calendars = calendars.value)
            _aiState.value = _aiState.value.copy(
                isExtracting = false,
                extractedDrafts = drafts
            )
        }
    }

    fun updateSlotParams(goal: String, durationMinutes: Int) {
        _aiState.value = _aiState.value.copy(slotGoal = goal, slotDurationMinutes = durationMinutes)
    }

    fun findSmartSlots() {
        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isFindingSlots = true)
            val zone = ZoneId.systemDefault()
            val startDate = LocalDate.now(zone)
            val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = startDate.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val occs = app.repository.getOccurrencesInRange(startMillis, endMillis, onlyVisible = true, zoneId = zone)
            val slots = app.aiService.suggestSmartSlots(
                goalText = _aiState.value.slotGoal,
                occurrences = occs,
                startDate = startDate,
                durationMinutes = _aiState.value.slotDurationMinutes,
                zoneId = zone
            )
            _aiState.value = _aiState.value.copy(
                isFindingSlots = false,
                freeSlots = slots
            )
        }
    }

    fun updateAiProviderSettings(
        provider: AiProviderType,
        model: String,
        baseUrl: String,
        enabled: Boolean,
        newApiKey: String?
    ) {
        app.preferences.updateAiConfig(provider, model, baseUrl, enabled, newApiKey)
        _aiState.value = _aiState.value.copy(testResult = null)
        _statusMessage.value = "KI-Einstellungen gespeichert"
    }

    fun testAiConnection(
        provider: AiProviderType,
        model: String,
        baseUrl: String,
        typedApiKey: String
    ) {
        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isTestingConnection = true, testResult = null)
            val effectiveKey = typedApiKey.ifBlank { app.preferences.getActiveAiKey(provider) }
            val cfg = settings.value.aiConfig.copy(
                provider = provider,
                model = model.ifBlank { provider.defaultModel },
                baseUrl = baseUrl.ifBlank { provider.defaultBaseUrl }
            )
            val res = app.aiClient.testConnection(cfg, effectiveKey)
            _aiState.value = _aiState.value.copy(isTestingConnection = false, testResult = res)
        }
    }

    companion object {
        fun factory(app: BttrCalApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(app) as T
                }
            }
    }
}
