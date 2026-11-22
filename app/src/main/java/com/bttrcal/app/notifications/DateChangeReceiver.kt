package com.bttrcal.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.widget.WidgetUpdater

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                WidgetUpdater.updateAllWidgets(context)
                val app = BttrCalApplication.from(context)
                ReminderScheduler.rescheduleUpcoming(context, app.database)
            }
        }
    }
}
