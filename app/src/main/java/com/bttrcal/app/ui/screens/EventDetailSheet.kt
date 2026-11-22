package com.bttrcal.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bttrcal.app.domain.engine.RecurrenceEngine
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    occurrence: EventOccurrence,
    isReadOnly: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShareIcs: () -> Unit
) {
    val palette = LocalBttrPalette.current
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN) }

    val ev = occurrence.event
    val startZdt = Instant.ofEpochMilli(occurrence.occurrenceStartMillis).atZone(zone)
    val endZdt = Instant.ofEpochMilli(occurrence.occurrenceEndMillis).atZone(zone)
    val accentColor = Color(occurrence.displayColorArgb)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Calendar badge + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = occurrence.calendarName,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
            }

            Text(
                text = ev.title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary
            )

            LiquidGlassSurface(
                cornerRadius = 20.dp,
                padding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val timeValue = if (ev.allDay) {
                        "${startZdt.format(dateFmt)} · Ganztägig"
                    } else {
                        "${startZdt.format(dateFmt)}\n${startZdt.format(timeFmt)} – ${endZdt.format(timeFmt)} Uhr"
                    }
                    DetailRow(Icons.Rounded.Schedule, timeValue, palette.accent)

                    val rruleText = RecurrenceEngine.parseRRule(ev.rrule)?.toGermanSummary()
                    if (rruleText != null) {
                        DetailRow(Icons.Rounded.Repeat, rruleText, palette.textSecondary)
                    }

                    if (ev.location.isNotBlank()) {
                        DetailRow(
                            icon = Icons.Rounded.Place,
                            text = "${ev.location} (in Karte öffnen)",
                            iconTint = palette.accent,
                            onClick = {
                                val uri = Uri.parse("geo:0,0?q=${Uri.encode(ev.location)}")
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        )
                    }

                    val remText = if (ev.reminderMinutes.isEmpty()) {
                        "Keine Erinnerung"
                    } else {
                        "Erinnerung: " + ev.reminderMinutes.joinToString(", ") { formatReminderMin(it) }
                    }
                    DetailRow(Icons.Rounded.NotificationsNone, remText, palette.textSecondary)

                    if (ev.description.isNotBlank()) {
                        DetailRow(Icons.Rounded.Notes, ev.description, palette.textSecondary)
                    }
                }
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isReadOnly) {
                    ActionPill(
                        icon = Icons.Rounded.Edit,
                        label = "Bearbeiten",
                        tint = palette.accent,
                        modifier = Modifier.weight(1f),
                        onClick = onEdit
                    )
                }
                ActionPill(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Kopie",
                    tint = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = onDuplicate
                )
                ActionPill(
                    icon = Icons.Rounded.Share,
                    label = ".ics",
                    tint = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = onShareIcs
                )
                if (!isReadOnly) {
                    ActionPill(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "Löschen",
                        tint = palette.danger,
                        modifier = Modifier.weight(1f),
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    text: String,
    iconTint: Color,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalBttrPalette.current
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (onClick != null) palette.accent else palette.textPrimary
            )
        }
    }

    if (onClick != null) {
        LiquidGlassSurface(
            cornerRadius = 12.dp,
            padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            onClick = onClick
        ) { rowContent() }
    } else {
        rowContent()
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 16.dp,
        padding = PaddingValues(vertical = 10.dp, horizontal = 6.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

fun formatReminderMin(minutes: Int): String = when {
    minutes == 0 -> "Zum Beginn"
    minutes < 60 -> "$minutes Min vorher"
    minutes == 60 -> "1 Std vorher"
    minutes % 1440 == 0 -> "${minutes / 1440} Tag(e) vorher"
    else -> "${minutes / 60} Std vorher"
}
