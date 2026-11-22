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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayCardWidgetProvider : AppWidgetProvider() {

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

        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayOccs = app.repository.getOccurrencesInRange(dayStart, dayEnd, onlyVisible = true, zoneId = zone).take(4)

        val views = RemoteViews(context.packageName, R.layout.widget_today_card)
        views.setInt(R.id.today_widget_root, "setBackgroundResource", WidgetUpdater.resolveBackgroundRes(style))

        val primaryText = if (isDark) Color.parseColor("#F2F4F8") else Color.parseColor("#101828")
        val mutedText = if (isDark) Color.parseColor("#98A2B3") else Color.parseColor("#667085")

        val weekdayStr = today.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)).uppercase(Locale.GERMAN)
        val monthYearStr = today.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN))
            .replaceFirstChar { it.titlecase(Locale.GERMAN) }

        views.setTextViewText(R.id.today_weekday, weekdayStr)
        views.setTextViewText(R.id.today_day_num, today.dayOfMonth.toString())
        views.setTextViewText(R.id.today_month_label, monthYearStr)
        views.setTextColor(R.id.today_day_num, primaryText)
        views.setTextColor(R.id.today_month_label, mutedText)
        views.setTextColor(R.id.today_header_label, mutedText)

        val rowIds = intArrayOf(R.id.today_row_0, R.id.today_row_1, R.id.today_row_2, R.id.today_row_3)
        val barIds = intArrayOf(R.id.today_bar_0, R.id.today_bar_1, R.id.today_bar_2, R.id.today_bar_3)
        val titleIds = intArrayOf(R.id.today_title_0, R.id.today_title_1, R.id.today_title_2, R.id.today_title_3)
        val timeIds = intArrayOf(R.id.today_time_0, R.id.today_time_1, R.id.today_time_2, R.id.today_time_3)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

        if (todayOccs.isEmpty()) {
            views.setViewVisibility(rowIds[0], View.VISIBLE)
            views.setInt(barIds[0], "setBackgroundColor", Color.parseColor("#34C759"))
            views.setTextViewText(titleIds[0], "Keine weiteren Termine heute")
            views.setTextColor(titleIds[0], mutedText)
            views.setTextViewText(timeIds[0], "")
            for (i in 1 until 4) views.setViewVisibility(rowIds[i], View.GONE)
        } else {
            for (i in 0 until 4) {
                val occ = todayOccs.getOrNull(i)
                if (occ == null) {
                    views.setViewVisibility(rowIds[i], View.GONE)
                } else {
                    views.setViewVisibility(rowIds[i], View.VISIBLE)
                    views.setInt(barIds[i], "setBackgroundColor", occ.displayColorArgb)
                    views.setTextViewText(titleIds[i], occ.event.title)
                    views.setTextColor(titleIds[i], primaryText)
                    val timeStr = if (occ.event.allDay) {
                        "Ganztägig"
                    } else {
                        Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone).format(timeFmt)
                    }
                    views.setTextViewText(timeIds[i], timeStr)
                    views.setTextColor(timeIds[i], mutedText)

                    val evIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, occ.event.id)
                        putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, occ.occurrenceStartMillis)
                    }
                    views.setOnClickPendingIntent(
                        rowIds[i],
                        PendingIntent.getActivity(
                            context,
                            4000 + i,
                            evIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, dayStart)
        }
        views.setOnClickPendingIntent(
            R.id.today_date_col,
            PendingIntent.getActivity(context, 4100, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        val addIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        }
        views.setOnClickPendingIntent(
            R.id.today_btn_add,
            PendingIntent.getActivity(context, 4101, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )

        manager.updateAppWidget(widgetId, views)
    }
}
