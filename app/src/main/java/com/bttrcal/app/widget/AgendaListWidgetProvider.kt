package com.bttrcal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.MainActivity
import com.bttrcal.app.R
import com.bttrcal.app.data.sync.SyncScheduler

class AgendaListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = BttrCalApplication.from(context)
        val style = app.preferences.settings.value.widgetStyle
        val isDark = WidgetUpdater.isDarkWidget(style)
        val primaryText = if (isDark) Color.parseColor("#F2F4F8") else Color.parseColor("#101828")
        val mutedText = if (isDark) Color.parseColor("#98A2B3") else Color.parseColor("#667085")

        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_agenda_list)
            views.setInt(R.id.agenda_widget_root, "setBackgroundResource", WidgetUpdater.resolveBackgroundRes(style))
            views.setTextColor(R.id.agenda_widget_title, primaryText)
            views.setTextColor(R.id.agenda_empty_view, mutedText)

            val serviceIntent = Intent(context, AgendaListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.agenda_list_view, serviceIntent)
            views.setEmptyView(R.id.agenda_list_view, R.id.agenda_empty_view)

            val itemTemplateIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val itemPending = PendingIntent.getActivity(
                context,
                5000,
                itemTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.agenda_list_view, itemPending)

            val addIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_QUICK_ADD, true)
            }
            views.setOnClickPendingIntent(
                R.id.agenda_btn_add,
                PendingIntent.getActivity(context, 5001, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val syncIntent = Intent(context, AgendaListWidgetProvider::class.java).apply {
                action = ACTION_SYNC_NOW
            }
            views.setOnClickPendingIntent(
                R.id.agenda_btn_sync,
                PendingIntent.getBroadcast(context, 5002, syncIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SYNC_NOW) {
            SyncScheduler.triggerImmediateBackgroundSync(context)
            WidgetUpdater.updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_SYNC_NOW = "com.bttrcal.app.widget.ACTION_SYNC_NOW"
    }
}
