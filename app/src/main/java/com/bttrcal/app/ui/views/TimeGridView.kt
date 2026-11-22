package com.bttrcal.app.ui.views

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bttrcal.app.domain.engine.TimeGridLayoutEngine
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.ui.theme.LiquidGlassSurface
import com.bttrcal.app.ui.theme.LocalBttrPalette
import com.bttrcal.app.ui.theme.bounceClick
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private const val GRID_BASE_PAGE = 500
private val HOUR_HEIGHT = 64.dp
private val TIME_AXIS_WIDTH = 48.dp

@Composable
fun TimeGridView(
    daysCount: Int,
    selectedDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    dataRevision: Long,
    loadOccurrences: (Long, Long) -> List<EventOccurrence>,
    onSelectDate: (LocalDate) -> Unit,
    onEventClick: (EventOccurrence) -> Unit,
    onCreateAtSlot: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalBttrPalette.current
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)

    val anchorBaseDate = remember(daysCount, firstDayOfWeek) {
        if (daysCount == 7) LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        else LocalDate.now(zone)
    }

    val targetAnchor = remember(selectedDate, daysCount, firstDayOfWeek) {
        if (daysCount == 7) selectedDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        else selectedDate
    }

    val targetPage = remember(targetAnchor, anchorBaseDate, daysCount) {
        val diffDays = ChronoUnit.DAYS.between(anchorBaseDate, targetAnchor)
        val offset = (diffDays / daysCount.toLong()).toInt()
        (GRID_BASE_PAGE + offset).coerceIn(0, GRID_BASE_PAGE * 2)
    }

    val pagerState = rememberPagerState(
        initialPage = targetPage,
        pageCount = { GRID_BASE_PAGE * 2 + 1 }
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
        val pageStart = anchorBaseDate.plusDays((pagerState.settledPage - GRID_BASE_PAGE) * daysCount.toLong())
        val pageEnd = pageStart.plusDays(daysCount.toLong())
        if (selectedDate.isBefore(pageStart) || !selectedDate.isBefore(pageEnd)) {
            onSelectDate(pageStart)
        }
    }

    val verticalScroll = rememberScrollState(initial = 450) // Start comfortably near 07:30

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 92.dp),
        cornerRadius = 26.dp,
        elevated = true,
        padding = PaddingValues(top = 10.dp, bottom = 6.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val startDate = anchorBaseDate.plusDays((page - GRID_BASE_PAGE) * daysCount.toLong())
            val dates = (0 until daysCount).map { startDate.plusDays(it.toLong()) }
            val rangeStartMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val rangeEndMillis = startDate.plusDays(daysCount.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

            val occs = remember(startDate, daysCount, dataRevision) {
                loadOccurrences(rangeStartMillis, rangeEndMillis)
            }
            val allDayEvents = remember(occs) { occs.filter { it.event.allDay } }
            val timedEvents = remember(occs) { occs.filter { !it.event.allDay } }

            Column(modifier = Modifier.fillMaxSize()) {
                // Day headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(TIME_AXIS_WIDTH))
                    dates.forEach { d ->
                        val isToday = d == today
                        val isSel = d == selectedDate
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .bounceClick(onClick = { onSelectDate(d) })
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = shortWeekday(d.dayOfWeek),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) Color(0xFFFF453A) else palette.textSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isToday -> Color(0xFFFF453A)
                                            isSel -> palette.accentSoft
                                            else -> Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = d.dayOfMonth.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isToday -> Color.White
                                        isSel -> palette.accent
                                        else -> palette.textPrimary
                                    }
                                )
                            }
                        }
                    }
                }

                // All-day strip
                if (allDayEvents.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tag",
                            modifier = Modifier.width(TIME_AXIS_WIDTH - 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textTertiary
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            allDayEvents.take(3).forEach { occ ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(occ.displayColorArgb).copy(alpha = 0.22f))
                                        .border(
                                            0.8.dp,
                                            Color(occ.displayColorArgb).copy(alpha = 0.7f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onEventClick(occ) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = occ.event.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.gridLine, thickness = 0.7.dp)

                // Scrollable 24-hour time grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(verticalScroll)
                ) {
                    // Hour labels column
                    Column(modifier = Modifier.width(TIME_AXIS_WIDTH)) {
                        for (h in 0 until 24) {
                            Box(
                                modifier = Modifier
                                    .height(HOUR_HEIGHT)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Text(
                                    text = String.format(Locale.GERMAN, "%02d:00", h),
                                    fontSize = 10.sp,
                                    color = palette.textTertiary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Day columns
                    Row(modifier = Modifier.weight(1f)) {
                        dates.forEach { dayDate ->
                            val dStart = dayDate.atStartOfDay(zone).toInstant().toEpochMilli()
                            val dEnd = dayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                            val dayTimed = remember(timedEvents, dayDate) {
                                timedEvents
                                    .filter { it.occurrenceEndMillis > dStart && it.occurrenceStartMillis < dEnd }
                                    .map { occ ->
                                        occ.copy(
                                            occurrenceStartMillis = maxOf(occ.occurrenceStartMillis, dStart),
                                            occurrenceEndMillis = minOf(occ.occurrenceEndMillis, dEnd)
                                        )
                                    }
                            }
                            val placed = remember(dayTimed) { TimeGridLayoutEngine.layoutDayEvents(dayTimed) }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(HOUR_HEIGHT * 24)
                            ) {
                                val colWidth = maxWidth

                                // Background hourly clickable slots
                                Column(modifier = Modifier.fillMaxSize()) {
                                    for (h in 0 until 24) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(HOUR_HEIGHT)
                                                .clickable { onCreateAtSlot(dayDate, h) }
                                        ) {
                                            HorizontalDivider(
                                                color = palette.gridLine,
                                                thickness = 0.6.dp
                                            )
                                        }
                                    }
                                }

                                // Left vertical hairline divider
                                Box(
                                    modifier = Modifier
                                        .width(0.6.dp)
                                        .height(HOUR_HEIGHT * 24)
                                        .background(palette.gridLine)
                                )

                                // Current time line
                                if (dayDate == today) {
                                    val nowTime = LocalTime.now(zone)
                                    val nowMinutes = nowTime.hour * 60 + nowTime.minute
                                    val yOffset = HOUR_HEIGHT * (nowMinutes / 60f)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .offset(y = yOffset - 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(palette.danger)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(1.8.dp)
                                                .background(palette.danger)
                                        )
                                    }
                                }

                                // Placed event blocks
                                placed.forEach { item ->
                                    val occ = item.occurrence
                                    val startMin = ((occ.occurrenceStartMillis - dStart) / 60_000f).coerceIn(0f, 1435f)
                                    val durMin = ((occ.occurrenceEndMillis - occ.occurrenceStartMillis) / 60_000f)
                                        .coerceAtLeast(28f)
                                        .coerceAtMost(1440f - startMin)

                                    val topDp = HOUR_HEIGHT * (startMin / 60f)
                                    val heightDp = HOUR_HEIGHT * (durMin / 60f)
                                    val sliceW = colWidth / item.totalColumns.toFloat()
                                    val leftDp = sliceW * item.column.toFloat()
                                    val accentColor = Color(occ.displayColorArgb)

                                    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
                                    val startStr = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).format(timeFmt)

                                    Box(
                                        modifier = Modifier
                                            .offset(x = leftDp + 1.5.dp, y = topDp + 1.dp)
                                            .width((sliceW - 3.dp).coerceAtLeast(18.dp))
                                            .height((heightDp - 2.dp).coerceAtLeast(22.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(accentColor.copy(alpha = if (palette.isDark) 0.28f else 0.18f))
                                            .border(1.dp, accentColor.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                                            .clickable { onEventClick(occ) }
                                            .padding(horizontal = 5.dp, vertical = 3.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = occ.event.title,
                                                fontSize = if (daysCount >= 7) 10.sp else 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = palette.textPrimary,
                                                maxLines = if (durMin >= 55f) 2 else 1,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = if (daysCount >= 7) 12.sp else 14.sp
                                            )
                                            if (durMin >= 45f && daysCount <= 3) {
                                                Text(
                                                    text = startStr,
                                                    fontSize = 10.sp,
                                                    color = palette.textSecondary,
                                                    maxLines = 1
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
        }
    }
}

private fun shortWeekday(dow: DayOfWeek): String = when (dow) {
    DayOfWeek.MONDAY -> "MO"
    DayOfWeek.TUESDAY -> "DI"
    DayOfWeek.WEDNESDAY -> "MI"
    DayOfWeek.THURSDAY -> "DO"
    DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"
    DayOfWeek.SUNDAY -> "SO"
}
