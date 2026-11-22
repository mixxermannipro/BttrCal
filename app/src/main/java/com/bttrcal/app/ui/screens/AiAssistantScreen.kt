package com.bttrcal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bttrcal.app.domain.model.AiConfig
import com.bttrcal.app.domain.model.AiProviderType
import com.bttrcal.app.domain.model.FreeSlotSuggestion
import com.bttrcal.app.domain.model.ParsedEventDraft
import com.bttrcal.app.ui.AiUiState
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AiAssistantScreen(
    aiConfig: AiConfig,
    aiState: AiUiState,
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
    val zone = remember { ZoneId.systemDefault() }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE dd.MM. · HH:mm", Locale.GERMAN) }

    val byokActive = aiConfig.enabled &&
        (aiConfig.hasStoredKey || aiConfig.provider == AiProviderType.CUSTOM_OPENAI_COMPATIBLE)

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
                        text = "KI-Assistent",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Smart Quick-Add, Briefings & freie Zeitfenster",
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
                            "Offline-Modus: Lokale KI-Engine aktiv · Tippen für eigenen API-Key"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary
                    )
                }
            }
        }

        // 1. Briefing Card
        item {
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

        // 2. Smart Quick-Add Card
        item {
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

        // 3. Free Slots Finder
        item {
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

        // 4. Text / Email to Events Extractor
        item {
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
