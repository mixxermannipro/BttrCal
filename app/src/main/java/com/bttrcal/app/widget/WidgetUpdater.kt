package com.bttrcal.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.bttrcal.app.R
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.ThemeMode
import com.bttrcal.app.domain.model.WidgetStyleConfig

object WidgetUpdater {

    fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)

        val monthIds = manager.getAppWidgetIds(ComponentName(context, MonthAgendaWidgetProvider::class.java))
        if (monthIds.isNotEmpty()) {
            val intent = Intent(context, MonthAgendaWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, monthIds)
            }
            context.sendBroadcast(intent)
        }

        val todayIds = manager.getAppWidgetIds(ComponentName(context, TodayCardWidgetProvider::class.java))
        if (todayIds.isNotEmpty()) {
            val intent = Intent(context, TodayCardWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, todayIds)
            }
            context.sendBroadcast(intent)
        }

        val agendaIds = manager.getAppWidgetIds(ComponentName(context, AgendaListWidgetProvider::class.java))
        if (agendaIds.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(agendaIds, R.id.agenda_list_view)
            val intent = Intent(context, AgendaListWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, agendaIds)
            }
            context.sendBroadcast(intent)
        }
    }

    fun resolveBackgroundRes(style: WidgetStyleConfig): Int = when (style.themeMode) {
        ThemeMode.LIGHT -> R.drawable.widget_bg_light
        ThemeMode.DARK -> R.drawable.widget_bg_dark
        ThemeMode.LIQUID_GLASS -> if (style.glassVariant == GlassVariant.DARK_GLASS) {
            R.drawable.widget_bg_liquid_glass_dark
        } else {
            R.drawable.widget_bg_liquid_glass_light
        }
    }

    fun isDarkWidget(style: WidgetStyleConfig): Boolean = when (style.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.LIQUID_GLASS -> style.glassVariant == GlassVariant.DARK_GLASS
    }
}
