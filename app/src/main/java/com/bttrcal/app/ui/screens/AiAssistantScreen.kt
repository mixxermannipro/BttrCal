package com.bttrcal.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bttrcal.app.domain.engine.DuplicateCluster
import com.bttrcal.app.domain.model.AiConfig
import com.bttrcal.app.domain.model.AiProviderType
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.DailyFocusMetrics
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.FreeSlotSuggestion
import com.bttrcal.app.domain.model.ParsedEventDraft
import com.bttrcal.app.domain.model.ScheduleConflict
import com.bttrcal.app.domain.model.TravelBufferWarning
import com.bttrcal.app.domain.model.UpcomingMilestone
import com.bttrcal.app.ui.AiUiState
import com.bttrcal.app.ui.theme.BouncyEntrance
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import com.bttrcal.app.ui.theme.bounceClick
import com.bttrcal.app.ui.theme.rememberBttrHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AiAssistantScreen(
    aiConfig: AiConfig,
    aiState: AiUiState,
    calendars: List<CalendarSource>,
    duplicateClusters: List<DuplicateCluster>,
    conflicts: List<ScheduleConflict>,
    travelWarnings: List<TravelBufferWarning>,
    upcomingMilestones: List<UpcomingMilestone>,
    dailyFocus: DailyFocusMetrics?,
    onMergeDuplicates: (DuplicateCluster, String, String) -> Unit,
    onDeleteDuplicateEvent: (String, String) -> Unit,
    onEventClick: (EventOccurrence) -> Unit,
    onQuickInputChanged: (String) -> Unit,
    onParseQuick: () -> Unit,
    onSaveDraftDirectly: (ParsedEventDraft) -> Unit,
    onOpenDraftInEditor: (ParsedEventDraft) -> Unit,
    onChangeBriefingDays: (Int) -> Unit,
    onRefreshBriefing: () -> Unit,
    onExtractTextChanged: (String) -> Unit,
    onExtractEvents: () -> Unit,
    onSlotParamsChanged: (String, Int) -> Unit,
    onFindSlots: () -> Unit,
    onBookSlot: (FreeSlotSuggestion) -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val haptics = rememberBttrHaptics()
    val zone = remember { ZoneId.systemDefault() }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE dd.MM. · HH:mm", Locale.GERMAN) }

    val byokActive = aiConfig.enabled &&
        (aiConfig.hasStoredKey || aiConfig.provider == AiProviderType.CUSTOM_OPENAI_COMPATIBLE)

    var mergeCandidate by remember { mutableStateOf<DuplicateCluster?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Intelligenz & KI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Duplikate, Konflikte, Fokus & Smart-Eingabe",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
                LiquidGlassSurface(
                    cornerRadius = 16.dp,
                    padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onClick = onOpenAiSettings
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BYOK", style = MaterialTheme.typography.labelMedium, color = palette.accent)
                    }
                }
            }
        }

        // Engine badge
        item {
            LiquidGlassSurface(
                cornerRadius = 18.dp,
                padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                onClick = onOpenAiSettings
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (byokActive) palette.success else palette.warning)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (byokActive) {
                            "BYOK aktiv: ${aiConfig.provider.displayName} (${aiConfig.model})"
                        } else {
                            "Offline-Modus: Lokale Intelligenz aktiv · Tippen für BYOK (Groq, Gemini, OpenAI)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                }
            }
        }

        // 0A. Lokale Intelligenz: Duplikate, Konflikte & Reisezeit-Puffer
        item {
            BouncyEntrance(delayIndex = 0) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    elevated = true,
                    padding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = palette.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Duplikate & Überschneidungen",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            val totalIssues = duplicateClusters.size + conflicts.size + travelWarnings.size
                            if (totalIssues == 0) {
                                StatBadge("Alles sauber", palette.success)
                            } else {
                                StatBadge("$totalIssues Hinweis(e)", palette.accent)
                            }
                        }

                        if (duplicateClusters.isEmpty() && conflicts.isEmpty() && travelWarnings.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.success.copy(alpha = 0.10f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = palette.success, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Keine doppelten Termine über deine Kalender oder Überschneidungen erkannt.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary
                                )
                            }
                        }

                        duplicateClusters.forEach { cluster ->
                            LiquidGlassSurface(
                                cornerRadius = 18.dp,
                                padding = PaddingValues(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cluster.suggestedTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = palette.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = cluster.reasonDe,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.accent
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                haptics.tick()
                                                mergeCandidate = cluster
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Zusammenführen", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    // Show each duplicate variant across calendars
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        cluster.occurrences.forEach { occ ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(palette.surfaceSubtle)
                                                    .bounceClick(scaleTarget = 0.98f) { onEventClick(occ) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(9.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(occ.displayColorArgb))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${occ.calendarName}: ${occ.event.title}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = palette.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Diesen Eintrag entfernen",
                                                    tint = palette.textTertiary,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .bounceClick(scaleTarget = 0.85f, haptic = true) {
                                                            onDeleteDuplicateEvent(occ.event.id, occ.event.title)
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (conflicts.isNotEmpty()) {
                            conflicts.take(4).forEach { conflict ->
                                val sStr = Instant.ofEpochMilli(conflict.second.occurrenceStartMillis).atZone(zone).format(fmt)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(palette.danger.copy(alpha = 0.12f))
                                        .bounceClick(scaleTarget = 0.98f) { onEventClick(conflict.first) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = palette.danger, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Konflikt (${conflict.overlapMinutes} Min): ${conflict.first.event.title} & ${conflict.second.event.title}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        if (travelWarnings.isNotEmpty()) {
                            travelWarnings.take(3).forEach { warn ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(palette.warning.copy(alpha = 0.12f))
                                        .bounceClick(scaleTarget = 0.98f) { onEventClick(warn.second) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.DirectionsTransit, contentDescription = null, tint = palette.warning, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = warn.messageDe,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${warn.first.event.title} → ${warn.second.event.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 0B. Tages-Fokus & Geburtstags-Radar (100% lokal)
        if (dailyFocus != null || upcomingMilestones.isNotEmpty()) {
            item {
                BouncyEntrance(delayIndex = 1) {
                    LiquidGlassSurface(
                        cornerRadius = 24.dp,
                        elevated = true,
                        padding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            dailyFocus?.let { focus ->
                                val barFraction by animateFloatAsState(
                                    targetValue = (focus.focusScore / 100f).coerceIn(0.05f, 1f),
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                                    label = "focus_bar"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Tages-Fokus",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatBadge("Score ${focus.focusScore}/100", palette.accent)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.surfaceSubtle)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(barFraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(palette.accent)
                                    )
                                }
                                Text(
                                    text = focus.summaryDe,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                            }

                            if (upcomingMilestones.isNotEmpty()) {
                                Text(
                                    text = "Geburtstags- & Jubiläums-Radar",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textSecondary
                                )
                                upcomingMilestones.take(4).forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(palette.surfaceSubtle)
                                            .bounceClick(scaleTarget = 0.98f) { onEventClick(m.occurrence) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Cake, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = m.occurrence.event.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        StatBadge(m.labelDe, palette.accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Briefing Card
        item {
            BouncyEntrance(delayIndex = 2) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    elevated = true,
                    padding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Plan-Briefing",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            ChoiceChipPill("Heute", aiState.briefingPeriodDays == 1) { onChangeBriefingDays(1) }
                            Spacer(modifier = Modifier.width(6.dp))
                            ChoiceChipPill("7 Tage", aiState.briefingPeriodDays == 7) { onChangeBriefingDays(7) }
                            Spacer(modifier = Modifier.width(6.dp))
                            LiquidGlassSurface(
                                cornerRadius = 12.dp,
                                padding = PaddingValues(6.dp),
                                onClick = onRefreshBriefing
                            ) {
                                if (aiState.isGeneratingBriefing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Neu", tint = palette.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        aiState.briefing?.let { b ->
                            Text(
                                text = b.summary,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.textPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatBadge("${b.totalEventsCount} Termine", palette.accent)
                                StatBadge("${b.busyMinutes / 60}h ${b.busyMinutes % 60}m belegt", palette.textSecondary)
                                if (b.conflictsCount > 0) {
                                    StatBadge("${b.conflictsCount} Konflikt(e)", palette.danger)
                                }
                            }
                            b.highlights.forEach { line ->
                                Text(
                                    text = "• $line",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                            }
                            Text(
                                text = if (b.usedAi) "Erstellt mit deinem BYOK-Modell" else "Lokal auf dem Gerät analysiert",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textTertiary
                            )
                        }
                    }
                }
            }
        }

        // 2. Smart Quick-Add Card
        item {
            BouncyEntrance(delayIndex = 3) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    elevated = true,
                    padding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Natürliche Eingabe (Quick-Add)",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        OutlinedTextField(
                            value = aiState.quickInput,
                            onValueChange = onQuickInputChanged,
                            label = { Text("z. B. Donnerstag 15:30 Steuerberater @ Büro für 45 Min") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = onParseQuick,
                            enabled = aiState.quickInput.isNotBlank() && !aiState.isParsingQuick,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (aiState.isParsingQuick) "Erkenne Termin..." else "Termin erkennen")
                        }

                        aiState.quickDraft?.let { draft ->
                            val startStr = Instant.ofEpochMilli(draft.startMillis).atZone(zone).format(fmt)
                            LiquidGlassSurface(
                                cornerRadius = 16.dp,
                                padding = PaddingValues(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(draft.title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                    Text(startStr, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                    if (draft.location.isNotBlank()) {
                                        Text("Ort: ${draft.location}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onOpenDraftInEditor(draft) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Editor")
                                        }
                                        Button(
                                            onClick = { onSaveDraftDirectly(draft) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Speichern")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Free Slots Finder
        item {
            BouncyEntrance(delayIndex = 4) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    elevated = true,
                    padding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = palette.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Freie Zeitfenster (nächste 7 Tage)",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                        }
                        OutlinedTextField(
                            value = aiState.slotGoal,
                            onValueChange = { onSlotParamsChanged(it, aiState.slotDurationMinutes) },
                            label = { Text("Wofür suchst du Zeit?") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(30, 45, 60, 90, 120).forEach { mins ->
                                ChoiceChipPill(
                                    label = if (mins >= 60) "${mins / 60}h${if (mins % 60 > 0) " ${mins % 60}m" else ""}" else "$mins Min",
                                    selected = aiState.slotDurationMinutes == mins
                                ) {
                                    onSlotParamsChanged(aiState.slotGoal, mins)
                                }
                            }
                        }
                        Button(
                            onClick = onFindSlots,
                            enabled = !aiState.isFindingSlots,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (aiState.isFindingSlots) "Suche Lücken..." else "Passende Slots vorschlagen")
                        }

                        aiState.freeSlots.take(5).forEach { slot ->
                            LiquidGlassSurface(
                                cornerRadius = 16.dp,
                                padding = PaddingValues(12.dp),
                                onClick = { onBookSlot(slot) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = slot.scoreLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.accent
                                        )
                                        Text(
                                            text = slot.reason,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textPrimary
                                        )
                                    }
                                    Text(
                                        text = "Buchen",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Text / Email to Events Extractor
        item {
            BouncyEntrance(delayIndex = 5) {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    elevated = true,
                    padding = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TextSnippet, contentDescription = null, tint = palette.accent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Text / E-Mail zu Terminen",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                        }
                        OutlinedTextField(
                            value = aiState.extractRawText,
                            onValueChange = onExtractTextChanged,
                            label = { Text("Nachricht, Einladung oder Notiz einfügen") },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = onExtractEvents,
                            enabled = aiState.extractRawText.isNotBlank() && !aiState.isExtracting,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (aiState.isExtracting) "Extrahiere..." else "Termine extrahieren")
                        }

                        aiState.extractedDrafts.forEach { draft ->
                            val sStr = Instant.ofEpochMilli(draft.startMillis).atZone(zone).format(fmt)
                            LiquidGlassSurface(
                                cornerRadius = 16.dp,
                                padding = PaddingValues(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(draft.title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                        Text(sStr, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                    }
                                    Button(
                                        onClick = { onSaveDraftDirectly(draft) },
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Import")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    mergeCandidate?.let { cluster ->
        MergeDuplicateDialog(
            cluster = cluster,
            writableCalendars = calendars.filter { !it.isReadOnly },
            onDismiss = { mergeCandidate = null },
            onConfirm = { targetCalId, finalTitle ->
                onMergeDuplicates(cluster, targetCalId, finalTitle)
                mergeCandidate = null
            }
        )
    }
}

@Composable
private fun MergeDuplicateDialog(
    cluster: DuplicateCluster,
    writableCalendars: List<CalendarSource>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val palette = LocalBttrPalette.current
    val haptics = rememberBttrHaptics()
    var mergedTitle by remember(cluster.id) { mutableStateOf(cluster.suggestedTitle) }
    val defaultTargetId = remember(cluster.id, writableCalendars) {
        writableCalendars.firstOrNull { cal ->
            cluster.occurrences.any { it.event.calendarId == cal.id }
        }?.id ?: writableCalendars.firstOrNull()?.id ?: cluster.occurrences.first().event.calendarId
    }
    var selectedCalId by remember(cluster.id) { mutableStateOf(defaultTargetId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (palette.isDark) Color(0xFF16171B) else Color(0xFFFCFDFE),
        title = { Text("Duplikate zusammenführen", color = palette.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Wähle den Ziel-Kalender. Alle ${cluster.occurrences.size} Einträge werden vereint; die Duplikate in den übrigen Kalendern werden automatisch gelöscht.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                OutlinedTextField(
                    value = mergedTitle,
                    onValueChange = { mergedTitle = it },
                    label = { Text("Gemeinsamer Titel") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Ziel-Kalender auswählen",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    writableCalendars.forEach { cal ->
                        val selected = selectedCalId == cal.id
                        val bg by animateColorAsState(
                            targetValue = if (selected) palette.accentSoft else palette.surfaceSubtle,
                            animationSpec = spring(stiffness = 380f),
                            label = "target_cal_bg"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(
                                    width = if (selected) 1.3.dp else 0.7.dp,
                                    color = if (selected) palette.accent else palette.border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .bounceClick(scaleTarget = 0.96f, haptic = true) {
                                    selectedCalId = cal.id
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(Color(cal.colorArgb))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cal.name, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                                Text(cal.accountType.labelDe, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            }
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onConfirm(selectedCalId, mergedTitle)
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Zusammenführen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun StatBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
