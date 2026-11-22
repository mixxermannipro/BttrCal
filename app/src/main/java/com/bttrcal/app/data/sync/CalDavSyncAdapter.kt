package com.bttrcal.app.data.sync

import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.data.security.SecretVault
import com.bttrcal.app.domain.engine.IcsCodec
import com.bttrcal.app.domain.model.CalendarAccountType
import com.bttrcal.app.domain.model.SyncState
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

class CalDavSyncAdapter(
    private val database: BttrCalDatabase,
    private val secretVault: SecretVault,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    fun syncAllCalDavAccounts(): List<SyncAdapterResult> {
        val accounts = database.getAccounts()
            .filter { it.enabled && it.accountType == CalendarAccountType.CALDAV }
        if (accounts.isEmpty()) return emptyList()

        val calendarsByAccount = database.getCalendars().groupBy { it.accountId }
        val results = mutableListOf<SyncAdapterResult>()

        for (account in accounts) {
            val password = secretVault.getSecret(secretVault.calDavPasswordKey(account.id))
            if (password.isNullOrBlank()) {
                results += SyncAdapterResult(
                    sourceLabel = "CalDAV: ${account.name}",
                    success = false,
                    message = "Passwort/App-Passwort fehlt"
                )
                continue
            }

            val credential = Credentials.basic(account.emailOrUser, password)
            val calendar = calendarsByAccount[account.id]?.firstOrNull()
            val calUrl = (calendar?.syncUrl ?: account.serverUrl).trimEnd('/') + "/"

            val res = runCatching {
                var pushed = 0
                if (calendar != null) {
                    val pending = database.getEventsByCalendar(calendar.id, includeDeleted = true)
                        .filter { it.syncState != SyncState.SYNCED }
                    for (ev in pending) {
                        val resourceUrl = ev.remoteId?.takeIf { it.startsWith("http") }
                            ?: "${calUrl}${ev.uid}.ics"
                        when (ev.syncState) {
                            SyncState.PENDING_CREATE, SyncState.PENDING_UPDATE -> {
                                val icsBody = IcsCodec.exportToIcs(listOf(ev), calendar.name)
                                val putReq = Request.Builder()
                                    .url(resourceUrl)
                                    .header("Authorization", credential)
                                    .put(icsBody.toRequestBody("text/calendar; charset=utf-8".toMediaType()))
                                    .build()
                                httpClient.newCall(putReq).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val etag = resp.header("ETag")
                                        database.upsertEvent(
                                            ev.copy(
                                                remoteId = resourceUrl,
                                                etag = etag ?: ev.etag,
                                                syncState = SyncState.SYNCED
                                            )
                                        )
                                        pushed++
                                    }
                                }
                            }
                            SyncState.PENDING_DELETE -> {
                                val delReq = Request.Builder()
                                    .url(resourceUrl)
                                    .header("Authorization", credential)
                                    .delete()
                                    .build()
                                httpClient.newCall(delReq).execute().use { resp ->
                                    if (resp.isSuccessful || resp.code == 404) {
                                        database.hardDeleteEvent(ev.id)
                                        pushed++
                                    }
                                }
                            }
                            SyncState.SYNCED -> Unit
                        }
                    }
                }

                // REPORT calendar-query to pull events from CalDAV collection
                val reportXml = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                      <D:prop>
                        <D:getetag />
                        <C:calendar-data />
                      </D:prop>
                      <C:filter>
                        <C:comp-filter name="VCALENDAR">
                          <C:comp-filter name="VEVENT" />
                        </C:comp-filter>
                      </C:filter>
                    </C:calendar-query>
                """.trimIndent()

                val reportReq = Request.Builder()
                    .url(calUrl)
                    .header("Authorization", credential)
                    .header("Depth", "1")
                    .method("REPORT", reportXml.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                    .build()

                var pulled = 0
                val activeKeys = mutableSetOf<String>()
                val targetCalId = calendar?.id ?: "caldav_${account.id}"

                httpClient.newCall(reportReq).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 207) {
                        error("Server antwortete mit HTTP ${resp.code}")
                    }
                    val xml = resp.body?.string().orEmpty()
                    val blocks = extractCalendarBlocks(xml, calUrl)
                    val parsedAll = blocks.flatMap { (href, icsData) ->
                        val resolvedUrl = resolveHref(calUrl, href)
                        IcsCodec.parseIcs(icsData, targetCalId).map { ev ->
                            val key = resolvedUrl.ifBlank { ev.uid }
                            activeKeys += key
                            ev.copy(remoteId = key, syncState = SyncState.SYNCED)
                        }
                    }
                    database.upsertEventsBatch(parsedAll)
                    if (parsedAll.isNotEmpty()) {
                        database.deleteSyncedEventsMissingFrom(targetCalId, activeKeys)
                    }
                    pulled = parsedAll.size
                }

                database.upsertAccount(
                    account.copy(
                        lastSyncedAt = System.currentTimeMillis(),
                        syncStatusMessage = "$pulled geladen · $pushed gesendet"
                    )
                )

                SyncAdapterResult(
                    sourceLabel = "CalDAV: ${account.name}",
                    success = true,
                    pulledCount = pulled,
                    pushedCount = pushed,
                    message = "$pulled geladen, $pushed gesendet"
                )
            }.getOrElse { ex ->
                database.upsertAccount(account.copy(syncStatusMessage = "Fehler: ${ex.message}"))
                SyncAdapterResult(
                    sourceLabel = "CalDAV: ${account.name}",
                    success = false,
                    message = ex.message ?: "CalDAV-Fehler"
                )
            }
            results += res
        }

        return results
    }

    private fun extractCalendarBlocks(xml: String, fallbackHref: String): List<Pair<String, String>> {
        val responseRegex = Regex(
            """<(?:\w+:)?response\b[^>]*>(.*?)</(?:\w+:)?response>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val hrefRegex = Regex("""<(?:\w+:)?href\b[^>]*>(.*?)</(?:\w+:)?href>""", RegexOption.DOT_MATCHES_ALL)
        val calDataRegex = Regex(
            """<(?:\w+:)?calendar-data\b[^>]*>(.*?)</(?:\w+:)?calendar-data>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        val blocks = mutableListOf<Pair<String, String>>()
        for (match in responseRegex.findAll(xml)) {
            val chunk = match.groupValues[1]
            val href = hrefRegex.find(chunk)?.groupValues?.get(1)?.trim() ?: fallbackHref
            val rawCal = calDataRegex.find(chunk)?.groupValues?.get(1)?.trim() ?: continue
            val unescaped = rawCal
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
            if (unescaped.contains("BEGIN:VEVENT", ignoreCase = true)) {
                blocks += href to unescaped
            }
        }
        return blocks
    }

    private fun resolveHref(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return runCatching { URI(baseUrl).resolve(href).toString() }.getOrDefault(href)
    }
}
