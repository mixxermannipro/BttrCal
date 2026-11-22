package com.bttrcal.app.ui.views

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import com.bttrcal.app.ui.theme.bounceClick
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private const val MONTH_BASE_PAGE = 600

@Composable
fun MonthView(
    selectedDate: LocalDate,
    visibleMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    showWeekNumbers: Boolean,
    dataRevision: Long,
    loadOccurrences: (Long, Long) -> List<EventOccurrence>,
    onSelectDate: (LocalDate) -> Unit,
    onVisibleMonthChanged: (YearMonth) -> Unit,
    onEventClick: (EventOccurrence) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val baseMonth = remember { YearMonth.now() }
    val targetPage = remember(visibleMonth) {
        val diff = ((visibleMonth.year - baseMonth.year) * 12) + (visibleMonth.monthValue - baseMonth.monthValue)
        (MONTH_BASE_PAGE + diff).coerceIn(0, MONTH_BASE_PAGE * 2)
    }

    val pagerState = rememberPagerState(
        initialPage = targetPage,
        pageCount = { MONTH_BASE_PAGE * 2 + 1 }
    )

    LaunchedEffect(targetPage) {
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(
                targetPage,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val ym = baseMonth.plusMonths((pagerState.settledPage - MONTH_BASE_PAGE).toLong())
        if (ym != visibleMonth) {
            onVisibleMonthChanged(ym)
        }
    }

    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)

    val dayStart = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val selectedDayEvents = remember(selectedDate, dataRevision) {
        loadOccurrences(dayStart, dayEnd)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Month grid card
        LiquidGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            cornerRadius = 26.dp,
            elevated = true,
            padding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Weekday labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showWeekNumbers) {
                        Text(
                            text = "KW",
                            modifier = Modifier.width(26.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary
                        )
                    }
                    for (i in 0 until 7) {
                        val dow = firstDayOfWeek.plus(i.toLong())
                        val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
                        Text(
                            text = shortDowDe(dow),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isWeekend) palette.textTertiary else palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    beyondViewportPageCount = 1
                ) { page ->
                    val pageYm = baseMonth.plusMonths((page - MONTH_BASE_PAGE).toLong())
                    MonthGridPage(
                        month = pageYm,
                        today = today,
                        selectedDate = selectedDate,
                        firstDayOfWeek = firstDayOfWeek,
                        showWeekNumbers = showWeekNumbers,
                        dataRevision = dataRevision,
                        loadOccurrences = loadOccurrences,
                        onSelectDate = onSelectDate
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected day header
        val headerFmt = remember { DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (selectedDate) {
                    today -> "Heute · ${selectedDate.format(headerFmt)}"
                    today.plusDays(1) -> "Morgen · ${selectedDate.format(headerFmt)}"
                    else -> selectedDate.format(headerFmt).replaceFirstChar { it.titlecase(Locale.GERMAN) }
                },
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassSurface(
                cornerRadius = 14.dp,
                padding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                onClick = { onAddForDate(selectedDate) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Termin hinzufügen",
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Neu",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent
                    )
                }
            }
        }

        if (selectedDayEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                LiquidGlassSurface(
                    cornerRadius = 22.dp,
                    padding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
                    onClick = { onAddForDate(selectedDate) }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.EventAvailable,
                            contentDescription = null,
                            tint = palette.accent.copy(alpha = 0.75f),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Freier Tag",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tippen, um einen neuen Termin zu erstellen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = selectedDayEvents,
                    key = { "${it.event.id}_${it.occurrenceStartMillis}" }
                ) { occ ->
                    MonthAgendaItemCard(
                        occurrence = occ,
                        onClick = { onEventClick(occ) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGridPage(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    showWeekNumbers: Boolean,
    dataRevision: Long,
    loadOccurrences: (Long, Long) -> List<EventOccurrence>,
    onSelectDate: (LocalDate) -> Unit
) {
    val palette = LocalBttrPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val gridStart = remember(month, firstDayOfWeek) {
        month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }
    val weekFields = remember(firstDayOfWeek) { WeekFields.of(firstDayOfWeek, 4) }

    val eventsByDate = remember(month, firstDayOfWeek, dataRevision) {
        val startMillis = gridStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = gridStart.plusDays(42).atStartOfDay(zone).toInstant().toEpochMilli()
        val list = loadOccurrences(startMillis, endMillis)
        val map = HashMap<LocalDate, MutableList<EventOccurrence>>()
        for (occ in list) {
            var d = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).toLocalDate()
            val endD = Instant.ofEpochMilli((occ.occurrenceEndMillis - 1L).coerceAtLeast(occ.occurrenceStartMillis))
                .atZone(zone).toLocalDate()
            while (!d.isAfter(endD)) {
                if (!d.isBefore(gridStart) && d.isBefore(gridStart.plusDays(42))) {
                    map.getOrPut(d) { mutableListOf() }.add(occ)
                }
                d = d.plusDays(1)
            }
        }
        map
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (weekRow in 0 until 6) {
            val rowFirstDate = gridStart.plusDays(weekRow * 7L)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showWeekNumbers) {
                    val kw = rowFirstDate.get(weekFields.weekOfWeekBasedYear())
                    Text(
                        text = kw.toString(),
                        modifier = Modifier.width(26.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textTertiary
                    )
                }
                for (col in 0 until 7) {
                    val cellDate = rowFirstDate.plusDays(col.toLong())
                    val isCurrentMonth = YearMonth.from(cellDate) == month
                    val isToday = cellDate == today
                    val isSelected = cellDate == selectedDate
                    val dayOccs = eventsByDate[cellDate].orEmpty()

                    MonthDayCell(
                        date = cellDate,
                        isCurrentMonth = isCurrentMonth,
                        isToday = isToday,
                        isSelected = isSelected,
                        occurrences = dayOccs,
                        onClick = { onSelectDate(cellDate) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    occurrences: List<EventOccurrence>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current

    val pillColor by animateColorAsState(
        targetValue = when {
            isToday -> palette.accent
            isSelected -> palette.accentSoft
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "day_pill_color"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "day_scale"
    )

    val textColor = when {
        isToday -> Color.White
        isSelected -> palette.accent
        isCurrentMonth -> palette.textPrimary
        else -> palette.textTertiary.copy(alpha = 0.65f)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .bounceClick(scaleTarget = 0.90f, haptic = true, onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(pillColor)
                .then(
                    if (isSelected && !isToday) {
                        Modifier.border(1.2.dp, palette.accent.copy(alpha = 0.7f), CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier.height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            occurrences.take(3).forEach { occ ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(occ.displayColorArgb))
                )
            }
        }
    }
}

@Composable
fun MonthAgendaItemCard(
    occurrence: EventOccurrence,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN) }

    val startZdt = Instant.ofEpochMilli(occurrence.occurrenceStartMillis).atZone(zone)
    val endZdt = Instant.ofEpochMilli(occurrence.occurrenceEndMillis).atZone(zone)
    val timeText = if (occurrence.event.allDay) {
        "Ganztägig"
    } else {
        "${startZdt.format(timeFmt)} – ${endZdt.format(timeFmt)}"
    }

    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        elevated = false,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(occurrence.displayColorArgb))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = occurrence.event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    if (occurrence.event.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Place,
                                contentDescription = null,
                                tint = palette.textTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = occurrence.event.location,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (occurrence.isRecurringInstance) {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = "Wiederkehrend",
                            tint = palette.textTertiary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(occurrence.displayColorArgb).copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = occurrence.calendarName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(occurrence.displayColorArgb),
                    maxLines = 1
                )
            }
        }
    }
}

private fun shortDowDe(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "Mo"
    DayOfWeek.TUESDAY -> "Di"
    DayOfWeek.WEDNESDAY -> "Mi"
    DayOfWeek.THURSDAY -> "Do"
    DayOfWeek.FRIDAY -> "Fr"
    DayOfWeek.SATURDAY -> "Sa"
    DayOfWeek.SUNDAY -> "So"
}
