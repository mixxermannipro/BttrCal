package com.bttrcal.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.MainActivity
import com.bttrcal.app.R
import com.bttrcal.app.domain.model.EventOccurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AgendaListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        AgendaRemoteViewsFactory(applicationContext)
}

private class AgendaRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<EventOccurrence> = emptyList()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMAN)

    override fun onCreate() {
        loadItems()
    }

    override fun onDataSetChanged() {
        loadItems()
    }

    private fun loadItems() {
        val app = BttrCalApplication.from(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        items = app.repository.getOccurrencesInRange(
            rangeStartMillis = todayStart,
            rangeEndMillis = now + 30L * 86_400_000L,
            onlyVisible = true,
            zoneId = zone
        ).take(40)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val occ = items.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_agenda_row)
        val app = BttrCalApplication.from(context)
        val isDark = WidgetUpdater.isDarkWidget(app.preferences.settings.value.widgetStyle)
        val primaryText = if (isDark) Color.parseColor("#F2F4F8") else Color.parseColor("#101828")
        val mutedText = if (isDark) Color.parseColor("#98A2B3") else Color.parseColor("#667085")

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val zdt = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone)
        val endZdt = Instant.ofEpochMilli(occ.occurrenceEndMillis).atZone(zone)
        val dayPrefix = when (zdt.toLocalDate()) {
            today -> "Heute"
            today.plusDays(1) -> "Morgen"
            else -> zdt.format(dateFmt)
        }
        val timePart = if (occ.event.allDay) {
            "Ganztägig"
        } else {
            "${zdt.format(timeFmt)}–${endZdt.format(timeFmt)}"
        }
        val subtitle = buildString {
            append("$dayPrefix · $timePart")
            if (occ.event.location.isNotBlank()) append(" · ${occ.event.location}")
        }

        val rv = RemoteViews(context.packageName, R.layout.widget_agenda_row)
        rv.setInt(R.id.agenda_row_bar, "setBackgroundColor", occ.displayColorArgb)
        rv.setTextViewText(R.id.agenda_row_title, occ.event.title)
        rv.setTextColor(R.id.agenda_row_title, primaryText)
        rv.setTextViewText(R.id.agenda_row_subtitle, subtitle)
        rv.setTextColor(R.id.agenda_row_subtitle, mutedText)

        val fillIn = Intent().apply {
            putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, occ.event.id)
            putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, occ.occurrenceStartMillis)
        }
        rv.setOnClickFillInIntent(R.id.agenda_row_root, fillIn)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
