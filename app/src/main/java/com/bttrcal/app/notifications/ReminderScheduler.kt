package com.bttrcal.app.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bttrcal.app.R
import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.domain.engine.RecurrenceEngine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReminderScheduler {

    const val CHANNEL_ID = "bttrcal_reminders"
    private const val HORIZON_DAYS = 14L
    private const val MAX_ALARMS = 48
    private const val PREFS_NAME = "bttrcal_alarm_ids"
    private const val KEY_IDS = "scheduled_request_codes"
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun rescheduleUpcoming(context: Context, database: BttrCalDatabase) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cancel previously scheduled alarms
        val oldIds = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        oldIds.mapNotNull { it.toIntOrNull() }.forEach { reqCode ->
            val pending = PendingIntent.getBroadcast(
                context,
                reqCode,
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }

        val now = System.currentTimeMillis()
        val horizonEnd = now + HORIZON_DAYS * 86_400_000L
        val calendars = database.getCalendars().filter { it.isVisible }.associateBy { it.id }
        val events = database.getEventsForRange(now - 86_400_000L, horizonEnd)
        val zone = ZoneId.systemDefault()

        data class ScheduledItem(
            val triggerAt: Long,
            val requestCode: Int,
            val eventId: String,
            val title: String,
            val subtitle: String,
            val location: String,
            val colorArgb: Int,
            val occStart: Long
        )

        val candidates = mutableListOf<ScheduledItem>()
        for (ev in events) {
            val cal = calendars[ev.calendarId] ?: continue
            if (ev.reminderMinutes.isEmpty()) continue
            val occs = RecurrenceEngine.expandOccurrences(
                event = ev,
                calendarColorArgb = cal.colorArgb,
                calendarName = cal.name,
                rangeStartMillis = now,
                rangeEndMillis = horizonEnd,
                defaultZoneId = zone
            )
            for (occ in occs) {
                for (mins in ev.reminderMinutes) {
                    if (mins < 0) continue
                    val triggerAt = occ.occurrenceStartMillis - mins * 60_000L
                    if (triggerAt <= now + 5_000L) continue
                    val startZdt = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zone)
                    val endZdt = Instant.ofEpochMilli(occ.occurrenceEndMillis).atZone(zone)
                    val subtitle = if (ev.allDay) {
                        "Ganztägig · ${cal.name}"
                    } else {
                        "${startZdt.format(TIME_FMT)}–${endZdt.format(TIME_FMT)} · ${cal.name}"
                    }
                    val reqCode = ("${ev.id}_${occ.occurrenceStartMillis}_$mins").hashCode()
                    candidates += ScheduledItem(
                        triggerAt = triggerAt,
                        requestCode = reqCode,
                        eventId = ev.id,
                        title = ev.title,
                        subtitle = subtitle,
                        location = ev.location,
                        colorArgb = occ.displayColorArgb,
                        occStart = occ.occurrenceStartMillis
                    )
                }
            }
        }

        val selected = candidates.sortedBy { it.triggerAt }.take(MAX_ALARMS)
        val newIds = mutableSetOf<String>()

        for (item in selected) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_TRIGGER_REMINDER
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, item.eventId)
                putExtra(ReminderReceiver.EXTRA_TITLE, item.title)
                putExtra(ReminderReceiver.EXTRA_SUBTITLE, item.subtitle)
                putExtra(ReminderReceiver.EXTRA_LOCATION, item.location)
                putExtra(ReminderReceiver.EXTRA_COLOR, item.colorArgb)
                putExtra(ReminderReceiver.EXTRA_OCC_START, item.occStart)
                putExtra(ReminderReceiver.EXTRA_NOTIF_ID, item.requestCode)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                item.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleAlarmCompat(alarmManager, item.triggerAt, pending)
            newIds += item.requestCode.toString()
        }

        prefs.edit().putStringSet(KEY_IDS, newIds).apply()
    }

    fun scheduleSnooze(
        context: Context,
        minutes: Int,
        eventId: String,
        title: String,
        subtitle: String,
        location: String,
        colorArgb: Int,
        occStart: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val reqCode = ("snooze_${eventId}_${System.currentTimeMillis()}").hashCode()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_SUBTITLE, subtitle)
            putExtra(ReminderReceiver.EXTRA_LOCATION, location)
            putExtra(ReminderReceiver.EXTRA_COLOR, colorArgb)
            putExtra(ReminderReceiver.EXTRA_OCC_START, occStart)
            putExtra(ReminderReceiver.EXTRA_NOTIF_ID, reqCode)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmCompat(alarmManager, triggerAt, pending)
    }

    private fun scheduleAlarmCompat(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
}
