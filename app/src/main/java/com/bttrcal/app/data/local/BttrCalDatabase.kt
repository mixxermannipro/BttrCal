package com.bttrcal.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.bttrcal.app.domain.model.CalendarAccount
import com.bttrcal.app.domain.model.CalendarAccountType
import com.bttrcal.app.domain.model.CalendarColors
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.domain.model.SyncLogEntry
import com.bttrcal.app.domain.model.SyncState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class BttrCalDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION
) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sync_accounts (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                email_or_user TEXT NOT NULL,
                account_type TEXT NOT NULL,
                server_url TEXT NOT NULL,
                last_synced_at INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                sync_status_message TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE calendars (
                id TEXT PRIMARY KEY,
                account_id TEXT NOT NULL,
                name TEXT NOT NULL,
                color_argb INTEGER NOT NULL,
                account_type TEXT NOT NULL,
                is_visible INTEGER NOT NULL,
                is_read_only INTEGER NOT NULL,
                is_default INTEGER NOT NULL,
                android_calendar_id INTEGER,
                remote_id TEXT,
                sync_url TEXT,
                account_name TEXT,
                account_owner_type TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE events (
                id TEXT PRIMARY KEY,
                uid TEXT NOT NULL,
                calendar_id TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                location TEXT NOT NULL,
                url TEXT NOT NULL,
                start_millis INTEGER NOT NULL,
                end_millis INTEGER NOT NULL,
                all_day INTEGER NOT NULL,
                time_zone_id TEXT NOT NULL,
                rrule TEXT,
                exdates TEXT NOT NULL,
                reminder_minutes TEXT NOT NULL,
                availability TEXT NOT NULL,
                color_override_argb INTEGER,
                attendees_summary TEXT NOT NULL,
                android_event_id INTEGER,
                remote_id TEXT,
                etag TEXT,
                sync_state TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(calendar_id) REFERENCES calendars(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_events_range ON events(start_millis, end_millis)")
        db.execSQL("CREATE INDEX idx_events_calendar ON events(calendar_id)")
        db.execSQL("CREATE INDEX idx_events_android ON events(android_event_id)")

        db.execSQL(
            """
            CREATE TABLE sync_logs (
                id TEXT PRIMARY KEY,
                timestamp INTEGER NOT NULL,
                source_label TEXT NOT NULL,
                success INTEGER NOT NULL,
                pulled_count INTEGER NOT NULL,
                pushed_count INTEGER NOT NULL,
                message TEXT NOT NULL
            )
            """.trimIndent()
        )

        seedDefaults(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun seedDefaults(db: SQLiteDatabase) {
        val localAccountId = "local_device"
        db.insertWithOnConflict(
            "sync_accounts",
            null,
            accountToValues(
                CalendarAccount(
                    id = localAccountId,
                    name = "Lokaler Speicher",
                    emailOrUser = "Dieses Gerät",
                    accountType = CalendarAccountType.LOCAL,
                    enabled = true,
                    syncStatusMessage = "Immer lokal verfügbar"
                )
            ),
            SQLiteDatabase.CONFLICT_REPLACE
        )

        val privateId = "cal_local_private"
        val workId = "cal_local_work"
        val bdayId = "cal_local_birthdays"

        listOf(
            CalendarSource(
                id = privateId,
                accountId = localAccountId,
                name = "Privat",
                colorArgb = CalendarColors.palette[0],
                accountType = CalendarAccountType.LOCAL,
                isVisible = true,
                isDefault = true
            ),
            CalendarSource(
                id = workId,
                accountId = localAccountId,
                name = "Arbeit",
                colorArgb = CalendarColors.palette[1],
                accountType = CalendarAccountType.LOCAL,
                isVisible = true
            ),
            CalendarSource(
                id = bdayId,
                accountId = localAccountId,
                name = "Geburtstage & Highlights",
                colorArgb = CalendarColors.palette[2],
                accountType = CalendarAccountType.LOCAL,
                isVisible = true
            )
        ).forEach { cal ->
            db.insertWithOnConflict("calendars", null, calendarToValues(cal), SQLiteDatabase.CONFLICT_REPLACE)
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()

        fun millis(date: LocalDate, hour: Int, min: Int): Long =
            ZonedDateTime.of(date, LocalTime.of(hour, min), zone).toInstant().toEpochMilli()

        val sampleEvents = listOf(
            CalendarEvent(
                id = "evt_welcome_1",
                uid = UUID.randomUUID().toString(),
                calendarId = workId,
                title = "Morgen-Fokus & Tagesplanung",
                description = "Überblick über Prioritäten, Sync-Status und offene Punkte.",
                location = "Home Office",
                startMillis = millis(today, 9, 30),
                endMillis = millis(today, 10, 30),
                timeZoneId = zone.id,
                reminderMinutes = listOf(15),
                updatedAt = now,
                createdAt = now
            ),
            CalendarEvent(
                id = "evt_welcome_2",
                uid = UUID.randomUUID().toString(),
                calendarId = privateId,
                title = "Sport & Bewegung",
                description = "Lockere Einheit im Park oder Studio.",
                location = "Stadtpark",
                startMillis = millis(today, 17, 30),
                endMillis = millis(today, 18, 30),
                timeZoneId = zone.id,
                reminderMinutes = listOf(30),
                updatedAt = now,
                createdAt = now
            ),
            CalendarEvent(
                id = "evt_welcome_3",
                uid = UUID.randomUUID().toString(),
                calendarId = workId,
                title = "Projekt-Sync & Review",
                description = "Wöchentlicher Abgleich im Team.",
                location = "Video-Call",
                startMillis = millis(today.plusDays(1), 11, 0),
                endMillis = millis(today.plusDays(1), 12, 0),
                timeZoneId = zone.id,
                reminderMinutes = listOf(15),
                updatedAt = now,
                createdAt = now
            ),
            CalendarEvent(
                id = "evt_welcome_4",
                uid = UUID.randomUUID().toString(),
                calendarId = bdayId,
                title = "Wochenend-Ausflug planen",
                description = "Route und Treffpunkt abstimmen.",
                startMillis = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillis = today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli(),
                allDay = true,
                timeZoneId = zone.id,
                reminderMinutes = listOf(60),
                updatedAt = now,
                createdAt = now
            )
        )

        sampleEvents.forEach { ev ->
            db.insertWithOnConflict("events", null, eventToValues(ev), SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // --- Accounts ---

    fun getAccounts(): List<CalendarAccount> {
        val list = mutableListOf<CalendarAccount>()
        readableDatabase.rawQuery("SELECT * FROM sync_accounts ORDER BY account_type, name", null).use { c ->
            while (c.moveToNext()) list += cursorToAccount(c)
        }
        return list
    }

    fun upsertAccount(account: CalendarAccount) {
        writableDatabase.insertWithOnConflict(
            "sync_accounts",
            null,
            accountToValues(account),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteAccount(accountId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("calendars", "account_id = ?", arrayOf(accountId))
            writableDatabase.delete("sync_accounts", "id = ?", arrayOf(accountId))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    // --- Calendars ---

    fun getCalendars(): List<CalendarSource> {
        val list = mutableListOf<CalendarSource>()
        readableDatabase.rawQuery("SELECT * FROM calendars ORDER BY is_default DESC, name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) list += cursorToCalendar(c)
        }
        return list
    }

    fun upsertCalendar(calendar: CalendarSource) {
        writableDatabase.insertWithOnConflict(
            "calendars",
            null,
            calendarToValues(calendar),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun setCalendarVisibility(calendarId: String, visible: Boolean) {
        val cv = ContentValues().apply { put("is_visible", if (visible) 1 else 0) }
        writableDatabase.update("calendars", cv, "id = ?", arrayOf(calendarId))
    }

    fun deleteCalendar(calendarId: String) {
        writableDatabase.delete("calendars", "id = ?", arrayOf(calendarId))
    }

    // --- Events ---

    fun getAllEvents(): List<CalendarEvent> {
        val list = mutableListOf<CalendarEvent>()
        readableDatabase.rawQuery("SELECT * FROM events WHERE sync_state != 'PENDING_DELETE' ORDER BY start_millis ASC", null).use { c ->
            while (c.moveToNext()) list += cursorToEvent(c)
        }
        return list
    }

    fun getEventsForRange(rangeStartMillis: Long, rangeEndMillis: Long): List<CalendarEvent> {
        val list = mutableListOf<CalendarEvent>()
        readableDatabase.rawQuery(
            """
            SELECT * FROM events
            WHERE sync_state != 'PENDING_DELETE'
              AND ((end_millis > ? AND start_millis < ?) OR (rrule IS NOT NULL AND rrule != ''))
            ORDER BY start_millis ASC
            """.trimIndent(),
            arrayOf(rangeStartMillis.toString(), rangeEndMillis.toString())
        ).use { c ->
            while (c.moveToNext()) list += cursorToEvent(c)
        }
        return list
    }

    fun getEventsByCalendar(calendarId: String, includeDeleted: Boolean = false): List<CalendarEvent> {
        val list = mutableListOf<CalendarEvent>()
        val query = if (includeDeleted) {
            "SELECT * FROM events WHERE calendar_id = ? ORDER BY start_millis ASC"
        } else {
            "SELECT * FROM events WHERE calendar_id = ? AND sync_state != 'PENDING_DELETE' ORDER BY start_millis ASC"
        }
        readableDatabase.rawQuery(query, arrayOf(calendarId)).use { c ->
            while (c.moveToNext()) list += cursorToEvent(c)
        }
        return list
    }

    fun getPendingSyncEvents(): List<CalendarEvent> {
        val list = mutableListOf<CalendarEvent>()
        readableDatabase.rawQuery(
            "SELECT * FROM events WHERE sync_state != 'SYNCED'",
            null
        ).use { c ->
            while (c.moveToNext()) list += cursorToEvent(c)
        }
        return list
    }

    fun getEventById(id: String): CalendarEvent? {
        readableDatabase.rawQuery("SELECT * FROM events WHERE id = ? LIMIT 1", arrayOf(id)).use { c ->
            return if (c.moveToFirst()) cursorToEvent(c) else null
        }
    }

    fun upsertEvent(event: CalendarEvent) {
        writableDatabase.insertWithOnConflict(
            "events",
            null,
            eventToValues(event),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertEventsBatch(events: List<CalendarEvent>) {
        if (events.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            events.forEach { ev ->
                writableDatabase.insertWithOnConflict(
                    "events",
                    null,
                    eventToValues(ev),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun hardDeleteEvent(eventId: String) {
        writableDatabase.delete("events", "id = ?", arrayOf(eventId))
    }

    fun deleteSyncedEventsMissingFrom(calendarId: String, activeRemoteOrAndroidIds: Set<String>) {
        val existing = getEventsByCalendar(calendarId, includeDeleted = false)
        writableDatabase.beginTransaction()
        try {
            for (ev in existing) {
                if (ev.syncState == SyncState.SYNCED) {
                    val key = ev.remoteId ?: ev.androidEventId?.toString() ?: ev.uid
                    if (key !in activeRemoteOrAndroidIds) {
                        writableDatabase.delete("events", "id = ?", arrayOf(ev.id))
                    }
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    // --- Sync Logs ---

    fun insertSyncLog(entry: SyncLogEntry) {
        val cv = ContentValues().apply {
            put("id", entry.id)
            put("timestamp", entry.timestamp)
            put("source_label", entry.sourceLabel)
            put("success", if (entry.success) 1 else 0)
            put("pulled_count", entry.pulledCount)
            put("pushed_count", entry.pushedCount)
            put("message", entry.message)
        }
        writableDatabase.insertWithOnConflict("sync_logs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL(
            """
            DELETE FROM sync_logs WHERE id NOT IN (
                SELECT id FROM sync_logs ORDER BY timestamp DESC LIMIT 40
            )
            """.trimIndent()
        )
    }

    fun getRecentSyncLogs(limit: Int = 20): List<SyncLogEntry> {
        val list = mutableListOf<SyncLogEntry>()
        readableDatabase.rawQuery(
            "SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list += SyncLogEntry(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                    sourceLabel = c.getString(c.getColumnIndexOrThrow("source_label")),
                    success = c.getInt(c.getColumnIndexOrThrow("success")) == 1,
                    pulledCount = c.getInt(c.getColumnIndexOrThrow("pulled_count")),
                    pushedCount = c.getInt(c.getColumnIndexOrThrow("pushed_count")),
                    message = c.getString(c.getColumnIndexOrThrow("message"))
                )
            }
        }
        return list
    }

    // --- Mapping ---

    private fun accountToValues(a: CalendarAccount): ContentValues = ContentValues().apply {
        put("id", a.id)
        put("name", a.name)
        put("email_or_user", a.emailOrUser)
        put("account_type", a.accountType.name)
        put("server_url", a.serverUrl)
        put("last_synced_at", a.lastSyncedAt)
        put("enabled", if (a.enabled) 1 else 0)
        put("sync_status_message", a.syncStatusMessage)
    }

    private fun cursorToAccount(c: Cursor): CalendarAccount = CalendarAccount(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        emailOrUser = c.getString(c.getColumnIndexOrThrow("email_or_user")),
        accountType = runCatching {
            CalendarAccountType.valueOf(c.getString(c.getColumnIndexOrThrow("account_type")))
        }.getOrDefault(CalendarAccountType.LOCAL),
        serverUrl = c.getString(c.getColumnIndexOrThrow("server_url")),
        lastSyncedAt = c.getLong(c.getColumnIndexOrThrow("last_synced_at")),
        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
        syncStatusMessage = c.getString(c.getColumnIndexOrThrow("sync_status_message"))
    )

    private fun calendarToValues(cal: CalendarSource): ContentValues = ContentValues().apply {
        put("id", cal.id)
        put("account_id", cal.accountId)
        put("name", cal.name)
        put("color_argb", cal.colorArgb)
        put("account_type", cal.accountType.name)
        put("is_visible", if (cal.isVisible) 1 else 0)
        put("is_read_only", if (cal.isReadOnly) 1 else 0)
        put("is_default", if (cal.isDefault) 1 else 0)
        if (cal.androidCalendarId != null) put("android_calendar_id", cal.androidCalendarId) else putNull("android_calendar_id")
        put("remote_id", cal.remoteId)
        put("sync_url", cal.syncUrl)
        put("account_name", cal.accountName)
        put("account_owner_type", cal.accountOwnerType)
    }

    private fun cursorToCalendar(c: Cursor): CalendarSource {
        val androidIdIdx = c.getColumnIndexOrThrow("android_calendar_id")
        return CalendarSource(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            accountId = c.getString(c.getColumnIndexOrThrow("account_id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            colorArgb = c.getInt(c.getColumnIndexOrThrow("color_argb")),
            accountType = runCatching {
                CalendarAccountType.valueOf(c.getString(c.getColumnIndexOrThrow("account_type")))
            }.getOrDefault(CalendarAccountType.LOCAL),
            isVisible = c.getInt(c.getColumnIndexOrThrow("is_visible")) == 1,
            isReadOnly = c.getInt(c.getColumnIndexOrThrow("is_read_only")) == 1,
            isDefault = c.getInt(c.getColumnIndexOrThrow("is_default")) == 1,
            androidCalendarId = if (c.isNull(androidIdIdx)) null else c.getLong(androidIdIdx),
            remoteId = c.getString(c.getColumnIndexOrThrow("remote_id")),
            syncUrl = c.getString(c.getColumnIndexOrThrow("sync_url")),
            accountName = c.getString(c.getColumnIndexOrThrow("account_name")),
            accountOwnerType = c.getString(c.getColumnIndexOrThrow("account_owner_type"))
        )
    }

    private fun eventToValues(ev: CalendarEvent): ContentValues = ContentValues().apply {
        put("id", ev.id)
        put("uid", ev.uid)
        put("calendar_id", ev.calendarId)
        put("title", ev.title)
        put("description", ev.description)
        put("location", ev.location)
        put("url", ev.url)
        put("start_millis", ev.startMillis)
        put("end_millis", ev.endMillis)
        put("all_day", if (ev.allDay) 1 else 0)
        put("time_zone_id", ev.timeZoneId)
        put("rrule", ev.rrule)
        put("exdates", ev.exdates.joinToString(","))
        put("reminder_minutes", ev.reminderMinutes.joinToString(","))
        put("availability", ev.availability.name)
        if (ev.colorOverrideArgb != null) put("color_override_argb", ev.colorOverrideArgb) else putNull("color_override_argb")
        put("attendees_summary", ev.attendeesSummary)
        if (ev.androidEventId != null) put("android_event_id", ev.androidEventId) else putNull("android_event_id")
        put("remote_id", ev.remoteId)
        put("etag", ev.etag)
        put("sync_state", ev.syncState.name)
        put("updated_at", ev.updatedAt)
        put("created_at", ev.createdAt)
    }

    private fun cursorToEvent(c: Cursor): CalendarEvent {
        val colorIdx = c.getColumnIndexOrThrow("color_override_argb")
        val androidEvIdx = c.getColumnIndexOrThrow("android_event_id")
        val exdatesRaw = c.getString(c.getColumnIndexOrThrow("exdates"))
        val remRaw = c.getString(c.getColumnIndexOrThrow("reminder_minutes"))

        return CalendarEvent(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            uid = c.getString(c.getColumnIndexOrThrow("uid")),
            calendarId = c.getString(c.getColumnIndexOrThrow("calendar_id")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            description = c.getString(c.getColumnIndexOrThrow("description")),
            location = c.getString(c.getColumnIndexOrThrow("location")),
            url = c.getString(c.getColumnIndexOrThrow("url")),
            startMillis = c.getLong(c.getColumnIndexOrThrow("start_millis")),
            endMillis = c.getLong(c.getColumnIndexOrThrow("end_millis")),
            allDay = c.getInt(c.getColumnIndexOrThrow("all_day")) == 1,
            timeZoneId = c.getString(c.getColumnIndexOrThrow("time_zone_id")),
            rrule = c.getString(c.getColumnIndexOrThrow("rrule")),
            exdates = exdatesRaw.split(",").mapNotNull { it.trim().toLongOrNull() },
            reminderMinutes = remRaw.split(",").mapNotNull { it.trim().toIntOrNull() },
            availability = runCatching {
                EventAvailability.valueOf(c.getString(c.getColumnIndexOrThrow("availability")))
            }.getOrDefault(EventAvailability.BUSY),
            colorOverrideArgb = if (c.isNull(colorIdx)) null else c.getInt(colorIdx),
            attendeesSummary = c.getString(c.getColumnIndexOrThrow("attendees_summary")),
            androidEventId = if (c.isNull(androidEvIdx)) null else c.getLong(androidEvIdx),
            remoteId = c.getString(c.getColumnIndexOrThrow("remote_id")),
            etag = c.getString(c.getColumnIndexOrThrow("etag")),
            syncState = runCatching {
                SyncState.valueOf(c.getString(c.getColumnIndexOrThrow("sync_state")))
            }.getOrDefault(SyncState.SYNCED),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        )
    }

    companion object {
        private const val DB_NAME = "bttrcal.db"
        private const val DB_VERSION = 1
    }
}
