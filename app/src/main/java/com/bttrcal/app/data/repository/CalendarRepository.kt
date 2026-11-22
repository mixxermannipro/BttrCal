package com.bttrcal.app.data.repository

import android.content.Context
import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.data.local.PreferencesRepository
import com.bttrcal.app.data.security.SecretVault
import com.bttrcal.app.data.sync.SyncScheduler
import com.bttrcal.app.domain.engine.DuplicateCluster
import com.bttrcal.app.domain.engine.DuplicateDetector
import com.bttrcal.app.domain.engine.IcsCodec
import com.bttrcal.app.domain.engine.RecurrenceEngine
import com.bttrcal.app.domain.model.CalendarAccount
import com.bttrcal.app.domain.model.CalendarAccountType
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.EventOccurrence
import com.bttrcal.app.domain.model.SyncLogEntry
import com.bttrcal.app.domain.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CalendarRepository(
    private val context: Context,
    private val database: BttrCalDatabase,
    private val preferences: PreferencesRepository,
    private val secretVault: SecretVault,
    private val onDataMutated: () -> Unit
) {

    private val _calendars = MutableStateFlow(database.getCalendars())
    val calendars: StateFlow<List<CalendarSource>> = _calendars.asStateFlow()

    private val _accounts = MutableStateFlow(database.getAccounts())
    val accounts: StateFlow<List<CalendarAccount>> = _accounts.asStateFlow()

    private val _syncLogs = MutableStateFlow(database.getRecentSyncLogs())
    val syncLogs: StateFlow<List<SyncLogEntry>> = _syncLogs.asStateFlow()

    private val _dataRevision = MutableStateFlow(0L)
    val dataRevision: StateFlow<Long> = _dataRevision.asStateFlow()

    private val rangeCache = ConcurrentHashMap<String, List<EventOccurrence>>()

    fun refreshFromDatabase() {
        rangeCache.clear()
        _calendars.value = database.getCalendars()
        _accounts.value = database.getAccounts()
        _syncLogs.value = database.getRecentSyncLogs()
        _dataRevision.value = _dataRevision.value + 1L
    }

    fun getOccurrencesInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
        onlyVisible: Boolean = true,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<EventOccurrence> {
        val cacheKey = "${rangeStartMillis}_${rangeEndMillis}_${onlyVisible}_${zoneId.id}_${_dataRevision.value}"
        rangeCache[cacheKey]?.let { return it }

        val calMap = _calendars.value
            .filter { !onlyVisible || it.isVisible }
            .associateBy { it.id }
        if (calMap.isEmpty()) return emptyList()

        val events = database.getEventsForRange(rangeStartMillis, rangeEndMillis)
        val result = mutableListOf<EventOccurrence>()
        for (ev in events) {
            val cal = calMap[ev.calendarId] ?: continue
            result += RecurrenceEngine.expandOccurrences(
                event = ev,
                calendarColorArgb = cal.colorArgb,
                calendarName = cal.name,
                rangeStartMillis = rangeStartMillis,
                rangeEndMillis = rangeEndMillis,
                defaultZoneId = zoneId
            )
        }
        val sorted = result.sortedWith(
            compareBy<EventOccurrence> { it.occurrenceStartMillis }
                .thenByDescending { it.event.allDay }
                .thenBy { it.event.title }
        )
        if (rangeCache.size > 48) rangeCache.clear()
        rangeCache[cacheKey] = sorted
        return sorted
    }

    fun searchEvents(query: String): List<EventOccurrence> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val calMap = _calendars.value.associateBy { it.id }
        return database.getAllEvents()
            .filter { ev ->
                ev.title.lowercase().contains(q) ||
                    ev.location.lowercase().contains(q) ||
                    ev.description.lowercase().contains(q)
            }
            .mapNotNull { ev ->
                val cal = calMap[ev.calendarId] ?: return@mapNotNull null
                EventOccurrence(
                    event = ev,
                    calendarColorArgb = cal.colorArgb,
                    calendarName = cal.name,
                    occurrenceStartMillis = ev.startMillis,
                    occurrenceEndMillis = ev.endMillis,
                    isRecurringInstance = !ev.rrule.isNullOrBlank()
                )
            }
    }

    fun getEventById(id: String): CalendarEvent? = database.getEventById(id)

    suspend fun saveEvent(event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        val cal = database.getCalendars().firstOrNull { it.id == event.calendarId }
        val isExternal = cal != null && cal.accountType != CalendarAccountType.LOCAL && !cal.isReadOnly
        val existing = database.getEventById(event.id)
        val nextSyncState = when {
            !isExternal -> SyncState.SYNCED
            existing == null -> SyncState.PENDING_CREATE
            existing.syncState == SyncState.PENDING_CREATE -> SyncState.PENDING_CREATE
            else -> SyncState.PENDING_UPDATE
        }
        val toSave = event.copy(
            uid = event.uid.ifBlank { UUID.randomUUID().toString() },
            syncState = nextSyncState,
            updatedAt = System.currentTimeMillis()
        )
        database.upsertEvent(toSave)
        refreshFromDatabase()
        onDataMutated()

        if (isExternal && preferences.settings.value.syncAfterEdit) {
            SyncScheduler.triggerImmediateBackgroundSync(context)
        }
        toSave
    }

    suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        val existing = database.getEventById(eventId) ?: return@withContext
        val cal = database.getCalendars().firstOrNull { it.id == existing.calendarId }
        val needsRemoteDelete = cal != null &&
            cal.accountType != CalendarAccountType.LOCAL &&
            !cal.isReadOnly &&
            (existing.androidEventId != null || existing.remoteId != null)

        if (needsRemoteDelete) {
            database.upsertEvent(
                existing.copy(
                    syncState = SyncState.PENDING_DELETE,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (preferences.settings.value.syncAfterEdit) {
                SyncScheduler.triggerImmediateBackgroundSync(context)
            }
        } else {
            database.hardDeleteEvent(eventId)
        }
        refreshFromDatabase()
        onDataMutated()
    }

    suspend fun mergeDuplicateCluster(
        cluster: DuplicateCluster,
        targetCalendarId: String,
        mergedTitle: String
    ): CalendarEvent = withContext(Dispatchers.IO) {
        val plan = DuplicateDetector.buildMergePlan(cluster, targetCalendarId, mergedTitle)
        val calMap = database.getCalendars().associateBy { it.id }
        var touchedExternal = false

        // 1. Persist merged event in target calendar
        val targetCal = calMap[plan.mergedEvent.calendarId]
        val targetExternal = targetCal != null && targetCal.accountType != CalendarAccountType.LOCAL && !targetCal.isReadOnly
        if (targetExternal) touchedExternal = true
        val existingTarget = database.getEventById(plan.mergedEvent.id)
        val nextState = when {
            !targetExternal -> SyncState.SYNCED
            existingTarget == null -> SyncState.PENDING_CREATE
            existingTarget.syncState == SyncState.PENDING_CREATE -> SyncState.PENDING_CREATE
            else -> SyncState.PENDING_UPDATE
        }
        val savedMerged = plan.mergedEvent.copy(
            uid = plan.mergedEvent.uid.ifBlank { UUID.randomUUID().toString() },
            syncState = nextState,
            updatedAt = System.currentTimeMillis()
        )
        database.upsertEvent(savedMerged)

        // 2. Delete all other duplicate events across calendars (respecting remote PENDING_DELETE)
        for (dupId in plan.eventIdsToDelete) {
            val dup = database.getEventById(dupId) ?: continue
            val dupCal = calMap[dup.calendarId]
            val needsRemoteDelete = dupCal != null &&
                dupCal.accountType != CalendarAccountType.LOCAL &&
                !dupCal.isReadOnly &&
                (dup.androidEventId != null || dup.remoteId != null)
            if (needsRemoteDelete) {
                touchedExternal = true
                database.upsertEvent(
                    dup.copy(
                        syncState = SyncState.PENDING_DELETE,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                database.hardDeleteEvent(dupId)
            }
        }

        refreshFromDatabase()
        onDataMutated()
        if (touchedExternal && preferences.settings.value.syncAfterEdit) {
            SyncScheduler.triggerImmediateBackgroundSync(context)
        }
        savedMerged
    }

    suspend fun createLocalCalendar(name: String, colorArgb: Int): CalendarSource = withContext(Dispatchers.IO) {
        val cal = CalendarSource(
            id = "cal_local_${UUID.randomUUID().toString().take(8)}",
            accountId = "local_device",
            name = name.trim().ifBlank { "Neuer Kalender" },
            colorArgb = colorArgb,
            accountType = CalendarAccountType.LOCAL,
            isVisible = true,
            isReadOnly = false,
            isDefault = false
        )
        database.upsertCalendar(cal)
        refreshFromDatabase()
        onDataMutated()
        cal
    }

    suspend fun setCalendarVisibility(calendarId: String, visible: Boolean) = withContext(Dispatchers.IO) {
        database.setCalendarVisibility(calendarId, visible)
        refreshFromDatabase()
        onDataMutated()
    }

    suspend fun updateCalendarColor(calendarId: String, colorArgb: Int) = withContext(Dispatchers.IO) {
        val cal = database.getCalendars().firstOrNull { it.id == calendarId } ?: return@withContext
        database.upsertCalendar(cal.copy(colorArgb = colorArgb))
        refreshFromDatabase()
        onDataMutated()
    }

    suspend fun deleteLocalCalendar(calendarId: String) = withContext(Dispatchers.IO) {
        val cal = database.getCalendars().firstOrNull { it.id == calendarId } ?: return@withContext
        if (cal.accountType == CalendarAccountType.LOCAL && !cal.isDefault) {
            database.deleteCalendar(calendarId)
            refreshFromDatabase()
            onDataMutated()
        }
    }

    suspend fun addCalDavAccount(
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        colorArgb: Int
    ): CalendarAccount = withContext(Dispatchers.IO) {
        val accId = "caldav_${UUID.randomUUID().toString().take(8)}"
        val cleanUrl = serverUrl.trim()
        val account = CalendarAccount(
            id = accId,
            name = name.trim().ifBlank { "CalDAV" },
            emailOrUser = username.trim(),
            accountType = CalendarAccountType.CALDAV,
            serverUrl = cleanUrl,
            enabled = true,
            syncStatusMessage = "Bereit für Sync"
        )
        secretVault.putSecret(secretVault.calDavPasswordKey(accId), password)
        database.upsertAccount(account)
        database.upsertCalendar(
            CalendarSource(
                id = "cal_$accId",
                accountId = accId,
                name = account.name,
                colorArgb = colorArgb,
                accountType = CalendarAccountType.CALDAV,
                isVisible = true,
                isReadOnly = false,
                syncUrl = cleanUrl
            )
        )
        refreshFromDatabase()
        account
    }

    suspend fun addIcsSubscription(
        name: String,
        url: String,
        colorArgb: Int
    ): CalendarAccount = withContext(Dispatchers.IO) {
        val accId = "ics_${UUID.randomUUID().toString().take(8)}"
        val cleanUrl = url.trim()
        val account = CalendarAccount(
            id = accId,
            name = name.trim().ifBlank { "ICS-Abo" },
            emailOrUser = cleanUrl,
            accountType = CalendarAccountType.ICS_SUBSCRIPTION,
            serverUrl = cleanUrl,
            enabled = true,
            syncStatusMessage = "Bereit für Abruf"
        )
        database.upsertAccount(account)
        database.upsertCalendar(
            CalendarSource(
                id = "cal_$accId",
                accountId = accId,
                name = account.name,
                colorArgb = colorArgb,
                accountType = CalendarAccountType.ICS_SUBSCRIPTION,
                isVisible = true,
                isReadOnly = true,
                syncUrl = cleanUrl
            )
        )
        refreshFromDatabase()
        account
    }

    suspend fun addGoogleApiAccount(
        name: String,
        email: String,
        oauthAccessToken: String
    ): CalendarAccount = withContext(Dispatchers.IO) {
        val accId = "gapi_${UUID.randomUUID().toString().take(8)}"
        val account = CalendarAccount(
            id = accId,
            name = name.trim().ifBlank { "Google API ($email)" },
            emailOrUser = email.trim(),
            accountType = CalendarAccountType.GOOGLE_API,
            serverUrl = "https://www.googleapis.com/calendar/v3",
            enabled = true,
            syncStatusMessage = "Token gespeichert · bereit"
        )
        secretVault.putSecret(secretVault.googleTokenKey(accId), oauthAccessToken.trim())
        database.upsertAccount(account)
        refreshFromDatabase()
        account
    }

    suspend fun removeAccount(accountId: String) = withContext(Dispatchers.IO) {
        if (accountId == "local_device") return@withContext
        secretVault.removeSecret(secretVault.calDavPasswordKey(accountId))
        secretVault.removeSecret(secretVault.googleTokenKey(accountId))
        database.deleteAccount(accountId)
        refreshFromDatabase()
        onDataMutated()
    }

    suspend fun importIcsIntoCalendar(icsContent: String, calendarId: String): Int = withContext(Dispatchers.IO) {
        val parsed = IcsCodec.parseIcs(icsContent, calendarId)
        if (parsed.isNotEmpty()) {
            database.upsertEventsBatch(parsed)
            refreshFromDatabase()
            onDataMutated()
        }
        parsed.size
    }

    suspend fun exportCalendarToIcs(calendarId: String): String = withContext(Dispatchers.IO) {
        val cal = database.getCalendars().firstOrNull { it.id == calendarId }
        val events = database.getEventsByCalendar(calendarId, includeDeleted = false)
        IcsCodec.exportToIcs(events, cal?.name ?: "BttrCal")
    }

    fun defaultWritableCalendarId(): String {
        val list = _calendars.value
        return list.firstOrNull { !it.isReadOnly && it.isDefault }?.id
            ?: list.firstOrNull { !it.isReadOnly }?.id
            ?: "cal_local_private"
    }
}
