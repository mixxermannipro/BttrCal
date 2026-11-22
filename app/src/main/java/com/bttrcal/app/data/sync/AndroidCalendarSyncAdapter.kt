package com.bttrcal.app.data.sync

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.domain.model.CalendarAccount
import com.bttrcal.app.domain.model.CalendarAccountType
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.domain.model.SyncState
import java.time.ZoneId
import java.util.UUID

data class SyncAdapterResult(
    val sourceLabel: String,
    val success: Boolean,
    val pulledCount: Int = 0,
    val pushedCount: Int = 0,
    val message: String
)

class AndroidCalendarSyncAdapter(
    private val context: Context,
    private val database: BttrCalDatabase
) {

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun sync(): SyncAdapterResult {
        if (!hasReadPermission()) {
            return SyncAdapterResult(
                sourceLabel = "Google & System-Kalender",
                success = false,
                message = "Kalender-Berechtigung nicht erteilt"
            )
        }

        val resolver = context.contentResolver
        var pulled = 0
        var pushed = 0

        val existingCalendarsByAndroidId = database.getCalendars()
            .filter { it.androidCalendarId != null }
            .associateBy { it.androidCalendarId!! }

        val discoveredCalendars = mutableListOf<CalendarSource>()
        val syncedAccounts = mutableMapOf<String, CalendarAccount>()

        val calProjection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY
        )

        resolver.query(CalendarContract.Calendars.CONTENT_URI, calProjection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val androidCalId = c.getLong(0)
                val displayName = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Kalender $androidCalId"
                val color = c.getInt(2)
                val accName = c.getString(3) ?: "Gerät"
                val accType = c.getString(4) ?: "LOCAL"
                val accessLevel = c.getInt(5)
                val visibleOnSystem = c.getInt(6) == 1
                val isPrimary = c.getInt(7) == 1

                val accountId = "android_${accType}_$accName".replace("[^A-Za-z0-9_.-]".toRegex(), "_")
                val calId = "android_cal_$androidCalId"
                val readOnly = accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                val prev = existingCalendarsByAndroidId[androidCalId]

                val friendlyAccountLabel = when {
                    accType.contains("google", ignoreCase = true) -> "Google Kalender ($accName)"
                    accType.contains("honor", ignoreCase = true) || accType.contains("huawei", ignoreCase = true) ->
                        "Honor Kalender ($accName)"
                    accType.contains("exchange", ignoreCase = true) -> "Exchange ($accName)"
                    else -> "$accName ($accType)"
                }

                syncedAccounts[accountId] = CalendarAccount(
                    id = accountId,
                    name = friendlyAccountLabel,
                    emailOrUser = accName,
                    accountType = CalendarAccountType.ANDROID_PROVIDER,
                    lastSyncedAt = System.currentTimeMillis(),
                    enabled = true,
                    syncStatusMessage = "Verbunden über Android Kalender-Provider"
                )

                val source = CalendarSource(
                    id = calId,
                    accountId = accountId,
                    name = displayName,
                    colorArgb = if (color != 0) color else (prev?.colorArgb ?: 0xFF0A84FF.toInt()),
                    accountType = CalendarAccountType.ANDROID_PROVIDER,
                    isVisible = prev?.isVisible ?: visibleOnSystem,
                    isReadOnly = readOnly,
                    isDefault = prev?.isDefault ?: isPrimary,
                    androidCalendarId = androidCalId,
                    accountName = accName,
                    accountOwnerType = accType
                )
                database.upsertCalendar(source)
                discoveredCalendars += source
            }
        }

        syncedAccounts.values.forEach { database.upsertAccount(it) }

        // Push local pending changes to CalendarContract before pulling
        if (hasWritePermission()) {
            val calMap = discoveredCalendars.associateBy { it.id }
            val pending = database.getPendingSyncEvents().filter { ev ->
                calMap[ev.calendarId]?.androidCalendarId != null
            }
            for (ev in pending) {
                val cal = calMap[ev.calendarId] ?: continue
                if (cal.isReadOnly) continue
                val androidCalId = cal.androidCalendarId ?: continue

                when (ev.syncState) {
                    SyncState.PENDING_CREATE -> {
                        val insertedId = insertIntoProvider(resolver, androidCalId, ev)
                        if (insertedId != null) {
                            database.upsertEvent(
                                ev.copy(
                                    androidEventId = insertedId,
                                    syncState = SyncState.SYNCED,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            pushed++
                            requestSystemSync(cal)
                        }
                    }
                    SyncState.PENDING_UPDATE -> {
                        val targetId = ev.androidEventId
                        val updated = if (targetId != null) {
                            updateInProvider(resolver, targetId, ev)
                        } else {
                            insertIntoProvider(resolver, androidCalId, ev) != null
                        }
                        if (updated) {
                            database.upsertEvent(ev.copy(syncState = SyncState.SYNCED))
                            pushed++
                            requestSystemSync(cal)
                        }
                    }
                    SyncState.PENDING_DELETE -> {
                        val targetId = ev.androidEventId
                        if (targetId != null) {
                            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, targetId)
                            resolver.delete(deleteUri, null, null)
                            pushed++
                            requestSystemSync(cal)
                        }
                        database.hardDeleteEvent(ev.id)
                    }
                    SyncState.SYNCED -> Unit
                }
            }
        }

        // Pull events for each discovered Android calendar (window: -90 days .. +365 days or recurring)
        val now = System.currentTimeMillis()
        val windowStart = now - 90L * 86_400_000L
        val windowEnd = now + 365L * 86_400_000L

        val evProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.UID_2445
        )

        for (cal in discoveredCalendars) {
            val androidCalId = cal.androidCalendarId ?: continue
            val existingByAndroidId = database.getEventsByCalendar(cal.id, includeDeleted = true)
                .filter { it.androidEventId != null }
                .associateBy { it.androidEventId!! }

            val activeAndroidIds = mutableSetOf<String>()
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0 AND " +
                "((${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?) OR " +
                "(${CalendarContract.Events.RRULE} IS NOT NULL AND ${CalendarContract.Events.RRULE} != ''))"
            val args = arrayOf(androidCalId.toString(), windowStart.toString(), windowEnd.toString())

            val batch = mutableListOf<CalendarEvent>()
            resolver.query(CalendarContract.Events.CONTENT_URI, evProjection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val androidEvId = c.getLong(0)
                    activeAndroidIds += androidEvId.toString()

                    val existing = existingByAndroidId[androidEvId]
                    if (existing != null && existing.syncState != SyncState.SYNCED) {
                        continue
                    }

                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Ohne Titel"
                    val desc = c.getString(2) ?: ""
                    val loc = c.getString(3) ?: ""
                    val start = c.getLong(4)
                    val rawEnd = if (c.isNull(5)) 0L else c.getLong(5)
                    val durationStr = c.getString(6)
                    val allDay = c.getInt(7) == 1
                    val tz = c.getString(8)?.takeIf { it.isNotBlank() } ?: ZoneId.systemDefault().id
                    val rrule = c.getString(9)?.takeIf { it.isNotBlank() }
                    val availRaw = c.getInt(10)
                    val evColor = if (c.isNull(11)) null else c.getInt(11)
                    val uid2445 = c.getString(12)?.takeIf { it.isNotBlank() } ?: "android_${androidCalId}_$androidEvId"

                    val end = when {
                        rawEnd > start -> rawEnd
                        !durationStr.isNullOrBlank() -> start + parseProviderDuration(durationStr, allDay)
                        allDay -> start + 86_400_000L
                        else -> start + 3_600_000L
                    }

                    val availability = when (availRaw) {
                        CalendarContract.Events.AVAILABILITY_FREE -> EventAvailability.FREE
                        CalendarContract.Events.AVAILABILITY_TENTATIVE -> EventAvailability.TENTATIVE
                        else -> EventAvailability.BUSY
                    }

                    val reminders = queryReminders(resolver, androidEvId)
                    batch += CalendarEvent(
                        id = existing?.id ?: "and_${androidCalId}_$androidEvId",
                        uid = existing?.uid ?: uid2445,
                        calendarId = cal.id,
                        title = title,
                        description = desc,
                        location = loc,
                        startMillis = start,
                        endMillis = end,
                        allDay = allDay,
                        timeZoneId = tz,
                        rrule = rrule,
                        reminderMinutes = reminders.ifEmpty { listOf(15) },
                        availability = availability,
                        colorOverrideArgb = evColor,
                        androidEventId = androidEvId,
                        syncState = SyncState.SYNCED,
                        updatedAt = System.currentTimeMillis(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                    pulled++
                }
            }
            database.upsertEventsBatch(batch)
            database.deleteSyncedEventsMissingFrom(cal.id, activeAndroidIds)
        }

        val summary = if (discoveredCalendars.isEmpty()) {
            "Keine System-/Google-Kalender auf dem Gerät gefunden"
        } else {
            "${discoveredCalendars.size} Kalender · $pulled geladen, $pushed gesendet"
        }

        return SyncAdapterResult(
            sourceLabel = "Google & System-Kalender",
            success = true,
            pulledCount = pulled,
            pushedCount = pushed,
            message = summary
        )
    }

    private fun queryReminders(resolver: ContentResolver, androidEventId: Long): List<Int> {
        val list = mutableListOf<Int>()
        val proj = arrayOf(CalendarContract.Reminders.MINUTES)
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            proj,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(androidEventId.toString()),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val m = c.getInt(0)
                if (m >= 0) list += m
            }
        }
        return list.distinct()
    }

    private fun insertIntoProvider(resolver: ContentResolver, androidCalId: Long, ev: CalendarEvent): Long? {
        val cv = buildEventContentValues(androidCalId, ev)
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, cv) ?: return null
        val newId = uri.lastPathSegment?.toLongOrNull() ?: return null
        replaceReminders(resolver, newId, ev.reminderMinutes)
        return newId
    }

    private fun updateInProvider(resolver: ContentResolver, androidEventId: Long, ev: CalendarEvent): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, androidEventId)
        val cv = buildEventContentValues(null, ev)
        val rows = resolver.update(uri, cv, null, null)
        replaceReminders(resolver, androidEventId, ev.reminderMinutes)
        return rows >= 0
    }

    private fun buildEventContentValues(androidCalId: Long?, ev: CalendarEvent): ContentValues = ContentValues().apply {
        if (androidCalId != null) put(CalendarContract.Events.CALENDAR_ID, androidCalId)
        put(CalendarContract.Events.TITLE, ev.title)
        put(CalendarContract.Events.DESCRIPTION, ev.description)
        put(CalendarContract.Events.EVENT_LOCATION, ev.location)
        put(CalendarContract.Events.DTSTART, ev.startMillis)
        put(CalendarContract.Events.ALL_DAY, if (ev.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, ev.timeZoneId)
        if (!ev.rrule.isNullOrBlank()) {
            put(CalendarContract.Events.RRULE, ev.rrule.removePrefix("RRULE:"))
            val sec = ((ev.endMillis - ev.startMillis).coerceAtLeast(60_000L) / 1000L)
            put(CalendarContract.Events.DURATION, "PT${sec}S")
            putNull(CalendarContract.Events.DTEND)
        } else {
            put(CalendarContract.Events.DTEND, ev.endMillis)
            putNull(CalendarContract.Events.DURATION)
            putNull(CalendarContract.Events.RRULE)
        }
        val avail = when (ev.availability) {
            EventAvailability.FREE -> CalendarContract.Events.AVAILABILITY_FREE
            EventAvailability.TENTATIVE -> CalendarContract.Events.AVAILABILITY_TENTATIVE
            EventAvailability.BUSY -> CalendarContract.Events.AVAILABILITY_BUSY
        }
        put(CalendarContract.Events.AVAILABILITY, avail)
        put(CalendarContract.Events.UID_2445, ev.uid.ifBlank { UUID.randomUUID().toString() })
    }

    private fun replaceReminders(resolver: ContentResolver, androidEventId: Long, minutes: List<Int>) {
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(androidEventId.toString())
        )
        minutes.filter { it >= 0 }.distinct().forEach { mins ->
            val rCv = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, androidEventId)
                put(CalendarContract.Reminders.MINUTES, mins)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, rCv)
        }
    }

    private fun requestSystemSync(cal: CalendarSource) {
        val accName = cal.accountName ?: return
        val accType = cal.accountOwnerType ?: return
        if (accType.equals("LOCAL", ignoreCase = true)) return
        runCatching {
            val account = android.accounts.Account(accName, accType)
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
        }
    }

    private fun parseProviderDuration(raw: String, allDay: Boolean): Long {
        val clean = raw.trim().uppercase()
        return runCatching {
            when {
                clean.startsWith("PT") && clean.endsWith("S") ->
                    clean.removePrefix("PT").removeSuffix("S").toLong() * 1000L
                clean.startsWith("PT") -> java.time.Duration.parse(clean).toMillis()
                clean.startsWith("P") && clean.endsWith("D") ->
                    clean.removePrefix("P").removeSuffix("D").toLong() * 86_400_000L
                else -> if (allDay) 86_400_000L else 3_600_000L
            }
        }.getOrElse { if (allDay) 86_400_000L else 3_600_000L }
    }
}
