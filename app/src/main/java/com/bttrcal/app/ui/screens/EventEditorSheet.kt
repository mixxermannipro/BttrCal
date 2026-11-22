package com.bttrcal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bttrcal.app.domain.model.CalendarColors
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.ui.EditorState
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
    editorState: EditorState,
    calendars: List<CalendarSource>,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit
) {
    val palette = LocalBttrPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val writableCalendars = remember(calendars) { calendars.filter { !it.isReadOnly } }

    val initialStart = remember(editorState) {
        Instant.ofEpochMilli(editorState.prefillStartMillis).atZone(zone).toLocalDateTime()
    }
    val initialEnd = remember(editorState) {
        Instant.ofEpochMilli(editorState.prefillEndMillis).atZone(zone).toLocalDateTime()
    }

    var title by remember(editorState) { mutableStateOf(editorState.prefillTitle) }
    var location by remember(editorState) { mutableStateOf(editorState.prefillLocation) }
    var description by remember(editorState) { mutableStateOf(editorState.prefillDescription) }
    var url by remember(editorState) { mutableStateOf(editorState.existingEvent?.url.orEmpty()) }
    var allDay by remember(editorState) { mutableStateOf(editorState.prefillAllDay) }

    var startDate by remember(editorState) { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember(editorState) { mutableStateOf(initialStart.toLocalTime()) }
    var endDate by remember(editorState) { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember(editorState) { mutableStateOf(initialEnd.toLocalTime()) }

    var selectedCalendarId by remember(editorState) {
        mutableStateOf(
            editorState.prefillCalendarId
                ?: writableCalendars.firstOrNull()?.id
                ?: calendars.firstOrNull()?.id
                ?: "cal_local_private"
        )
    }
    var rrule by remember(editorState) { mutableStateOf(editorState.prefillRRule) }
    var reminderMinutes by remember(editorState) {
        mutableIntStateOf(editorState.prefillReminders.firstOrNull() ?: 15)
    }
    var availability by remember(editorState) {
        mutableStateOf(editorState.existingEvent?.availability ?: EventAvailability.BUSY)
    }
    var colorOverride by remember(editorState) {
        mutableStateOf(editorState.existingEvent?.colorOverrideArgb)
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMAN) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (palette.isDark) Color(0xFF141925) else Color(0xFFFCFDFE)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassSurface(
                    cornerRadius = 14.dp,
                    padding = PaddingValues(8.dp),
                    onClick = onDismiss
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Abbrechen", tint = palette.textSecondary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (editorState.existingEvent == null) "Neuer Termin" else "Termin bearbeiten",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val sMillis = if (allDay) {
                            startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                        } else {
                            LocalDateTime.of(startDate, startTime).atZone(zone).toInstant().toEpochMilli()
                        }
                        var eMillis = if (allDay) {
                            val safeEndD = if (endDate.isBefore(startDate)) startDate else endDate
                            safeEndD.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        } else {
                            LocalDateTime.of(endDate, endTime).atZone(zone).toInstant().toEpochMilli()
                        }
                        if (eMillis <= sMillis) {
                            eMillis = sMillis + if (allDay) 86_400_000L else 3_600_000L
                        }

                        val existing = editorState.existingEvent
                        val now = System.currentTimeMillis()
                        val result = CalendarEvent(
                            id = existing?.id ?: "evt_${UUID.randomUUID()}",
                            uid = existing?.uid ?: UUID.randomUUID().toString(),
                            calendarId = selectedCalendarId,
                            title = title.trim().ifBlank { "Neuer Termin" },
                            description = description.trim(),
                            location = location.trim(),
                            url = url.trim(),
                            startMillis = sMillis,
                            endMillis = eMillis,
                            allDay = allDay,
                            timeZoneId = zone.id,
                            rrule = rrule,
                            exdates = existing?.exdates ?: emptyList(),
                            reminderMinutes = if (reminderMinutes >= 0) listOf(reminderMinutes) else emptyList(),
                            availability = availability,
                            colorOverrideArgb = colorOverride,
                            androidEventId = existing?.androidEventId,
                            remoteId = existing?.remoteId,
                            etag = existing?.etag,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now
                        )
                        onSave(result)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Speichern")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                placeholder = { Text("z. B. Meeting, Sport, Zahnarzt") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Calendar picker
            Text("Kalender", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                writableCalendars.forEach { cal ->
                    val selected = cal.id == selectedCalendarId
                    val c = Color(cal.colorArgb)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) c.copy(alpha = 0.2f) else palette.surfaceSubtle)
                            .border(
                                width = if (selected) 1.4.dp else 0.7.dp,
                                color = if (selected) c else palette.border,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { selectedCalendarId = cal.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(c)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cal.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary
                        )
                    }
                }
            }

            // Date & time block
            LiquidGlassSurface(
                cornerRadius = 20.dp,
                padding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ganztägig",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = allDay, onCheckedChange = { allDay = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Beginn", modifier = Modifier.width(56.dp), color = palette.textSecondary)
                        LiquidGlassSurface(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 12.dp,
                            padding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            onClick = { showStartDatePicker = true }
                        ) {
                            Text(startDate.format(dateFmt), color = palette.textPrimary)
                        }
                        if (!allDay) {
                            LiquidGlassSurface(
                                cornerRadius = 12.dp,
                                padding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                                onClick = { showStartTimePicker = true }
                            ) {
                                Text(startTime.format(timeFmt), color = palette.accent)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Ende", modifier = Modifier.width(56.dp), color = palette.textSecondary)
                        LiquidGlassSurface(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 12.dp,
                            padding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            onClick = { showEndDatePicker = true }
                        ) {
                            Text(endDate.format(dateFmt), color = palette.textPrimary)
                        }
                        if (!allDay) {
                            LiquidGlassSurface(
                                cornerRadius = 12.dp,
                                padding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                                onClick = { showEndTimePicker = true }
                            ) {
                                Text(endTime.format(timeFmt), color = palette.accent)
                            }
                        }
                    }
                }
            }

            // Recurrence
            Text("Wiederholung", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            val rruleOptions = listOf(
                null to "Nie",
                "FREQ=DAILY" to "Täglich",
                "FREQ=WEEKLY" to "Wöchentlich",
                "FREQ=MONTHLY" to "Monatlich",
                "FREQ=YEARLY" to "Jährlich"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rruleOptions.forEach { (ruleVal, label) ->
                    val active = rrule == ruleVal
                    ChoiceChipPill(label = label, selected = active) { rrule = ruleVal }
                }
            }

            // Reminders
            Text("Erinnerung", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            val remOptions = listOf(-1 to "Keine", 0 to "Beginn", 10 to "10 Min", 15 to "15 Min", 30 to "30 Min", 60 to "1 Std", 1440 to "1 Tag")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                remOptions.forEach { (mins, label) ->
                    ChoiceChipPill(label = label, selected = reminderMinutes == mins) { reminderMinutes = mins }
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Ort") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notizen") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Availability + Color override
            Text("Verfügbarkeit", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventAvailability.entries.forEach { av ->
                    ChoiceChipPill(label = av.labelDe, selected = availability == av) {
                        availability = av
                    }
                }
            }

            Text("Farbakzent (optional)", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChoiceChipPill(label = "Standard", selected = colorOverride == null) {
                    colorOverride = null
                }
                CalendarColors.palette.forEach { argb ->
                    val selected = colorOverride == argb
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (selected) 2.5.dp else 0.dp,
                                color = if (selected) palette.textPrimary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { colorOverride = argb }
                    )
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link / Meeting-URL") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (editorState.existingEvent != null) {
                Spacer(modifier = Modifier.height(4.dp))
                LiquidGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    padding = PaddingValues(vertical = 12.dp),
                    onClick = { onDelete(editorState.existingEvent.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = palette.danger)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Termin löschen", color = palette.danger, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        SimpleDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showStartDatePicker = false },
            onConfirm = { picked ->
                startDate = picked
                if (endDate.isBefore(picked)) endDate = picked
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        SimpleDatePickerDialog(
            initialDate = endDate,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { picked ->
                endDate = if (picked.isBefore(startDate)) startDate else picked
                showEndDatePicker = false
            }
        )
    }

    if (showStartTimePicker) {
        SimpleTimePickerDialog(
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { picked ->
                startTime = picked
                if (endDate == startDate && !endTime.isAfter(picked)) {
                    endTime = picked.plusHours(1)
                }
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        SimpleTimePickerDialog(
            initialTime = endTime,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { picked ->
                endTime = picked
                showEndTimePicker = false
            }
        )
    }
}

@Composable
fun ChoiceChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalBttrPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accentSoft else palette.surfaceSubtle)
            .border(
                width = if (selected) 1.2.dp else 0.7.dp,
                color = if (selected) palette.accent else palette.border,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.accent else palette.textSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                val picked = if (millis != null) {
                    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                } else initialDate
                onConfirm(picked)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        text = {
            TimePicker(state = state)
        }
    )
}
