package com.bttrcal.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bttrcal.app.domain.model.ParsedEventDraft
import com.bttrcal.app.ui.AiUiState
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    aiState: AiUiState,
    onInputChanged: (String) -> Unit,
    onParse: (String?) -> Unit,
    onSaveDraftDirectly: (ParsedEventDraft) -> Unit,
    onOpenInEditor: (ParsedEventDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalBttrPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE dd.MM.yyyy · HH:mm", Locale.GERMAN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (palette.isDark) Color(0xFF141416) else Color(0xFFFCFDFE)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Smart Quick-Add",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )
            }
            Text(
                text = "Schreibe einfach natürlich – funktioniert sowohl lokal als auch mit deinem BYOK-KI-Modell.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )

            val examples = listOf(
                "Morgen 14:30 Zahnarzt in Praxis Mitte für 45 Min",
                "Freitag 19 Uhr Kino mit Freunden",
                "Jeden Montag 09:00 Wochen-Sync"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examples.forEach { sample ->
                    ChoiceChipPill(label = sample, selected = false) {
                        onInputChanged(sample)
                        onParse(sample)
                    }
                }
            }

            OutlinedTextField(
                value = aiState.quickInput,
                onValueChange = onInputChanged,
                label = { Text("Termin beschreiben") },
                placeholder = { Text("z. B. Übermorgen 16:00 Kaffeepause @ Café Central") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            Button(
                onClick = { onParse(null) },
                enabled = aiState.quickInput.isNotBlank() && !aiState.isParsingQuick,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (aiState.isParsingQuick) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.surface)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Analysieren")
            }

            AnimatedVisibility(visible = aiState.quickDraft != null) {
                aiState.quickDraft?.let { draft ->
                    val s = Instant.ofEpochMilli(draft.startMillis).atZone(zone)
                    val e = Instant.ofEpochMilli(draft.endMillis).atZone(zone)
                    LiquidGlassSurface(
                        cornerRadius = 20.dp,
                        padding = PaddingValues(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = draft.confidenceNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                            Text(
                                text = draft.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary
                            )
                            Text(
                                text = if (draft.allDay) {
                                    "${s.toLocalDate()} · Ganztägig"
                                } else {
                                    "${s.format(fmt)} – ${e.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                            if (draft.location.isNotBlank()) {
                                Text("Ort: ${draft.location}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                            }
                            if (!draft.rrule.isNullOrBlank()) {
                                Text("Serie: ${draft.rrule}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onOpenInEditor(draft) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Anpassen")
                                }
                                Button(
                                    onClick = { onSaveDraftDirectly(draft) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Eintragen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
