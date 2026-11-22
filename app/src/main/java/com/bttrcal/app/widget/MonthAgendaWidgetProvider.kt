package com.bttrcal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.MainActivity
import com.bttrcal.app.R
import com.bttrcal.app.domain.model.EventOccurrence
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class MonthAgendaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV_MONTH -> {
                adjustMonthOffset(context, -1)
                refreshAll(context)
            }
            ACTION_NEXT_MONTH -> {
                adjustMonthOffset(context, +1)
                refreshAll(context)
            }
            ACTION_RESET_TODAY -> {
                setMonthOffset(context, 0)
                refreshAll(context)
            }
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    private fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, MonthAgendaWidgetProvider::class.java))
        ids.forEach { updateWidget(context, mgr, it) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val app = BttrCalApplication.from(context)
        val style = app.preferences.settings.value.widgetStyle
        val isDark = WidgetUpdater.isDarkWidget(style)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val monthOffset = getMonthOffset(context)
        val targetYm = YearMonth.from(today).plusMonths(monthOffset.toLong())

        val firstOfMonth = targetYm.atDay(1)
        val firstDow = style.firstDayOfWeek
        val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(firstDow))
        val gridEnd = gridStart.plusDays(42)

        val rangeStartMillis = gridStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEndMillis = gridEnd.atStartOfDay(zone).toInstant().toEpochMilli()
        val gridOccurrences = app.repository.getOccurrencesInRange(rangeStartMillis, rangeEndMillis, onlyVisible = true, zoneId = zone)

        val occsByDate = mutableMapOf<LocalDate, MutableList<EventOccurrence>>()
        for (occ in gridOccurrences) {
            var d = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).toLocalDate()
            val endD = Instant.ofEpochMilli((occ.occurrenceEndMillis - 1L).coerceAtLeast(occ.occurrenceStartMillis)).atZone(zone).toLocalDate()
            while (!d.isAfter(endD)) {
                if (!d.isBefore(gridStart) && d.isBefore(gridEnd)) {
                    occsByDate.getOrPut(d) { mutableListOf() }.add(occ)
                }
                d = d.plusDays(1)
            }
        }

        val views = RemoteViews(context.packageName, R.layout.widget_month_agenda)
        views.setInt(R.id.widget_root, "setBackgroundResource", WidgetUpdater.resolveBackgroundRes(style))

        val primaryText = if (isDark) Color.parseColor("#F2F4F8") else Color.parseColor("#101828")
        val mutedText = if (isDark) Color.parseColor("#98A2B3") else Color.parseColor("#667085")
        val outMonthText = if (isDark) Color.parseColor("#475467") else Color.parseColor("#B0B7C3")
        val accentBlue = Color.parseColor("#0A84FF")

        val monthTitle = targetYm.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
            .replaceFirstChar { it.titlecase(Locale.GERMAN) }
        views.setTextViewText(R.id.widget_month_title, monthTitle)
        views.setTextColor(R.id.widget_month_title, primaryText)
        views.setTextColor(R.id.widget_upcoming_label, mutedText)

        // Weekday headers aligned with firstDayOfWeek
        val wdIds = intArrayOf(R.id.wd_0, R.id.wd_1, R.id.wd_2, R.id.wd_3, R.id.wd_4, R.id.wd_5, R.id.wd_6)
        for (i in 0 until 7) {
            val dow = firstDow.plus(i.toLong())
            views.setTextViewText(wdIds[i], shortDowDe(dow))
            val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            views.setTextColor(wdIds[i], if (isWeekend) mutedText else primaryText)
        }

        // Fill 42 cells
        for (i in 0 until 42) {
            val cellDate = gridStart.plusDays(i.toLong())
            val dayId = DAY_IDS[i]
            val dotId = DOT_IDS[i]
            val isCurrentMonth = YearMonth.from(cellDate) == targetYm
            val isToday = cellDate == today
            val dayEvents = occsByDate[cellDate].orEmpty()

            views.setTextViewText(dayId, cellDate.dayOfMonth.toString())
            if (isToday) {
                views.setInt(dayId, "setBackgroundResource", R.drawable.widget_today_circle)
                views.setTextColor(dayId, Color.WHITE)
            } else {
                views.setInt(dayId, "setBackgroundResource", 0)
                views.setTextColor(dayId, if (isCurrentMonth) primaryText else outMonthText)
            }

            if (dayEvents.isNotEmpty()) {
                val dots = "•".repeat(dayEvents.size.coerceAtMost(3))
                views.setTextViewText(dotId, dots)
                views.setTextColor(dotId, dayEvents.first().displayColorArgb)
                views.setViewVisibility(dotId, View.VISIBLE)
            } else {
                views.setTextViewText(dotId, "")
                views.setViewVisibility(dotId, View.INVISIBLE)
            }

            val dayMillis = cellDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val openDayIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, dayMillis)
            }
            val openDayPending = PendingIntent.getActivity(
                context,
                1000 + i,
                openDayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(dayId, openDayPending)
        }

        // Next 3 upcoming events from now
        val now = System.currentTimeMillis()
        val upcoming = app.repository.getOccurrencesInRange(
            now,
            now + 30L * 86_400_000L,
            onlyVisible = true,
            zoneId = zone
        ).take(3)

        val rowIds = intArrayOf(R.id.widget_ev_row_0, R.id.widget_ev_row_1, R.id.widget_ev_row_2)
        val barIds = intArrayOf(R.id.widget_ev_bar_0, R.id.widget_ev_bar_1, R.id.widget_ev_bar_2)
        val titleIds = intArrayOf(R.id.widget_ev_title_0, R.id.widget_ev_title_1, R.id.widget_ev_title_2)
        val timeIds = intArrayOf(R.id.widget_ev_time_0, R.id.widget_ev_time_1, R.id.widget_ev_time_2)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        val dayTimeFmt = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMAN)

        if (upcoming.isEmpty()) {
            views.setViewVisibility(rowIds[0], View.VISIBLE)
            views.setInt(barIds[0], "setBackgroundColor", accentBlue)
            views.setTextViewText(titleIds[0], "Keine anstehenden Termine")
            views.setTextColor(titleIds[0], mutedText)
            views.setTextViewText(timeIds[0], "")
            views.setViewVisibility(rowIds[1], View.GONE)
            views.setViewVisibility(rowIds[2], View.GONE)
        } else {
            for (idx in 0 until 3) {
                val occ = upcoming.getOrNull(idx)
                if (occ == null) {
                    views.setViewVisibility(rowIds[idx], View.GONE)
                } else {
                    views.setViewVisibility(rowIds[idx], View.VISIBLE)
                    views.setInt(barIds[idx], "setBackgroundColor", occ.displayColorArgb)
                    views.setTextViewText(titleIds[idx], occ.event.title)
                    views.setTextColor(titleIds[idx], primaryText)
                    val zdt = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone)
                    val tLabel = when {
                        occ.event.allDay && zdt.toLocalDate() == today -> "Heute"
                        occ.event.allDay -> "${zdt.dayOfMonth}.${zdt.monthValue}."
                        zdt.toLocalDate() == today -> zdt.format(timeFmt)
                        else -> zdt.format(dayTimeFmt)
                    }
                    views.setTextViewText(timeIds[idx], tLabel)
                    views.setTextColor(timeIds[idx], mutedText)

                    val evIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, occ.event.id)
                        putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, occ.occurrenceStartMillis)
                    }
                    val evPending = PendingIntent.getActivity(
                        context,
                        2000 + idx,
                        evIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(rowIds[idx], evPending)
                }
            }
        }

        // Prev / Next / Today / Quick-add buttons
        views.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            broadcastPending(context, ACTION_PREV_MONTH, 301)
        )
        views.setOnClickPendingIntent(
            R.id.widget_btn_next,
            broadcastPending(context, ACTION_NEXT_MONTH, 302)
        )
        views.setOnClickPendingIntent(
            R.id.widget_month_title,
            broadcastPending(context, ACTION_RESET_TODAY, 303)
        )
        val addIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        }
        views.setOnClickPendingIntent(
            R.id.widget_btn_add,
            PendingIntent.getActivity(context, 304, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        manager.updateAppWidget(widgetId, views)
    }

    private fun broadcastPending(context: Context, action: String, reqCode: Int): PendingIntent {
        val intent = Intent(context, MonthAgendaWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getMonthOffset(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_MONTH_OFFSET, 0)

    private fun setMonthOffset(context: Context, offset: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_MONTH_OFFSET, offset).apply()
    }

    private fun adjustMonthOffset(context: Context, delta: Int) {
        setMonthOffset(context, getMonthOffset(context) + delta)
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

    companion object {
        const val ACTION_PREV_MONTH = "com.bttrcal.app.widget.ACTION_PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.bttrcal.app.widget.ACTION_NEXT_MONTH"
        const val ACTION_RESET_TODAY = "com.bttrcal.app.widget.ACTION_RESET_TODAY"
        const val ACTION_REFRESH = "com.bttrcal.app.widget.ACTION_REFRESH"
        private const val PREFS_NAME = "bttrcal_month_widget_prefs"
        private const val KEY_MONTH_OFFSET = "month_offset"

        private val DAY_IDS = intArrayOf(
            R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6,
            R.id.day_7, R.id.day_8, R.id.day_9, R.id.day_10, R.id.day_11, R.id.day_12, R.id.day_13,
            R.id.day_14, R.id.day_15, R.id.day_16, R.id.day_17, R.id.day_18, R.id.day_19, R.id.day_20,
            R.id.day_21, R.id.day_22, R.id.day_23, R.id.day_24, R.id.day_25, R.id.day_26, R.id.day_27,
            R.id.day_28, R.id.day_29, R.id.day_30, R.id.day_31, R.id.day_32, R.id.day_33, R.id.day_34,
            R.id.day_35, R.id.day_36, R.id.day_37, R.id.day_38, R.id.day_39, R.id.day_40, R.id.day_41
        )
        private val DOT_IDS = intArrayOf(
            R.id.dot_0, R.id.dot_1, R.id.dot_2, R.id.dot_3, R.id.dot_4, R.id.dot_5, R.id.dot_6,
            R.id.dot_7, R.id.dot_8, R.id.dot_9, R.id.dot_10, R.id.dot_11, R.id.dot_12, R.id.dot_13,
            R.id.dot_14, R.id.dot_15, R.id.dot_16, R.id.dot_17, R.id.dot_18, R.id.dot_19, R.id.dot_20,
            R.id.dot_21, R.id.dot_22, R.id.dot_23, R.id.dot_24, R.id.dot_25, R.id.dot_26, R.id.dot_27,
            R.id.dot_28, R.id.dot_29, R.id.dot_30, R.id.dot_31, R.id.dot_32, R.id.dot_33, R.id.dot_34,
            R.id.dot_35, R.id.dot_36, R.id.dot_37, R.id.dot_38, R.id.dot_39, R.id.dot_40, R.id.dot_41
        )
    }
}
