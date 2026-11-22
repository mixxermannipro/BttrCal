package com.bttrcal.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.data.sync.SyncScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val app = BttrCalApplication.from(context)
            ReminderScheduler.rescheduleUpcoming(context, app.database)
            val settings = app.preferences.settings.value
            SyncScheduler.schedulePeriodicSync(context, settings.syncInterval, settings.wifiOnlySync)
        }
    }
}
