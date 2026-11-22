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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun YearView(
    year: Int,
    firstDayOfWeek: DayOfWeek,
    dataRevision: Long,
    loadOccurrences: (Long, Long) -> List<EventOccurrence>,
    onSelectMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val months = remember(year) { (1..12).map { YearMonth.of(year, it) } }

    val activeDates = remember(year, dataRevision) {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        loadOccurrences(start, end)
            .map { Instant.ofEpochMilli(it.occurrenceStartMillis).atZone(zone).toLocalDate() }
            .toHashSet()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(months, key = { it.toString() }) { ym ->
            MiniMonthCard(
                month = ym,
                today = today,
                firstDayOfWeek = firstDayOfWeek,
                activeDates = activeDates,
                onClick = { onSelectMonth(ym) }
            )
        }
    }
}

@Composable
private fun MiniMonthCard(
    month: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    activeDates: Set<LocalDate>,
    onClick: () -> Unit
) {
    val palette = LocalBttrPalette.current
    val monthName = remember(month) {
        month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", Locale.GERMAN))
            .replaceFirstChar { it.titlecase(Locale.GERMAN) }
    }
    val gridStart = remember(month, firstDayOfWeek) {
        month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }

    LiquidGlassSurface(
        cornerRadius = 18.dp,
        elevated = false,
        padding = PaddingValues(10.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = monthName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (YearMonth.from(today) == month) palette.accent else palette.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            for (r in 0 until 6) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val d = gridStart.plusDays((r * 7 + c).toLong())
                        val inMonth = YearMonth.from(d) == month
                        val isToday = d == today
                        val hasEvent = inMonth && d in activeDates

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (inMonth) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isToday -> palette.accent
                                                hasEvent -> palette.accentSoft
                                                else -> Color.Transparent
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = d.dayOfMonth.toString(),
                                        fontSize = 8.sp,
                                        fontWeight = if (isToday || hasEvent) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        color = if (isToday) Color.White else palette.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
