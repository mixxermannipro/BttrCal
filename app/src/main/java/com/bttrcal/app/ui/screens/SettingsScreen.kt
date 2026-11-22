package com.bttrcal.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import com.bttrcal.app.domain.model.ThemeMode
import com.bttrcal.app.ui.AiUiState
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import com.bttrcal.app.ui.theme.rememberBttrHaptics
import java.time.DayOfWeek

@Composable
fun SettingsScreen(
    settings: AppSettings,
    aiState: AiUiState,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
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
    val haptics = rememberBttrHaptics()

    BackHandler(onBack = onBack)

    var selectedProvider by remember(settings.aiConfig.provider) { mutableStateOf(settings.aiConfig.provider) }
    var aiModel by remember(settings.aiConfig.model) { mutableStateOf(settings.aiConfig.model) }
    var aiBaseUrl by remember(settings.aiConfig.baseUrl) { mutableStateOf(settings.aiConfig.baseUrl) }
    var aiEnabled by remember(settings.aiConfig.enabled) { mutableStateOf(settings.aiConfig.enabled) }
    var newApiKey by remember(settings.aiConfig.provider) { mutableStateOf("") }
    var showAiAdvanced by remember { mutableStateOf(false) }

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassSurface(
                    cornerRadius = 18.dp,
                    padding = PaddingValues(9.dp),
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Zurück",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Einstellungen",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary
                )
            }
        }

        // Section 1: Erscheinungsbild & Kalenderansicht
        item {
            LiquidGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = palette.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Darstellung", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChipPill("Dunkel", settings.themeMode != ThemeMode.LIGHT) {
                            onSetThemeMode(ThemeMode.DARK)
                        }
                        ChoiceChipPill("Hell", settings.themeMode == ThemeMode.LIGHT) {
                            onSetThemeMode(ThemeMode.LIGHT)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wochenstart", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChipPill("Mo", settings.firstDayOfWeek == DayOfWeek.MONDAY) {
                                onSetFirstDayOfWeek(DayOfWeek.MONDAY)
                            }
                            ChoiceChipPill("So", settings.firstDayOfWeek == DayOfWeek.SUNDAY) {
                                onSetFirstDayOfWeek(DayOfWeek.SUNDAY)
                            }
                        }
                    }

                    ToggleRow("Kalenderwochen (KW)", settings.showWeekNumbers, onSetShowWeekNumbers)
                }
            }
        }

        // Section 2: Bring Your Own Key KI
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
                        Text("Bring Your Own Key (KI)", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    }
                    Text(
                        text = "Key wird lokal im Android Keystore (AES-256) verschlüsselt. Ohne Key läuft die Offline-Spracherkennung.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )

                    ToggleRow("BYOK-KI aktiv", aiEnabled) { aiEnabled = it }

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
                                    "API-Key (gespeichert)"
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAiAdvanced = !showAiAdvanced }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modell & Endpunkt (${aiModel})",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showAiAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = palette.textSecondary
                        )
                    }

                    AnimatedVisibility(visible = showAiAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                label = { Text("Base-URL") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                onTestAiConnection(selectedProvider, aiModel, aiBaseUrl, newApiKey)
                            },
                            enabled = !aiState.isTestingConnection,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (aiState.isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Testen")
                        }
                        Button(
                            onClick = {
                                haptics.confirm()
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

        // Section 3: Termine & System
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
                        Text("Termine & Benachrichtigungen", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    }

                    Text("Standard-Dauer", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
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

                    if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Benachrichtigungen erlauben")
                        }
                    } else {
                        Text(
                            text = "Lokale Erinnerungen aktiv · BttrCal v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary
                        )
                    }
                }
            }
        }
    }
}
