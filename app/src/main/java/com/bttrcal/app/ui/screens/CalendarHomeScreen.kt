package com.bttrcal.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bttrcal.app.domain.model.AiProviderType
import com.bttrcal.app.domain.model.AppTab
import com.bttrcal.app.domain.model.CalendarViewMode
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.ParsedEventDraft
import com.bttrcal.app.ui.MainViewModel
import com.bttrcal.app.ui.OverlayScreen
import com.bttrcal.app.ui.theme.LiquidGlassBackdrop
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import com.bttrcal.app.ui.theme.bounceClick
import com.bttrcal.app.ui.views.AgendaView
import com.bttrcal.app.ui.views.MonthAgendaItemCard
import com.bttrcal.app.ui.views.MonthView
import com.bttrcal.app.ui.views.TimeGridView
import com.bttrcal.app.ui.views.YearView
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BttrCalAppRoot(viewModel: MainViewModel) {
    val palette = LocalBttrPalette.current
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val dataRevision by viewModel.dataRevision.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val overlayScreen by viewModel.overlayScreen.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchAiDraft by viewModel.searchAiDraft.collectAsStateWithLifecycle()
    val isRefiningSearchAi by viewModel.isRefiningSearchAi.collectAsStateWithLifecycle()

    val detailOcc by viewModel.detailOccurrence.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val quickAddOpen by viewModel.quickAddOpen.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMsg) {
        if (!statusMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMsg!!)
            viewModel.clearStatusMessage()
        }
    }

    val byokActive = settings.aiConfig.enabled &&
        (settings.aiConfig.hasStoredKey || settings.aiConfig.provider == AiProviderType.CUSTOM_OPENAI_COMPATIBLE)

    LiquidGlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = overlayScreen to selectedTab,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(tween(150)))
                },
                label = "root_screen_transition"
            ) { (overlay, tab) ->
                when (overlay) {
                    OverlayScreen.WIDGETS -> WidgetStudioScreen(
                        widgetStyle = settings.widgetStyle,
                        dataRevision = dataRevision,
                        onBack = { viewModel.closeOverlayScreen() },
                        loadOccurrences = { s, e -> viewModel.getOccurrencesForRange(s, e) },
                        onUpdateWidgetStyle = { viewModel.updateWidgetStyle(it) },
                        onStatusMessage = { viewModel.showStatus(it) }
                    )
                    OverlayScreen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        aiState = aiState,
                        onBack = { viewModel.closeOverlayScreen() },
                        onSetThemeMode = { viewModel.setThemeMode(it) },
                        onSetFirstDayOfWeek = { viewModel.setFirstDayOfWeek(it) },
                        onSetShowWeekNumbers = { viewModel.setShowWeekNumbers(it) },
                        onSetDefaultDuration = { viewModel.setDefaultDurationMinutes(it) },
                        onSetDefaultReminder = { viewModel.setDefaultReminderMinutes(it) },
                        onSaveAiConfig = { p, m, u, en, k -> viewModel.updateAiProviderSettings(p, m, u, en, k) },
                        onTestAiConnection = { p, m, u, k -> viewModel.testAiConnection(p, m, u, k) }
                    )
                    OverlayScreen.NONE -> when (tab) {
                        AppTab.CALENDAR -> CalendarTabContent(
                            viewMode = viewMode,
                            selectedDate = selectedDate,
                            visibleMonth = visibleMonth,
                            searchActive = searchActive,
                            searchQuery = searchQuery,
                            searchAiDraft = searchAiDraft,
                            isRefiningSearchAi = isRefiningSearchAi,
                            byokActive = byokActive,
                            firstDayOfWeek = settings.firstDayOfWeek,
                            showWeekNumbers = settings.showWeekNumbers,
                            dataRevision = dataRevision,
                            loadOccurrences = { s, e -> viewModel.getOccurrencesForRange(s, e) },
                            searchEvents = { q -> viewModel.searchEvents(q) },
                            parseSearchLocally = { q -> viewModel.parseSearchLocally(q) },
                            onRefineSearchWithAi = { q -> viewModel.refineSearchWithAi(q) },
                            onSaveParsedFromSearch = { draft ->
                                viewModel.saveDraftDirectly(draft)
                                val targetDate = Instant.ofEpochMilli(draft.startMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                viewModel.selectDate(targetDate)
                                viewModel.toggleSearch(false)
                            },
                            onPrefillEditorFromSearch = { draft ->
                                viewModel.toggleSearch(false)
                                viewModel.openEditorFromDraft(draft)
                            },
                            onViewModeChanged = { viewModel.setViewMode(it) },
                            onSelectDate = { viewModel.selectDate(it) },
                            onVisibleMonthChanged = { viewModel.setVisibleMonth(it) },
                            onJumpToday = { viewModel.jumpToToday() },
                            onToggleSearch = { viewModel.toggleSearch(it) },
                            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                            onTriggerSync = { viewModel.triggerSyncNow() },
                            onOpenWidgets = { viewModel.openWidgetsScreen() },
                            onOpenSettings = { viewModel.openSettingsScreen() },
                            onEventClick = { viewModel.openEventDetail(it) },
                            onCreateAtDate = { d, h -> viewModel.openNewEventEditor(d, h) }
                        )
                        AppTab.AI -> AiAssistantScreen(
                            aiConfig = settings.aiConfig,
                            aiState = aiState,
                            onQuickInputChanged = { viewModel.updateAiQuickInput(it) },
                            onParseQuick = { viewModel.parseQuickEvent(null) },
                            onSaveDraftDirectly = { viewModel.saveDraftDirectly(it) },
                            onOpenDraftInEditor = { viewModel.openEditorFromDraft(it) },
                            onChangeBriefingDays = { viewModel.setBriefingPeriodDays(it) },
                            onRefreshBriefing = { viewModel.refreshAiBriefing() },
                            onExtractTextChanged = { viewModel.updateExtractRawText(it) },
                            onExtractEvents = { viewModel.extractEventsFromRawText() },
                            onSlotParamsChanged = { g, d -> viewModel.updateSlotParams(g, d) },
                            onFindSlots = { viewModel.findSmartSlots() },
                            onBookSlot = { slot ->
                                viewModel.openEditorFromDraft(
                                    ParsedEventDraft(
                                        title = aiState.slotGoal.ifBlank { "Fokuszeit" },
                                        startMillis = slot.startMillis,
                                        endMillis = slot.endMillis
                                    )
                                )
                            },
                            onOpenAiSettings = { viewModel.openSettingsScreen() }
                        )
                        AppTab.SYNC -> SyncCenterScreen(
                            settings = settings,
                            isSyncing = isSyncing,
                            accounts = accounts,
                            calendars = calendars,
                            syncLogs = syncLogs,
                            onSyncNow = { viewModel.triggerSyncNow() },
                            onSelectInterval = { viewModel.setSyncInterval(it) },
                            onWifiOnlyChanged = { viewModel.setWifiOnlySync(it) },
                            onSyncOnLaunchChanged = { viewModel.setSyncOnLaunch(it) },
                            onSyncAfterEditChanged = { viewModel.setSyncAfterEdit(it) },
                            onToggleCalendarVisible = { id, vis -> viewModel.toggleCalendarVisibility(id, vis) },
                            onCreateLocalCalendar = { name, color -> viewModel.createLocalCalendar(name, color) },
                            onAddCalDav = { n, u, usr, pw, col -> viewModel.addCalDavAccount(n, u, usr, pw, col) },
                            onAddIcsSubscription = { n, u, col -> viewModel.addIcsSubscription(n, u, col) },
                            onAddGoogleApi = { n, em, tok -> viewModel.addGoogleApiAccount(n, em, tok) },
                            onRemoveAccount = { viewModel.removeAccount(it) },
                            onImportIcs = { txt, calId -> viewModel.importIcsText(txt, calId) },
                            onExportCalendarIcs = { calId, calName -> viewModel.shareCalendarIcs(context, calId, calName) }
                        )
                    }
                }
            }

            // Floating iOS-26 / Honor Style Bottom Dock + Dedicated Circular + Button
            if (overlayScreen == OverlayScreen.NONE) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    SnackbarHost(hostState = snackbarHostState)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingLiquidNavBar(
                            selectedTab = selectedTab,
                            onSelectTab = { viewModel.selectTab(it) },
                            modifier = Modifier.weight(1f)
                        )

                        AnimatedVisibility(
                            visible = selectedTab == AppTab.CALENDAR,
                            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.85f),
                            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f)
                        ) {
                            Row {
                                Spacer(modifier = Modifier.width(12.dp))
                                LiquidGlassSurface(
                                    modifier = Modifier.size(56.dp),
                                    cornerRadius = 28.dp,
                                    elevated = true,
                                    onClick = { viewModel.openNewEventEditor() }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Neuer Termin",
                                            tint = palette.textPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    SnackbarHost(hostState = snackbarHostState)
                }
            }
        }
    }

    detailOcc?.let { occ ->
        val cal = calendars.firstOrNull { it.id == occ.event.calendarId }
        EventDetailSheet(
            occurrence = occ,
            isReadOnly = cal?.isReadOnly == true,
            onDismiss = { viewModel.closeEventDetail() },
            onEdit = { viewModel.openEditEventEditor(occ.event) },
            onDuplicate = { viewModel.duplicateEvent(occ.event) },
            onDelete = { viewModel.deleteEvent(occ.event.id) },
            onShareIcs = { viewModel.shareEventIcs(context, occ.event) }
        )
    }

    if (editorState.isOpen) {
        EventEditorSheet(
            editorState = editorState,
            calendars = calendars,
            onDismiss = { viewModel.closeEditor() },
            onSave = { viewModel.saveEvent(it) },
            onDelete = { viewModel.deleteEvent(it) }
        )
    }

    if (quickAddOpen) {
        QuickAddSheet(
            aiState = aiState,
            onInputChanged = { viewModel.updateAiQuickInput(it) },
            onParse = { viewModel.parseQuickEvent(it) },
            onSaveDraftDirectly = { viewModel.saveDraftDirectly(it) },
            onOpenInEditor = { viewModel.openEditorFromDraft(it) },
            onDismiss = { viewModel.closeQuickAdd() }
        )
    }
}

@Composable
private fun CalendarTabContent(
    viewMode: CalendarViewMode,
    selectedDate: LocalDate,
    visibleMonth: YearMonth,
    searchActive: Boolean,
    searchQuery: String,
    searchAiDraft: ParsedEventDraft?,
    isRefiningSearchAi: Boolean,
    byokActive: Boolean,
    firstDayOfWeek: DayOfWeek,
    showWeekNumbers: Boolean,
    dataRevision: Long,
    loadOccurrences: (Long, Long) -> List<EventOccurrence>,
    searchEvents: (String) -> List<EventOccurrence>,
    parseSearchLocally: (String) -> ParsedEventDraft,
    onRefineSearchWithAi: (String) -> Unit,
    onSaveParsedFromSearch: (ParsedEventDraft) -> Unit,
    onPrefillEditorFromSearch: (ParsedEventDraft) -> Unit,
    onViewModeChanged: (CalendarViewMode) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onVisibleMonthChanged: (YearMonth) -> Unit,
    onJumpToday: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTriggerSync: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenSettings: () -> Unit,
    onEventClick: (EventOccurrence) -> Unit,
    onCreateAtDate: (LocalDate, Int?) -> Unit
) {
    val palette = LocalBttrPalette.current
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN) }
    val titleText = if (viewMode == CalendarViewMode.YEAR) {
        visibleMonth.year.toString()
    } else {
        visibleMonth.atDay(1).format(monthFmt).replaceFirstChar { it.titlecase(Locale.GERMAN) }
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Minimalist Header (Month/Year + 2 circular frosted icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .bounceClick(onClick = {
                        val next = if (viewMode == CalendarViewMode.YEAR) CalendarViewMode.MONTH else CalendarViewMode.YEAR
                        onViewModeChanged(next)
                    })
            )

            LiquidGlassSurface(
                modifier = Modifier.size(40.dp),
                cornerRadius = 20.dp,
                elevated = true,
                onClick = { onToggleSearch(!searchActive) }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Suche & Schnell-Eingabe",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                LiquidGlassSurface(
                    modifier = Modifier.size(40.dp),
                    cornerRadius = 20.dp,
                    elevated = true,
                    onClick = { menuExpanded = true }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menü",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = palette.surfaceElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("Heute", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Today, contentDescription = null, tint = palette.accent) },
                        onClick = {
                            menuExpanded = false
                            onJumpToday()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Jetzt synchronisieren", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null, tint = palette.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onTriggerSync()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Widgets & Vorschau", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null, tint = palette.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onOpenWidgets()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Einstellungen", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = palette.textPrimary) },
                        onClick = {
                            menuExpanded = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }

        // Smart Dual-Purpose Search & Instant Natural-Language Entry
        AnimatedVisibility(visible = searchActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Suchen oder eingeben: z. B. Pokern am 23. Nov 17 Uhr") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textSecondary)
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (searchActive && searchQuery.isNotBlank()) {
            val activeDraft = searchAiDraft ?: remember(searchQuery) { parseSearchLocally(searchQuery) }
            val results = remember(searchQuery, dataRevision) { searchEvents(searchQuery) }
            val zone = remember { ZoneId.systemDefault() }
            val dtFmt = remember { DateTimeFormatter.ofPattern("EEE, d. MMM yyyy · HH:mm", Locale.GERMAN) }
            val endFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN) }

            val startZdt = Instant.ofEpochMilli(activeDraft.startMillis).atZone(zone)
            val endZdt = Instant.ofEpochMilli(activeDraft.endMillis).atZone(zone)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "smart_parsed_card") {
                    LiquidGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        elevated = true,
                        padding = PaddingValues(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (activeDraft.usedAi) "Per BYOK-KI erkannt" else "Lokal erkannt · Direkt eintragen",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.accent,
                                    modifier = Modifier.weight(1f)
                                )
                                if (byokActive) {
                                    LiquidGlassSurface(
                                        cornerRadius = 12.dp,
                                        padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        onClick = { onRefineSearchWithAi(searchQuery) }
                                    ) {
                                        if (isRefiningSearchAi) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text("KI verfeinern", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                                        }
                                    }
                                }
                            }

                            Text(
                                text = activeDraft.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (activeDraft.allDay) {
                                    "${startZdt.toLocalDate()} · Ganztägig"
                                } else {
                                    "${startZdt.format(dtFmt)} – ${endZdt.format(endFmt)} Uhr"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                            if (activeDraft.location.isNotBlank()) {
                                Text(
                                    text = "Ort: ${activeDraft.location}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                            }
                            if (activeDraft.description.isNotBlank()) {
                                Text(
                                    text = "Notiz: ${activeDraft.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textTertiary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onPrefillEditorFromSearch(activeDraft) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Vorausfüllen")
                                }
                                Button(
                                    onClick = { onSaveParsedFromSearch(activeDraft) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Eintragen")
                                }
                            }
                        }
                    }
                }

                if (results.isNotEmpty()) {
                    item {
                        Text(
                            text = "Gefundene Termine (${results.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    items(results, key = { "${it.event.id}_srch" }) { occ ->
                        MonthAgendaItemCard(occurrence = occ, onClick = { onEventClick(occ) })
                    }
                }
            }
        } else {
            // Minimalist Segmented View Bar (Jahr | Monat | Woche | Tag | Liste)
            val modes = listOf(
                CalendarViewMode.YEAR to "Jahr",
                CalendarViewMode.MONTH to "Monat",
                CalendarViewMode.WEEK to "Woche",
                CalendarViewMode.DAY to "Tag",
                CalendarViewMode.AGENDA to "Liste"
            )
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 10.dp),
                cornerRadius = 22.dp,
                padding = PaddingValues(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    modes.forEach { (mode, label) ->
                        val active = viewMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (active) palette.surfaceSubtle else Color.Transparent)
                                .bounceClick(scaleTarget = 0.94f) { onViewModeChanged(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) palette.textPrimary else palette.textSecondary
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.985f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.985f, animationSpec = tween(140)))
                },
                label = "calendar_view_mode",
                modifier = Modifier.weight(1f)
            ) { mode ->
                when (mode) {
                    CalendarViewMode.MONTH -> MonthView(
                        selectedDate = selectedDate,
                        visibleMonth = visibleMonth,
                        firstDayOfWeek = firstDayOfWeek,
                        showWeekNumbers = showWeekNumbers,
                        dataRevision = dataRevision,
                        loadOccurrences = loadOccurrences,
                        onSelectDate = onSelectDate,
                        onVisibleMonthChanged = onVisibleMonthChanged,
                        onEventClick = onEventClick,
                        onAddForDate = { d -> onCreateAtDate(d, null) }
                    )
                    CalendarViewMode.DAY -> TimeGridView(
                        daysCount = 1,
                        selectedDate = selectedDate,
                        firstDayOfWeek = firstDayOfWeek,
                        dataRevision = dataRevision,
                        loadOccurrences = loadOccurrences,
                        onSelectDate = onSelectDate,
                        onEventClick = onEventClick,
                        onCreateAtSlot = { d, h -> onCreateAtDate(d, h) }
                    )
                    CalendarViewMode.THREE_DAY -> TimeGridView(
                        daysCount = 3,
                        selectedDate = selectedDate,
                        firstDayOfWeek = firstDayOfWeek,
                        dataRevision = dataRevision,
                        loadOccurrences = loadOccurrences,
                        onSelectDate = onSelectDate,
                        onEventClick = onEventClick,
                        onCreateAtSlot = { d, h -> onCreateAtDate(d, h) }
                    )
                    CalendarViewMode.WEEK -> TimeGridView(
                        daysCount = 7,
                        selectedDate = selectedDate,
                        firstDayOfWeek = firstDayOfWeek,
                        dataRevision = dataRevision,
                        loadOccurrences = loadOccurrences,
                        onSelectDate = onSelectDate,
                        onEventClick = onEventClick,
                        onCreateAtSlot = { d, h -> onCreateAtDate(d, h) }
                    )
                    CalendarViewMode.AGENDA -> AgendaView(
                        startDate = selectedDate,
                        dataRevision = dataRevision,
                        loadOccurrences = loadOccurrences,
                        onEventClick = onEventClick,
                        onCreateNew = { d -> onCreateAtDate(d, null) }
                    )
                    CalendarViewMode.YEAR -> YearView(
                        year = visibleMonth.year,
                        firstDayOfWeek = firstDayOfWeek,
                        dataRevision = dataRevision,
                        loadOccurrences = loadOccurrences,
                        onSelectMonth = { ym ->
                            onSelectDate(ym.atDay(1))
                            onViewModeChanged(CalendarViewMode.MONTH)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingLiquidNavBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 28.dp,
        elevated = true,
        padding = PaddingValues(horizontal = 5.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val icon: ImageVector = when (tab) {
                    AppTab.CALENDAR -> Icons.Rounded.CalendarMonth
                    AppTab.AI -> Icons.Rounded.AutoAwesome
                    AppTab.SYNC -> Icons.Rounded.CloudSync
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(23.dp))
                        .background(if (selected) palette.surfaceSubtle else Color.Transparent)
                        .bounceClick(scaleTarget = 0.93f, haptic = true) { onSelectTab(tab) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.labelDe,
                            tint = if (selected) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.labelDe,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) palette.accent else palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}
