package com.bttrcal.app.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bttrcal.app.MainActivity
import com.bttrcal.app.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
                val snoozeMin = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Termin"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""
                val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
                val color = intent.getIntExtra(EXTRA_COLOR, 0xFF0A84FF.toInt())
                val occStart = intent.getLongExtra(EXTRA_OCC_START, 0L)

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)
                ReminderScheduler.scheduleSnooze(
                    context = context,
                    minutes = snoozeMin,
                    eventId = eventId,
                    title = title,
                    subtitle = subtitle,
                    location = location,
                    colorArgb = color,
                    occStart = occStart
                )
            }
            ACTION_TRIGGER_REMINDER, null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) return
                }

                ReminderScheduler.ensureChannel(context)
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Termin"
                val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""
                val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
                val color = intent.getIntExtra(EXTRA_COLOR, 0xFF0A84FF.toInt())
                val occStart = intent.getLongExtra(EXTRA_OCC_START, 0L)
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, eventId.hashCode())

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, eventId)
                    putExtra(MainActivity.EXTRA_OPEN_DATE_MILLIS, occStart)
                }
                val openPending = PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                fun snoozePending(minutes: Int, codeOffset: Int): PendingIntent {
                    val sIntent = Intent(context, ReminderReceiver::class.java).apply {
                        action = ACTION_SNOOZE
                        putExtra(EXTRA_NOTIF_ID, notifId)
                        putExtra(EXTRA_SNOOZE_MINUTES, minutes)
                        putExtra(EXTRA_EVENT_ID, eventId)
                        putExtra(EXTRA_TITLE, title)
                        putExtra(EXTRA_SUBTITLE, subtitle)
                        putExtra(EXTRA_LOCATION, location)
                        putExtra(EXTRA_COLOR, color)
                        putExtra(EXTRA_OCC_START, occStart)
                    }
                    return PendingIntent.getBroadcast(
                        context,
                        notifId + codeOffset,
                        sIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                val body = buildString {
                    if (subtitle.isNotBlank()) append(subtitle)
                    if (location.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("Ort: $location")
                    }
                }

                val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(body.ifBlank { "Anstehender Termin" })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { title }))
                    .setColor(color)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openPending)
                    .addAction(0, "10 Min schlummern", snoozePending(10, 1))
                    .addAction(0, "30 Min schlummern", snoozePending(30, 2))
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notifId, notification)
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.bttrcal.app.ACTION_TRIGGER_REMINDER"
        const val ACTION_SNOOZE = "com.bttrcal.app.ACTION_SNOOZE"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_COLOR = "extra_color"
        const val EXTRA_OCC_START = "extra_occ_start"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_min"
    }
}
