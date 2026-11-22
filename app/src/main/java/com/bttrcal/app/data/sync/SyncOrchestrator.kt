package com.bttrcal.app.data.sync

import android.content.Context
import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.data.local.PreferencesRepository
import com.bttrcal.app.data.security.SecretVault
import com.bttrcal.app.domain.model.SyncLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class SyncOrchestrator(
    private val context: Context,
    private val database: BttrCalDatabase,
    private val preferences: PreferencesRepository,
    secretVault: SecretVault,
    private val onSyncFinished: () -> Unit
) {
    private val androidAdapter = AndroidCalendarSyncAdapter(context, database)
    private val calDavAdapter = CalDavSyncAdapter(database, secretVault)
    private val icsAdapter = IcsSubscriptionSyncAdapter(database)
    private val googleApiAdapter = GoogleApiSyncAdapter(database, secretVault)
    private val mutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    suspend fun syncAll(): List<SyncAdapterResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            _isSyncing.value = true
            try {
                val results = mutableListOf<SyncAdapterResult>()

                if (androidAdapter.hasReadPermission()) {
                    results += runCatching { androidAdapter.sync() }.getOrElse {
                        SyncAdapterResult("Google & System-Kalender", false, message = it.message ?: "Fehler")
                    }
                }
                results += calDavAdapter.syncAllCalDavAccounts()
                results += icsAdapter.syncAllSubscriptions()
                results += googleApiAdapter.syncAllGoogleApiAccounts()

                val now = System.currentTimeMillis()
                results.forEach { res ->
                    database.insertSyncLog(
                        SyncLogEntry(
                            id = UUID.randomUUID().toString(),
                            timestamp = now,
                            sourceLabel = res.sourceLabel,
                            success = res.success,
                            pulledCount = res.pulledCount,
                            pushedCount = res.pushedCount,
                            message = res.message
                        )
                    )
                }

                val summary = if (results.isEmpty()) {
                    "Nur lokale Kalender aktiv"
                } else {
                    val ok = results.count { it.success }
                    val totalPulled = results.sumOf { it.pulledCount }
                    val totalPushed = results.sumOf { it.pushedCount }
                    "$ok/${results.size} Quellen OK · $totalPulled geladen, $totalPushed gesendet"
                }
                preferences.recordSyncResult(now, summary)
                onSyncFinished()
                results
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
