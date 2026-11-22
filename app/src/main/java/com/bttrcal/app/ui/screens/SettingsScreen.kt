package com.bttrcal.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bttrcal.app.BuildConfig
import com.bttrcal.app.data.local.AppSettings
import com.bttrcal.app.domain.model.AiProviderType
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.ThemeMode
import com.bttrcal.app.ui.AiUiState
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.DayOfWeek

@Composable
fun SettingsScreen(
    settings: AppSettings,
    aiState: AiUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetGlassVariant: (GlassVariant) -> Unit,
    onSetFirstDayOfWeek: (DayOfWeek) -> Unit,
    onSetShowWeekNumbers: (Boolean) -> Unit,
    onSetDefaultDuration: (Int) -> Unit,
    onSetDefaultReminder: (Int) -> Unit,
    onSaveAiConfig: (AiProviderType, String, String, Boolean, String?) -> Unit,
    onTestAiConnection: (AiProviderType, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val context = LocalContext.current

    var selectedProvider by remember(settings.aiConfig.provider) { mutableStateOf(settings.aiConfig.provider) }
    var aiModel by remember(settings.aiConfig.model) { mutableStateOf(settings.aiConfig.model) }
    var aiBaseUrl by remember(settings.aiConfig.baseUrl) { mutableStateOf(settings.aiConfig.baseUrl) }
    var aiEnabled by remember(settings.aiConfig.enabled) { mutableStateOf(settings.aiConfig.enabled) }
    var newApiKey by remember(settings.aiConfig.provider) { mutableStateOf("") }

    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifGranted = ok
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Einstellungen",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.textPrimary
            )
            Text(
                text = "Design, Bring Your Own Key KI & Kalender-Standardwerte",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }

        // Theme & Liquid Glass Mode
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = palette.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Erscheinungsbild", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            ChoiceChipPill(mode.labelDe, settings.themeMode == mode) {
                                onSetThemeMode(mode)
                            }
                        }
                    }
                    if (settings.themeMode == ThemeMode.LIQUID_GLASS) {
                        Text("Liquid Glass Tönung", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassVariant.entries.forEach { variant ->
                                ChoiceChipPill(variant.labelDe, settings.glassVariant == variant) {
                                    onSetGlassVariant(variant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // BYOK AI Configuration
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = palette.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bring Your Own Key (BYOK) KI", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    }
                    Text(
                        text = "Dein API-Schlüssel wird direkt auf dem Gerät im Android Keystore (AES-256) verschlüsselt gespeichert und nur direkt an deinen Anbieter gesendet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )

                    ToggleRow("BYOK-KI verwenden", aiEnabled) { aiEnabled = it }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiProviderType.entries.forEach { prov ->
                            ChoiceChipPill(prov.displayName, selectedProvider == prov) {
                                selectedProvider = prov
                                aiModel = prov.defaultModel
                                aiBaseUrl = prov.defaultBaseUrl
                                newApiKey = ""
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it },
                        label = {
                            Text(
                                if (settings.aiConfig.hasStoredKey && selectedProvider == settings.aiConfig.provider) {
                                    "API-Key (gespeichert · zum Ändern neu eingeben)"
                                } else {
                                    "API-Key eingeben"
                                }
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = { aiModel = it },
                        label = { Text("Modell") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = aiBaseUrl,
                        onValueChange = { aiBaseUrl = it },
                        label = { Text("Endpunkt / Base-URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onTestAiConnection(selectedProvider, aiModel, aiBaseUrl, newApiKey) },
                            enabled = !aiState.isTestingConnection,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (aiState.isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Verbindung testen")
                        }
                        Button(
                            onClick = {
                                val keyUpdate = if (newApiKey.isNotBlank()) newApiKey else null
                                onSaveAiConfig(selectedProvider, aiModel, aiBaseUrl, aiEnabled, keyUpdate)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Speichern")
                        }
                    }

                    aiState.testResult?.let { res ->
                        Text(
                            text = res.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (res.success) palette.success else palette.danger
                        )
                    }
                }
            }
        }

        // Calendar defaults
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = palette.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kalender-Vorgaben", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    }

                    Text("Wochenstart", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChipPill("Montag", settings.firstDayOfWeek == DayOfWeek.MONDAY) {
                            onSetFirstDayOfWeek(DayOfWeek.MONDAY)
                        }
                        ChoiceChipPill("Sonntag", settings.firstDayOfWeek == DayOfWeek.SUNDAY) {
                            onSetFirstDayOfWeek(DayOfWeek.SUNDAY)
                        }
                    }

                    ToggleRow("Kalenderwochen (KW) anzeigen", settings.showWeekNumbers, onSetShowWeekNumbers)

                    Text("Standard-Termindauer", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 45, 60, 90).forEach { mins ->
                            ChoiceChipPill("$mins Min", settings.defaultEventDurationMinutes == mins) {
                                onSetDefaultDuration(mins)
                            }
                        }
                    }

                    Text("Standard-Erinnerung", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(-1 to "Keine", 10 to "10 Min", 15 to "15 Min", 30 to "30 Min", 60 to "1 Std").forEach { (m, label) ->
                            ChoiceChipPill(label, settings.defaultReminderMinutes == m) {
                                onSetDefaultReminder(m)
                            }
                        }
                    }
                }
            }
        }

        // Notifications & About
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = palette.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Benachrichtigungen & System", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    }
                    if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Erinnerungs-Benachrichtigungen erlauben")
                        }
                    } else {
                        Text(
                            text = "Lokale Erinnerungen sind aktiv.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.success
                        )
                    }
                    Text(
                        text = "BttrCal v${BuildConfig.VERSION_NAME} · Local-First SQLite + Android Keystore",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary
                    )
                }
            }
        }
    }
}
