package com.bttrcal.app.data.sync

import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.domain.engine.IcsCodec
import com.bttrcal.app.domain.model.CalendarAccountType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class IcsSubscriptionSyncAdapter(
    private val database: BttrCalDatabase,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    fun syncAllSubscriptions(): List<SyncAdapterResult> {
        val accounts = database.getAccounts()
            .filter { it.enabled && it.accountType == CalendarAccountType.ICS_SUBSCRIPTION }
        if (accounts.isEmpty()) return emptyList()

        val calendarsByAccount = database.getCalendars().groupBy { it.accountId }
        val results = mutableListOf<SyncAdapterResult>()

        for (account in accounts) {
            val cal = calendarsByAccount[account.id]?.firstOrNull()
            val rawUrl = (cal?.syncUrl ?: account.serverUrl).trim()
            if (rawUrl.isBlank()) continue
            val httpUrl = if (rawUrl.startsWith("webcal://", ignoreCase = true)) {
                "https://" + rawUrl.substringAfter("://")
            } else {
                rawUrl
            }

            val res = runCatching {
                val request = Request.Builder()
                    .url(httpUrl)
                    .header("User-Agent", "BttrCal/1.0 (Android)")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    if (!body.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                        error("Keine gültige ICS-Datei")
                    }
                    val targetCalId = cal?.id ?: "ics_${account.id}"
                    val parsed = IcsCodec.parseIcs(body, targetCalId)
                    database.upsertEventsBatch(parsed)
                    val activeUids = parsed.map { it.remoteId ?: it.uid }.toSet()
                    database.deleteSyncedEventsMissingFrom(targetCalId, activeUids)
                    database.upsertAccount(
                        account.copy(
                            lastSyncedAt = System.currentTimeMillis(),
                            syncStatusMessage = "${parsed.size} Termine aktualisiert"
                        )
                    )
                    SyncAdapterResult(
                        sourceLabel = "ICS: ${account.name}",
                        success = true,
                        pulledCount = parsed.size,
                        message = "${parsed.size} Termine aus Abo geladen"
                    )
                }
            }.getOrElse { ex ->
                database.upsertAccount(
                    account.copy(syncStatusMessage = "Fehler: ${ex.message}")
                )
                SyncAdapterResult(
                    sourceLabel = "ICS: ${account.name}",
                    success = false,
                    message = ex.message ?: "Download fehlgeschlagen"
                )
            }
            results += res
        }
        return results
    }
}
