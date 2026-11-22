package com.bttrcal.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.ThemeMode
import com.bttrcal.app.domain.model.WidgetStyleConfig
import com.bttrcal.app.ui.theme.BouncyEntrance
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import com.bttrcal.app.ui.theme.rememberBttrHaptics
import com.bttrcal.app.widget.AgendaListWidgetProvider
import com.bttrcal.app.widget.MonthAgendaWidgetProvider
import com.bttrcal.app.widget.SplitMonthWidgetProvider
import com.bttrcal.app.widget.TodayCardWidgetProvider
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun WidgetStudioScreen(
    widgetStyle: WidgetStyleConfig,
    dataRevision: Long,
    onBack: () -> Unit,
    loadOccurrences: (Long, Long) -> List<EventOccurrence>,
    onUpdateWidgetStyle: (WidgetStyleConfig) -> Unit,
    onStatusMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val context = LocalContext.current
    val haptics = rememberBttrHaptics()
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)

    BackHandler(onBack = onBack)

    val upcoming = remember(dataRevision) {
        val now = System.currentTimeMillis()
        loadOccurrences(now - 3_600_000L, now + 14L * 86_400_000L)
            .filter { occ -> occ.event.allDay || occ.occurrenceEndMillis > now }
            .distinctBy { it.event.title.trim().lowercase(Locale.GERMAN) }
            .take(4)
    }

    fun pinWidget(providerClass: Class<*>, label: String) {
        haptics.confirm()
        val mgr = AppWidgetManager.getInstance(context)
        if (mgr.isRequestPinAppWidgetSupported) {
            val ok = mgr.requestPinAppWidget(ComponentName(context, providerClass), null, null)
            onStatusMessage(
                if (ok) "Anfrage für „$label“ an Launcher gesendet"
                else "Bitte Widget manuell über Startbildschirm -> Widgets hinzufügen"
            )
        } else {
            onStatusMessage("Launcher unterstützt Direkt-Pinning nicht – bitte über Homescreen -> Widgets hinzufügen")
        }
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
                Column {
                    Text(
                        text = "Widgets",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "Liquid Glass, Dunkel oder Hell für deinen Startbildschirm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
            }
        }

        // Widget style controls
        item {
            LiquidGlassSurface(
                cornerRadius = 24.dp,
                elevated = true,
                padding = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Widget-Design", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            ChoiceChipPill(
                                label = mode.labelDe,
                                selected = widgetStyle.themeMode == mode
                            ) {
                                onUpdateWidgetStyle(widgetStyle.copy(themeMode = mode))
                            }
                        }
                    }
                    if (widgetStyle.themeMode == ThemeMode.LIQUID_GLASS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassVariant.entries.forEach { variant ->
                                ChoiceChipPill(
                                    label = variant.labelDe,
                                    selected = widgetStyle.glassVariant == variant
                                ) {
                                    onUpdateWidgetStyle(widgetStyle.copy(glassVariant = variant))
                                }
                            }
                        }
                        Text(
                            text = "Glas-Deckkraft: ${(widgetStyle.glassOpacity * 100).toInt()} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary
                        )
                        Slider(
                            value = widgetStyle.glassOpacity,
                            onValueChange = { onUpdateWidgetStyle(widgetStyle.copy(glassOpacity = it)) },
                            valueRange = 0.45f..0.98f
                        )
                    }
                }
            }
        }

        // Widget 0 (Flagship): Video-Matched Split Glass Widget (4x2)
        item {
            BouncyEntrance(delayIndex = 0) {
                WidgetPreviewWrapper(
                    title = "Split Glas (4×2 · Video-Stil)",
                    subtitle = "Mini-Monat mit Korallen-Heute-Kreis links & Glas-Terminkarten rechts",
                    onPin = { pinWidget(SplitMonthWidgetProvider::class.java, "BttrCal Split Glas") }
                ) {
                    SplitMonthWidgetPreview(widgetStyle, today, upcoming)
                }
            }
        }

        // Widget 1: Honor-Style Month + Agenda Preview
        item {
            BouncyEntrance(delayIndex = 1) {
                WidgetPreviewWrapper(
                    title = "Monat & Termine (4×4 · Honor-Stil)",
                    subtitle = "Monatsraster mit Blättern, Punkten und kommenden Terminen",
                    onPin = { pinWidget(MonthAgendaWidgetProvider::class.java, "Monat & Termine") }
                ) {
                    HonorMonthWidgetPreview(widgetStyle, today, upcoming)
                }
            }
        }

        // Widget 2: Today Card Preview
        item {
            BouncyEntrance(delayIndex = 2) {
                WidgetPreviewWrapper(
                    title = "Heute-Karte (4×2 · Apple-Stil)",
                    subtitle = "Großes Tagesdatum links, bis zu 4 heutige Termine rechts",
                    onPin = { pinWidget(TodayCardWidgetProvider::class.java, "Heute-Karte") }
                ) {
                    TodayCardPreview(widgetStyle, today, upcoming)
                }
            }
        }

        // Widget 3: Scrollable Agenda Preview
        item {
            BouncyEntrance(delayIndex = 3) {
                WidgetPreviewWrapper(
                    title = "Scrollbare Agenda-Liste (4×3)",
                    subtitle = "Kommende 30 Tage mit direktem Sync-Button & Schnell-Erstellung",
                    onPin = { pinWidget(AgendaListWidgetProvider::class.java, "Agenda-Liste") }
                ) {
                    AgendaListPreview(widgetStyle, upcoming)
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewWrapper(
    title: String,
    subtitle: String,
    onPin: () -> Unit,
    content: @Composable () -> Unit
) {
    val palette = LocalBttrPalette.current
    LiquidGlassSurface(
        cornerRadius = 24.dp,
        elevated = true,
        padding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Widgets, contentDescription = null, tint = palette.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                }
            }
            content()
            Button(
                onClick = onPin,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auf Startbildschirm hinzufügen")
            }
        }
    }
}

@Composable
private fun previewSurfaceColors(style: WidgetStyleConfig): Pair<Color, Color> {
    val isDark = style.themeMode == ThemeMode.DARK ||
        (style.themeMode == ThemeMode.LIQUID_GLASS && style.glassVariant == GlassVariant.DARK_GLASS)
    val bg = when (style.themeMode) {
        ThemeMode.LIGHT -> Color(0xFFFDFEFF)
        ThemeMode.DARK -> Color(0xFF151923)
        ThemeMode.LIQUID_GLASS -> if (isDark) Color(0xFF182133).copy(alpha = style.glassOpacity)
        else Color(0xFFF2F7FF).copy(alpha = style.glassOpacity)
    }
    val fg = if (isDark) Color(0xFFF4F6FA) else Color(0xFF0F172A)
    return bg to fg
}

@Composable
private fun HonorMonthWidgetPreview(
    style: WidgetStyleConfig,
    today: LocalDate,
    upcoming: List<EventOccurrence>
) {
    val (bg, fg) = previewSurfaceColors(style)
    val ym = YearMonth.from(today)
    val gridStart = ym.atDay(1).with(TemporalAdjusters.previousOrSame(style.firstDayOfWeek))
    val monthTitle = today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
        .replaceFirstChar { it.titlecase(Locale.GERMAN) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(monthTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = fg, modifier = Modifier.weight(1f))
                Text("‹   ›", color = Color(0xFFFF4F38), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF4F38)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            for (r in 0 until 5) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val d = gridStart.plusDays((r * 7 + c).toLong())
                        val isToday = d == today
                        val inMonth = YearMonth.from(d) == ym
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isToday) Color(0xFFFF4F38) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = d.dayOfMonth.toString(),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    color = when {
                                        isToday -> Color.White
                                        inMonth -> fg
                                        else -> fg.copy(alpha = 0.35f)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            upcoming.take(2).forEach { occ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(14.dp)
                            .background(Color(occ.displayColorArgb), RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        occ.event.title,
                        fontSize = 12.sp,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCardPreview(
    style: WidgetStyleConfig,
    today: LocalDate,
    upcoming: List<EventOccurrence>
) {
    val (bg, fg) = previewSurfaceColors(style)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(78.dp)) {
                Text(
                    today.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF3B30)
                )
                Text(today.dayOfMonth.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (upcoming.isEmpty()) {
                    Text("Freier Tag", fontSize = 12.sp, color = fg.copy(alpha = 0.7f))
                } else {
                    upcoming.take(3).forEach { occ ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .background(Color(occ.displayColorArgb), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(occ.event.title, fontSize = 12.sp, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaListPreview(
    style: WidgetStyleConfig,
    upcoming: List<EventOccurrence>
) {
    val (bg, fg) = previewSurfaceColors(style)
    val timeFmt = remember { DateTimeFormatter.ofPattern("dd.MM · HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BttrCal · Agenda", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg, modifier = Modifier.weight(1f))
                Text("Sync  +", color = Color(0xFFFF4F38), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (upcoming.isEmpty()) {
                Text("Keine anstehenden Termine", fontSize = 12.sp, color = fg.copy(alpha = 0.6f))
            } else {
                upcoming.take(3).forEach { occ ->
                    val s = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).format(timeFmt)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(Color(occ.displayColorArgb), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(occ.event.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
                            Text(s, fontSize = 10.sp, color = fg.copy(alpha = 0.65f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitMonthWidgetPreview(
    style: WidgetStyleConfig,
    today: LocalDate,
    upcoming: List<EventOccurrence>
) {
    val isDark = style.themeMode == ThemeMode.DARK ||
        (style.themeMode == ThemeMode.LIQUID_GLASS && style.glassVariant == GlassVariant.DARK_GLASS)
    val isGlass = style.themeMode == ThemeMode.LIQUID_GLASS

    val cardBg = when (style.themeMode) {
        ThemeMode.LIGHT -> Color(0xFFFAFBFD)
        ThemeMode.DARK -> Color(0xFF151820)
        ThemeMode.LIQUID_GLASS -> if (isDark) Color(0xFF1C2338).copy(alpha = 0.44f) else Color(0xFFFFFFFF).copy(alpha = 0.78f)
    }
    val innerCardBg = when (style.themeMode) {
        ThemeMode.LIGHT -> Color(0xFFF1F4F9)
        ThemeMode.DARK -> Color(0xFF222634)
        ThemeMode.LIQUID_GLASS -> if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.70f)
    }
    val fg = if (isDark) Color.White else Color(0xFF0F172A)
    val muted = if (isDark) Color.White.copy(alpha = 0.72f) else Color(0xFF526075)
    val ym = YearMonth.from(today)
    val gridStart = ym.atDay(1).with(TemporalAdjusters.previousOrSame(style.firstDayOfWeek))
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN) }
    val zone = remember { ZoneId.systemDefault() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (isGlass) {
                    Brush.linearGradient(
                        if (isDark) listOf(Color(0xFF1E2942), Color(0xFF2B3554), Color(0xFF1A2238))
                        else listOf(Color(0xFFD7E7FF), Color(0xFFECE4FF), Color(0xFFD2F3F7))
                    )
                } else {
                    Brush.verticalGradient(listOf(cardBg, cardBg))
                }
            )
            .padding(if (isGlass) 8.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .border(1.dp, if (isDark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.45f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        today.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EE", Locale.GERMAN)).take(2),
                        fontSize = 10.sp,
                        color = muted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                for (r in 0 until 5) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val d = gridStart.plusDays((r * 7 + c).toLong())
                            val isToday = d == today
                            val inMonth = YearMonth.from(d) == ym
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(17.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(if (isToday) Color(0xFFFF453A) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        d.dayOfMonth.toString(),
                                        fontSize = 8.5.sp,
                                        color = when {
                                            isToday -> Color.White
                                            inMonth -> fg
                                            else -> fg.copy(alpha = 0.42f)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ANSTEHEND", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = muted, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF4F38)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                upcoming.take(3).forEach { occ ->
                    val s = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone)
                    val e = Instant.ofEpochMilli(occ.occurrenceEndMillis).atZone(zone)
                    val tStr = if (occ.event.allDay) "Ganztägig" else "${s.format(timeFmt)}–${e.format(timeFmt)}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(innerCardBg)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(occ.displayColorArgb))
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Column {
                            Text(occ.event.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tStr, fontSize = 9.5.sp, color = muted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
