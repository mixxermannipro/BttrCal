package com.bttrcal.app.ui.views

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AgendaView(
    startDate: LocalDate,
    dataRevision: Long,
    loadOccurrences: (Long, Long) -> List<EventOccurrence>,
    onEventClick: (EventOccurrence) -> Unit,
    onCreateNew: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN) }

    val grouped = remember(startDate, dataRevision) {
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = startDate.plusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()
        val occs = loadOccurrences(startMillis, endMillis)
        occs.groupBy { occ ->
            Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).toLocalDate()
        }.toSortedMap()
    }

    if (grouped.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassSurface(
                cornerRadius = 26.dp,
                elevated = true,
                padding = PaddingValues(28.dp),
                onClick = { onCreateNew(startDate) }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Keine Termine in den nächsten 90 Tagen",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Jetzt ersten Termin anlegen",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (date, dayEvents) ->
            item(key = "hdr_$date") {
                val badge = when (date) {
                    today -> "HEUTE"
                    today.plusDays(1) -> "MORGEN"
                    else -> null
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = date.format(dayFmt).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (date == today) palette.accent else palette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.accentSoft)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.accent
                            )
                        }
                    }
                }
            }

            items(
                items = dayEvents,
                key = { "${it.event.id}_${it.occurrenceStartMillis}_$date" }
            ) { occ ->
                MonthAgendaItemCard(
                    occurrence = occ,
                    onClick = { onEventClick(occ) }
                )
            }
        }
    }
}
