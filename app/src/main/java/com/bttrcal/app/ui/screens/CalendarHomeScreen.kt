package com.bttrcal.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bttrcal.app.domain.model.AppTab
import com.bttrcal.app.domain.model.CalendarViewMode
import com.bttrcal.app.ui.MainViewModel
import com.bttrcal.app.ui.theme.LiquidGlassBackdrop
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import com.bttrcal.app.ui.theme.bounceClick
import com.bttrcal.app.ui.views.AgendaView
import com.bttrcal.app.ui.views.MonthAgendaItemCard
import com.bttrcal.app.ui.views.MonthView
import com.bttrcal.app.ui.views.TimeGridView
import com.bttrcal.app.ui.views.YearView
import java.time.LocalDate
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
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

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

    LiquidGlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "main_tab_transition"
            ) { tab ->
                when (tab) {
                    AppTab.CALENDAR -> {
                        CalendarTabContent(
                            viewMode = viewMode,
                            selectedDate = selectedDate,
                            visibleMonth = visibleMonth,
                            searchActive = searchActive,
                            searchQuery = searchQuery,
                            isSyncing = isSyncing,
                            firstDayOfWeek = settings.firstDayOfWeek,
                            showWeekNumbers = settings.showWeekNumbers,
                            dataRevision = dataRevision,
                            loadOccurrences = { s, e -> viewModel.getOccurrencesForRange(s, e) },
                            searchEvents = { q -> viewModel.searchEvents(q) },
                            onViewModeChanged = { viewModel.setViewMode(it) },
                            onSelectDate = { viewModel.selectDate(it) },
                            onVisibleMonthChanged = { viewModel.setVisibleMonth(it) },
                            onJumpToday = { viewModel.jumpToToday() },
                            onToggleSearch = { viewModel.toggleSearch(it) },
                            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                            onTriggerSync = { viewModel.triggerSyncNow() },
                            onEventClick = { viewModel.openEventDetail(it) },
                            onCreateAtDate = { d, h -> viewModel.openNewEventEditor(d, h) },
                            onOpenQuickAdd = { viewModel.openQuickAdd() }
                        )
                    }
                    AppTab.AI -> {
                        AiAssistantScreen(
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
                                    com.bttrcal.app.domain.model.ParsedEventDraft(
                                        title = aiState.slotGoal.ifBlank { "Fokuszeit" },
                                        startMillis = slot.startMillis,
                                        endMillis = slot.endMillis
                                    )
                                )
                            },
                            onOpenAiSettings = { viewModel.selectTab(AppTab.SETTINGS) }
                        )
                    }
                    AppTab.SYNC -> {
                        SyncCenterScreen(
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
                    AppTab.WIDGETS -> {
                        WidgetStudioScreen(
                            widgetStyle = settings.widgetStyle,
                            dataRevision = dataRevision,
                            loadOccurrences = { s, e -> viewModel.getOccurrencesForRange(s, e) },
                            onUpdateWidgetStyle = { viewModel.updateWidgetStyle(it) },
                            onStatusMessage = { viewModel.showStatus(it) }
                        )
                    }
                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            aiState = aiState,
                            onSetThemeMode = { viewModel.setThemeMode(it) },
                            onSetGlassVariant = { viewModel.setGlassVariant(it) },
                            onSetFirstDayOfWeek = { viewModel.setFirstDayOfWeek(it) },
                            onSetShowWeekNumbers = { viewModel.setShowWeekNumbers(it) },
                            onSetDefaultDuration = { viewModel.setDefaultDurationMinutes(it) },
                            onSetDefaultReminder = { viewModel.setDefaultReminderMinutes(it) },
                            onSaveAiConfig = { p, m, u, en, k -> viewModel.updateAiProviderSettings(p, m, u, en, k) },
                            onTestAiConnection = { p, m, u, k -> viewModel.testAiConnection(p, m, u, k) }
                        )
                    }
                }
            }

            // Floating iOS-26 Style Liquid Glass Bottom Dock + Circular + Button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.8f),
                        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.8f)
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(10.dp))
                            LiquidGlassSurface(
                                modifier = Modifier.size(56.dp),
                                cornerRadius = 28.dp,
                                elevated = true,
                                onClick = { viewModel.openNewEventEditor() }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(palette.accent.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Neuer Termin",
                                        tint = palette.accent,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Event detail modal
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

    // Event editor modal
    if (editorState.isOpen) {
        EventEditorSheet(
            editorState = editorState,
            calendars = calendars,
            onDismiss = { viewModel.closeEditor() },
            onSave = { viewModel.saveEvent(it) },
            onDelete = { viewModel.deleteEvent(it) }
        )
    }

    // Smart Quick-Add modal
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
    visibleMonth: java.time.YearMonth,
    searchActive: Boolean,
    searchQuery: String,
    isSyncing: Boolean,
    firstDayOfWeek: java.time.DayOfWeek,
    showWeekNumbers: Boolean,
    dataRevision: Long,
    loadOccurrences: (Long, Long) -> List<com.bttrcal.app.domain.model.EventOccurrence>,
    searchEvents: (String) -> List<com.bttrcal.app.domain.model.EventOccurrence>,
    onViewModeChanged: (CalendarViewMode) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onVisibleMonthChanged: (java.time.YearMonth) -> Unit,
    onJumpToday: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onTriggerSync: () -> Unit,
    onEventClick: (com.bttrcal.app.domain.model.EventOccurrence) -> Unit,
    onCreateAtDate: (LocalDate, Int?) -> Unit,
    onOpenQuickAdd: () -> Unit
) {
    val palette = LocalBttrPalette.current
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN) }
    val titleText = if (viewMode == CalendarViewMode.YEAR) {
        visibleMonth.year.toString()
    } else {
        visibleMonth.atDay(1).format(monthFmt).replaceFirstChar { it.titlecase(Locale.GERMAN) }
    }

    val spinInf = rememberInfiniteTransition(label = "hdr_sync_spin")
    val spinAngle by spinInf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "hdr_angle"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Apple-like Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                cornerRadius = 14.dp,
                padding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                onClick = onJumpToday
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Today, contentDescription = "Heute", tint = Color(0xFFFF453A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Heute", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            LiquidGlassSurface(
                cornerRadius = 14.dp,
                padding = PaddingValues(8.dp),
                onClick = onOpenQuickAdd
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Smart Quick-Add",
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            LiquidGlassSurface(
                cornerRadius = 14.dp,
                padding = PaddingValues(8.dp),
                onClick = onTriggerSync
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Sync",
                    tint = if (isSyncing) palette.accent else palette.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = if (isSyncing) spinAngle else 0f }
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            LiquidGlassSurface(
                cornerRadius = 14.dp,
                padding = PaddingValues(8.dp),
                onClick = { onToggleSearch(!searchActive) }
            ) {
                Icon(
                    imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = "Suche",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Animated Search Bar & Results
        AnimatedVisibility(visible = searchActive) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Termine, Orte oder Notizen durchsuchen...") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (searchActive && searchQuery.isNotBlank()) {
            val results = remember(searchQuery, dataRevision) { searchEvents(searchQuery) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (results.isEmpty()) {
                    item {
                        Text(
                            text = "Keine passenden Termine gefunden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(results, key = { "${it.event.id}_srch" }) { occ ->
                        MonthAgendaItemCard(occurrence = occ, onClick = { onEventClick(occ) })
                    }
                }
            }
        } else {
            // View mode pill switcher
            val modes = listOf(
                CalendarViewMode.DAY,
                CalendarViewMode.THREE_DAY,
                CalendarViewMode.WEEK,
                CalendarViewMode.MONTH,
                CalendarViewMode.AGENDA
            )
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 10.dp),
                cornerRadius = 18.dp,
                padding = PaddingValues(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    modes.forEach { mode ->
                        val active = viewMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) palette.accent else Color.Transparent)
                                .bounceClick(scaleTarget = 0.94f) { onViewModeChanged(mode) }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.shortLabelDe,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) Color.White else palette.textSecondary
                            )
                        }
                    }
                }
            }

            // Calendar view content with smooth spring transition
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.98f, animationSpec = tween(140)))
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
        modifier = modifier
            .background(
                color = palette.background.copy(alpha = if (palette.isGlass) 0.65f else 0.94f),
                shape = RoundedCornerShape(28.dp)
            ),
        cornerRadius = 28.dp,
        elevated = true,
        padding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
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
                    AppTab.WIDGETS -> Icons.Rounded.Widgets
                    AppTab.SETTINGS -> Icons.Rounded.Settings
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) palette.accentSoft else Color.Transparent)
                        .bounceClick(scaleTarget = 0.92f, haptic = true) { onSelectTab(tab) }
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
