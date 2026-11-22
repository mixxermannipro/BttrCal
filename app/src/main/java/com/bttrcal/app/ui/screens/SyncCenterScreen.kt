package com.bttrcal.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bttrcal.app.data.local.AppSettings
import com.bttrcal.app.domain.model.CalendarAccount
import com.bttrcal.app.domain.model.CalendarAccountType
import com.bttrcal.app.domain.model.CalendarColors
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.SyncIntervalOption
import com.bttrcal.app.domain.model.SyncLogEntry
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SyncCenterScreen(
    settings: AppSettings,
    isSyncing: Boolean,
    accounts: List<CalendarAccount>,
    calendars: List<CalendarSource>,
    syncLogs: List<SyncLogEntry>,
    onSyncNow: () -> Unit,
    onSelectInterval: (SyncIntervalOption) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onSyncOnLaunchChanged: (Boolean) -> Unit,
    onSyncAfterEditChanged: (Boolean) -> Unit,
    onToggleCalendarVisible: (String, Boolean) -> Unit,
    onCreateLocalCalendar: (String, Int) -> Unit,
    onAddCalDav: (String, String, String, String, Int) -> Unit,
    onAddIcsSubscription: (String, String, Int) -> Unit,
    onAddGoogleApi: (String, String, String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onImportIcs: (String, String) -> Unit,
    onExportCalendarIcs: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val context = LocalContext.current

    var hasCalendarPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCalendarPerm = grants.values.all { it }
        if (hasCalendarPerm) onSyncNow()
    }

    var importTargetCalId by remember {
        mutableStateOf(calendars.firstOrNull { !it.isReadOnly }?.id ?: "cal_local_private")
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!content.isNullOrBlank()) {
                onImportIcs(content, importTargetCalId)
            }
        }
    }

    var showLocalCalDialog by remember { mutableStateOf(false) }
    var showCalDavDialog by remember { mutableStateOf(false) }
    var calDavPresetName by remember { mutableStateOf("Nextcloud / CalDAV") }
    var calDavPresetUrl by remember { mutableStateOf("") }
    var calDavPresetHint by remember { mutableStateOf("") }
    var calDavPresetColor by remember { mutableIntStateOf(CalendarColors.palette[4]) }
    var showIcsSubDialog by remember { mutableStateOf(false) }
    var icsPresetName by remember { mutableStateOf("Feiertage / Abo") }
    var icsPresetHint by remember { mutableStateOf("") }
    var showGoogleApiDialog by remember { mutableStateOf(false) }

    val timeFmt = remember { DateTimeFormatter.ofPattern("dd.MM. · HH:mm:ss", Locale.GERMAN) }

    val spinInf = rememberInfiniteTransition(label = "sync_spin")
    val spinAngle by spinInf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )

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
                        text = "Sync & Kalender",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = settings.lastSyncSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = if (isSyncing) spinAngle else 0f }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSyncing) "Synct..." else "Sync")
                }
            }
        }

        // Auto-sync interval card
        item {
            LiquidGlassSurface(
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = palette.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Automatischer Hintergrund-Sync",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                    }
                    Text(
                        text = "Wähle das Intervall für den automatischen Abgleich mit Google Kalender, Honor Kalender, CalDAV & ICS-Abos:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SyncIntervalOption.entries.forEach { opt ->
                            ChoiceChipPill(
                                label = opt.labelDe,
                                selected = settings.syncInterval == opt
                            ) {
                                onSelectInterval(opt)
                            }
                        }
                    }

                    ToggleRow("Nur im WLAN synchronisieren", settings.wifiOnlySync, onWifiOnlyChanged)
                    ToggleRow("Beim App-Start automatisch syncen", settings.syncOnLaunch, onSyncOnLaunchChanged)
                    ToggleRow("Nach Termin-Änderung sofort syncen", settings.syncAfterEdit, onSyncAfterEditChanged)

                    if (settings.lastFullSyncAt > 0L) {
                        val lastStr = Instant.ofEpochMilli(settings.lastFullSyncAt)
                            .atZone(ZoneId.systemDefault())
                            .format(timeFmt)
                        Text(
                            text = "Letzter vollständiger Sync: $lastStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary
                        )
                    }
                }
            }
        }

        // Google & Honor System Calendar Integration
        item {
            LiquidGlassSurface(
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Google Kalender & Honor / System-Kalender",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = if (hasCalendarPerm) {
                            "Kalenderzugriff aktiv: Alle auf dem Gerät angemeldeten Google-, Honor- und Exchange-Kalender werden bidirektional synchronisiert."
                        } else {
                            "Erlaube den Kalenderzugriff, um deine Google-Kalender und Honor-Systemkalender direkt zu übernehmen und bidirektional zu synchronisieren."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    if (!hasCalendarPerm) {
                        Button(
                            onClick = {
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Google & System-Kalender verbinden")
                        }
                    }
                }
            }
        }

        // Additional integrations & Import
        item {
            LiquidGlassSurface(
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Weitere Kalender & Anbieter",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Schnellauswahl für Apple iCloud, Outlook / Microsoft 365, Nextcloud, Mailbox.org & Fastmail:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChoiceChipPill("Apple iCloud", false) {
                            calDavPresetName = "Apple iCloud"
                            calDavPresetUrl = "https://caldav.icloud.com"
                            calDavPresetHint = "Nutze deine Apple-ID E-Mail und ein App-spezifisches Passwort (appleid.apple.com)."
                            calDavPresetColor = CalendarColors.palette[0]
                            showCalDavDialog = true
                        }
                        ChoiceChipPill("Outlook / M365", false) {
                            icsPresetName = "Outlook Kalender"
                            icsPresetHint = "Tipp: Falls Outlook auf dem Gerät angemeldet ist, nutze oben „Google & System-Kalender“ für 2-Wege-Sync – oder füge hier den Outlook-ICS-Link ein."
                            showIcsSubDialog = true
                        }
                        ChoiceChipPill("Nextcloud", false) {
                            calDavPresetName = "Nextcloud"
                            calDavPresetUrl = "https://cloud.example.de/remote.php/dav/calendars/USER/personal/"
                            calDavPresetHint = "Kopiere den CalDAV-Link aus deiner Nextcloud Kalender-App."
                            calDavPresetColor = CalendarColors.palette[5]
                            showCalDavDialog = true
                        }
                        ChoiceChipPill("Mailbox.org", false) {
                            calDavPresetName = "Mailbox.org"
                            calDavPresetUrl = "https://dav.mailbox.org/caldav/"
                            calDavPresetHint = "Vollständige CalDAV-URL deines Mailbox.org-Kalenders eingeben."
                            calDavPresetColor = CalendarColors.palette[1]
                            showCalDavDialog = true
                        }
                        ChoiceChipPill("Fastmail", false) {
                            calDavPresetName = "Fastmail"
                            calDavPresetUrl = "https://caldav.fastmail.com/dav/calendars/"
                            calDavPresetHint = "Nutze deinen Fastmail-Benutzernamen und ein App-Passwort."
                            calDavPresetColor = CalendarColors.palette[2]
                            showCalDavDialog = true
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                calDavPresetName = "CalDAV"
                                calDavPresetUrl = ""
                                calDavPresetHint = ""
                                calDavPresetColor = CalendarColors.palette[4]
                                showCalDavDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CalDAV")
                        }
                        OutlinedButton(
                            onClick = {
                                icsPresetName = "Feiertage / Abo"
                                icsPresetHint = ""
                                showIcsSubDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ICS / WebCal")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showGoogleApiDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Google API Token")
                        }
                        OutlinedButton(
                            onClick = {
                                importTargetCalId = calendars.firstOrNull { !it.isReadOnly }?.id ?: "cal_local_private"
                                filePicker.launch(arrayOf("text/calendar", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(".ics Import")
                        }
                    }
                    Button(
                        onClick = { showLocalCalDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Neuen lokalen Kalender anlegen")
                    }
                }
            }
        }

        // Connected accounts
        if (accounts.any { it.accountType != CalendarAccountType.LOCAL }) {
            item {
                Text(
                    text = "Verbundene Konten",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            accounts.filter { it.accountType != CalendarAccountType.LOCAL }.forEach { acc ->
                item(key = "acc_${acc.id}") {
                    LiquidGlassSurface(
                        cornerRadius = 18.dp,
                        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.name, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                Text(
                                    text = "${acc.accountType.labelDe} · ${acc.syncStatusMessage.ifBlank { acc.emailOrUser }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                            }
                            if (acc.accountType != CalendarAccountType.ANDROID_PROVIDER) {
                                IconButton(onClick = { onRemoveAccount(acc.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Entfernen", tint = palette.danger)
                                }
                            }
                        }
                    }
                }
            }
        }

        // All Calendars list
        item {
            Text(
                text = "Sichtbare Kalender (${calendars.size})",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        calendars.forEach { cal ->
            item(key = "cal_${cal.id}") {
                LiquidGlassSurface(
                    cornerRadius = 18.dp,
                    padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(cal.colorArgb))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cal.name, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                            Text(
                                text = cal.accountType.labelDe + if (cal.isReadOnly) " · Nur lesen" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                        IconButton(onClick = { onExportCalendarIcs(cal.id, cal.name) }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = ".ics Export", tint = palette.accent)
                        }
                        Switch(
                            checked = cal.isVisible,
                            onCheckedChange = { onToggleCalendarVisible(cal.id, it) }
                        )
                    }
                }
            }
        }

        // Recent Sync Logs
        if (syncLogs.isNotEmpty()) {
            item {
                Text(
                    text = "Letzte Sync-Protokolle",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            syncLogs.take(6).forEach { log ->
                item(key = "log_${log.id}") {
                    val tStr = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).format(timeFmt)
                    LiquidGlassSurface(
                        cornerRadius = 16.dp,
                        padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (log.success) palette.success else palette.danger)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.sourceLabel, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                                Text("${log.message} · $tStr", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLocalCalDialog) {
        LocalCalendarDialog(
            onDismiss = { showLocalCalDialog = false },
            onCreate = { name, color ->
                onCreateLocalCalendar(name, color)
                showLocalCalDialog = false
            }
        )
    }

    if (showCalDavDialog) {
        CalDavConnectDialog(
            initialName = calDavPresetName,
            initialUrl = calDavPresetUrl,
            hint = calDavPresetHint,
            initialColor = calDavPresetColor,
            onDismiss = { showCalDavDialog = false },
            onConnect = { name, url, user, pass, color ->
                onAddCalDav(name, url, user, pass, color)
                showCalDavDialog = false
            }
        )
    }

    if (showIcsSubDialog) {
        IcsSubscriptionDialog(
            initialName = icsPresetName,
            hint = icsPresetHint,
            onDismiss = { showIcsSubDialog = false },
            onConnect = { name, url, color ->
                onAddIcsSubscription(name, url, color)
                showIcsSubDialog = false
            }
        )
    }

    if (showGoogleApiDialog) {
        GoogleApiConnectDialog(
            onDismiss = { showGoogleApiDialog = false },
            onConnect = { name, email, token ->
                onAddGoogleApi(name, email, token)
                showGoogleApiDialog = false
            }
        )
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalBttrPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColorPickerRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CalendarColors.palette.forEach { c ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (c == selected) 2.5.dp else 0.dp,
                        color = if (c == selected) Color.Black else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
private fun LocalCalendarDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(CalendarColors.palette.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer lokaler Kalender") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                ColorPickerRow(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.ifBlank { "Kalender" }, color) }) { Text("Erstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun CalDavConnectDialog(
    initialName: String,
    initialUrl: String,
    hint: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConnect: (String, String, String, String, Int) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var color by remember(initialColor) { mutableIntStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CalDAV-Kalender verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint.isNotBlank()) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bezeichnung") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Kalender-URL (https://...)") }, singleLine = true)
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Benutzername / E-Mail") }, singleLine = true)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Passwort / App-Passwort") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                ColorPickerRow(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConnect(name, url, user, pass, color) }
            ) { Text("Verbinden") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun IcsSubscriptionDialog(
    initialName: String,
    hint: String,
    onDismiss: () -> Unit,
    onConnect: (String, String, Int) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(CalendarColors.palette[5]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ICS / WebCal-Abo hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint.isNotBlank()) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https:// oder webcal:// URL") }, singleLine = true)
                ColorPickerRow(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onConnect(name, url, color) }) { Text("Abonnieren") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun GoogleApiConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("Google API Direkt") }
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Calendar REST API") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Optionaler Direkt-Sync via OAuth2 Bearer Token (zusätzlich zum System-Provider).",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Kontoname") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Google E-Mail") }, singleLine = true)
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("OAuth Access Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (token.isNotBlank()) onConnect(name, email, token) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
