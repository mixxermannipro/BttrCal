package com.bttrcal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.MainActivity
import com.bttrcal.app.R
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class SplitMonthWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val app = BttrCalApplication.from(context)
        val style = app.preferences.settings.value.widgetStyle
        val isDark = WidgetUpdater.isDarkWidget(style)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val ym = YearMonth.from(today)

        val gridStart = ym.atDay(1).with(TemporalAdjusters.previousOrSame(style.firstDayOfWeek))
        val gridEnd = gridStart.plusDays(42)

        val rangeStartMillis = gridStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEndMillis = gridEnd.atStartOfDay(zone).toInstant().toEpochMilli()
        val gridOccs = app.repository.getOccurrencesInRange(rangeStartMillis, rangeEndMillis, onlyVisible = true, zoneId = zone)

        val occsByDate = HashMap<LocalDate, MutableList<EventOccurrence>>()
        for (occ in gridOccs) {
            var d = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).toLocalDate()
            val endD = Instant.ofEpochMilli((occ.occurrenceEndMillis - 1L).coerceAtLeast(occ.occurrenceStartMillis))
                .atZone(zone).toLocalDate()
            while (!d.isAfter(endD)) {
                if (!d.isBefore(gridStart) && d.isBefore(gridEnd)) {
                    occsByDate.getOrPut(d) { mutableListOf() }.add(occ)
                }
                d = d.plusDays(1)
            }
        }

        val views = RemoteViews(context.packageName, R.layout.widget_split_month)

        val bgRes = when (style.themeMode) {
            ThemeMode.LIGHT -> R.drawable.widget_bg_light
            ThemeMode.DARK -> R.drawable.widget_bg_dark
            ThemeMode.LIQUID_GLASS -> if (style.glassVariant == GlassVariant.DARK_GLASS) {
                R.drawable.widget_bg_liquid_glass_clear
            } else {
                R.drawable.widget_bg_liquid_glass_light
            }
        }
        val cardRes = when (style.themeMode) {
            ThemeMode.LIGHT -> R.drawable.widget_event_card_light
            ThemeMode.DARK -> R.drawable.widget_event_card_dark
            ThemeMode.LIQUID_GLASS -> if (style.glassVariant == GlassVariant.DARK_GLASS) {
                R.drawable.widget_event_card_glass_dark
            } else {
                R.drawable.widget_event_card_glass_light
            }
        }

        views.setInt(R.id.split_widget_root, "setBackgroundResource", bgRes)

        val primaryText = if (isDark) Color.WHITE else Color.parseColor("#0F172A")
        val mutedText = if (isDark) Color.parseColor("#CCFFFFFF") else Color.parseColor("#526075")
        val outMonthText = if (isDark) Color.parseColor("#7AFFFFFF") else Color.parseColor("#A0AAB8")

        val dateHeader = today.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN))
        val dayShort = shortDow2De(today.dayOfWeek)
        views.setTextViewText(R.id.split_date_label, dateHeader)
        views.setTextViewText(R.id.split_day_label, dayShort)
        views.setTextColor(R.id.split_date_label, primaryText)
        views.setTextColor(R.id.split_day_label, mutedText)
        views.setTextColor(R.id.split_upcoming_label, mutedText)

        val wdIds = intArrayOf(R.id.swd_0, R.id.swd_1, R.id.swd_2, R.id.swd_3, R.id.swd_4, R.id.swd_5, R.id.swd_6)
        for (i in 0 until 7) {
            val dow = style.firstDayOfWeek.plus(i.toLong())
            views.setTextViewText(wdIds[i], singleDowDe(dow))
            val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            views.setTextColor(wdIds[i], if (isWeekend) outMonthText else mutedText)
        }

        for (i in 0 until 42) {
            val cellDate = gridStart.plusDays(i.toLong())
            val dayId = DAY_IDS[i]
            val dotId = DOT_IDS[i]
            val inMonth = YearMonth.from(cellDate) == ym
            val isToday = cellDate == today
            val dayEvents = occsByDate[cellDate].orEmpty()

            views.setTextViewText(dayId, cellDate.dayOfMonth.toString())
            if (isToday) {
                views.setInt(dayId, "setBackgroundResource", R.drawable.widget_today_coral)
                views.setTextColor(dayId, Color.WHITE)
            } else {
                views.setInt(dayId, "setBackgroundResource", 0)
                views.setTextColor(dayId, if (inMonth) primaryText else outMonthText)
            }

            if (dayEvents.isNotEmpty()) {
                views.setTextViewText(dotId, "•")
                views.setTextColor(
                    dotId,
                    if (isToday && isDark) Color.parseColor("#E6FFFFFF") else dayEvents.first().displayColorArgb
                )
                views.setViewVisibility(dotId, View.VISIBLE)
            } else {
                views.setTextViewText(dotId, "")
                views.setViewVisibility(dotId, View.INVISIBLE)
            }

            val dayMillis = cellDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val cellIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, dayMillis)
            }
            views.setOnClickPendingIntent(
                dayId,
                PendingIntent.getActivity(
                    context,
                    6100 + i,
                    cellIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        // Open calendar on tap of left column header / background
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val openTodayIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, todayStart)
        }
        views.setOnClickPendingIntent(
            R.id.split_left_col,
            PendingIntent.getActivity(context, 6000, openTodayIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        val quickAddIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        }
        views.setOnClickPendingIntent(
            R.id.split_btn_add,
            PendingIntent.getActivity(context, 6001, quickAddIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        // Upcoming 3 active/future events (filter out timed events from earlier today that already ended + deduplicate same-day titles)
        val now = System.currentTimeMillis()
        val upcoming = app.repository.getOccurrencesInRange(
            rangeStartMillis = todayStart,
            rangeEndMillis = todayStart + 14L * 86_400_000L,
            onlyVisible = true,
            zoneId = zone
        )
            .filter { occ -> occ.event.allDay || occ.occurrenceEndMillis > now }
            .distinctBy { occ ->
                val d = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).toLocalDate()
                "${d}_${occ.event.title.trim().lowercase(Locale.GERMAN)}"
            }
            .take(3)

        val cardIds = intArrayOf(R.id.sev_card_0, R.id.sev_card_1, R.id.sev_card_2)
        val barIds = intArrayOf(R.id.sev_bar_0, R.id.sev_bar_1, R.id.sev_bar_2)
        val titleIds = intArrayOf(R.id.sev_title_0, R.id.sev_title_1, R.id.sev_title_2)
        val timeIds = intArrayOf(R.id.sev_time_0, R.id.sev_time_1, R.id.sev_time_2)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        val dayFmt = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)

        if (upcoming.isEmpty()) {
            views.setViewVisibility(cardIds[0], View.VISIBLE)
            views.setInt(cardIds[0], "setBackgroundResource", cardRes)
            views.setInt(barIds[0], "setBackgroundColor", Color.parseColor("#34C759"))
            views.setTextViewText(titleIds[0], "Heute frei")
            views.setTextColor(titleIds[0], primaryText)
            views.setTextViewText(timeIds[0], "Tippen zum Planen")
            views.setTextColor(timeIds[0], mutedText)

            val addIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_QUICK_ADD, true)
            }
            views.setOnClickPendingIntent(
                cardIds[0],
                PendingIntent.getActivity(context, 6010, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            views.setViewVisibility(cardIds[1], View.INVISIBLE)
            views.setViewVisibility(cardIds[2], View.INVISIBLE)
        } else {
            for (idx in 0 until 3) {
                val occ = upcoming.getOrNull(idx)
                if (occ == null) {
                    views.setViewVisibility(cardIds[idx], View.INVISIBLE)
                } else {
                    views.setViewVisibility(cardIds[idx], View.VISIBLE)
                    views.setInt(cardIds[idx], "setBackgroundResource", cardRes)
                    views.setInt(barIds[idx], "setBackgroundColor", occ.displayColorArgb)
                    views.setTextViewText(titleIds[idx], occ.event.title)
                    views.setTextColor(titleIds[idx], primaryText)

                    val startZdt = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone)
                    val endZdt = Instant.ofEpochMilli(occ.occurrenceEndMillis).atZone(zone)
                    val isToday = startZdt.toLocalDate() == today
                    val timeLabel = when {
                        occ.event.allDay && isToday -> "Ganztägig"
                        occ.event.allDay -> "${startZdt.format(dayFmt)} · Ganztägig"
                        isToday -> "${startZdt.format(timeFmt)}–${endZdt.format(timeFmt)}"
                        else -> "${startZdt.format(dayFmt)} ${startZdt.format(timeFmt)}–${endZdt.format(timeFmt)}"
                    }
                    views.setTextViewText(timeIds[idx], timeLabel)
                    views.setTextColor(timeIds[idx], mutedText)

                    val evIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, occ.event.id)
                        putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, occ.occurrenceStartMillis)
                    }
                    views.setOnClickPendingIntent(
                        cardIds[idx],
                        PendingIntent.getActivity(context, 6020 + idx, evIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )
                }
            }
        }

        manager.updateAppWidget(widgetId, views)
    }

    private fun singleDowDe(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "M"
        DayOfWeek.TUESDAY -> "D"
        DayOfWeek.WEDNESDAY -> "M"
        DayOfWeek.THURSDAY -> "D"
        DayOfWeek.FRIDAY -> "F"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "S"
    }

    private fun shortDow2De(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "Mo"
        DayOfWeek.TUESDAY -> "Di"
        DayOfWeek.WEDNESDAY -> "Mi"
        DayOfWeek.THURSDAY -> "Do"
        DayOfWeek.FRIDAY -> "Fr"
        DayOfWeek.SATURDAY -> "Sa"
        DayOfWeek.SUNDAY -> "So"
    }

    companion object {
        private val DAY_IDS = intArrayOf(
            R.id.sday_0, R.id.sday_1, R.id.sday_2, R.id.sday_3, R.id.sday_4, R.id.sday_5, R.id.sday_6,
            R.id.sday_7, R.id.sday_8, R.id.sday_9, R.id.sday_10, R.id.sday_11, R.id.sday_12, R.id.sday_13,
            R.id.sday_14, R.id.sday_15, R.id.sday_16, R.id.sday_17, R.id.sday_18, R.id.sday_19, R.id.sday_20,
            R.id.sday_21, R.id.sday_22, R.id.sday_23, R.id.sday_24, R.id.sday_25, R.id.sday_26, R.id.sday_27,
            R.id.sday_28, R.id.sday_29, R.id.sday_30, R.id.sday_31, R.id.sday_32, R.id.sday_33, R.id.sday_34,
            R.id.sday_35, R.id.sday_36, R.id.sday_37, R.id.sday_38, R.id.sday_39, R.id.sday_40, R.id.sday_41
        )
        private val DOT_IDS = intArrayOf(
            R.id.sdot_0, R.id.sdot_1, R.id.sdot_2, R.id.sdot_3, R.id.sdot_4, R.id.sdot_5, R.id.sdot_6,
            R.id.sdot_7, R.id.sdot_8, R.id.sdot_9, R.id.sdot_10, R.id.sdot_11, R.id.sdot_12, R.id.sdot_13,
            R.id.sdot_14, R.id.sdot_15, R.id.sdot_16, R.id.sdot_17, R.id.sdot_18, R.id.sdot_19, R.id.sdot_20,
            R.id.sdot_21, R.id.sdot_22, R.id.sdot_23, R.id.sdot_24, R.id.sdot_25, R.id.sdot_26, R.id.sdot_27,
            R.id.sdot_28, R.id.sdot_29, R.id.sdot_30, R.id.sdot_31, R.id.sdot_32, R.id.sdot_33, R.id.sdot_34,
            R.id.sdot_35, R.id.sdot_36, R.id.sdot_37, R.id.sdot_38, R.id.sdot_39, R.id.sdot_40, R.id.sdot_41
        )
    }
}
