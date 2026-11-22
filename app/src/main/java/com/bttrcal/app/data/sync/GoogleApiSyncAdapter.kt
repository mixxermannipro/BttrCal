package com.bttrcal.app.data.sync

import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.data.security.SecretVault
import com.bttrcal.app.domain.model.CalendarAccountType
import com.bttrcal.app.domain.model.CalendarColors
import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.CalendarSource
import com.bttrcal.app.domain.model.EventAvailability
import com.bttrcal.app.domain.model.SyncState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class GoogleApiSyncAdapter(
    private val database: BttrCalDatabase,
    private val secretVault: SecretVault,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    fun syncAllGoogleApiAccounts(): List<SyncAdapterResult> {
        val accounts = database.getAccounts()
            .filter { it.enabled && it.accountType == CalendarAccountType.GOOGLE_API }
        if (accounts.isEmpty()) return emptyList()

        val results = mutableListOf<SyncAdapterResult>()
        for (account in accounts) {
            val token = secretVault.getSecret(secretVault.googleTokenKey(account.id))
            if (token.isNullOrBlank()) {
                results += SyncAdapterResult(
                    sourceLabel = "Google API: ${account.name}",
                    success = false,
                    message = "OAuth Access Token fehlt"
                )
                continue
            }

            val res = runCatching {
                var pulled = 0
                var pushed = 0

                val calListReq = Request.Builder()
                    .url("$BASE_URL/users/me/calendarList")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                val syncedCalendars = mutableListOf<CalendarSource>()
                val existingById = database.getCalendars().associateBy { it.id }

                httpClient.newCall(calListReq).execute().use { resp ->
                    if (!resp.isSuccessful) error("Google API HTTP ${resp.code}")
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val items = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val obj = items.getJSONObject(i)
                        val remoteCalId = obj.getString("id")
                        val localId = "gapi_${account.id}_${remoteCalId.hashCode()}"
                        val summary = obj.optString("summary", remoteCalId)
                        val accessRole = obj.optString("accessRole", "reader")
                        val readOnly = accessRole == "reader" || accessRole == "freeBusyReader"
                        val primary = obj.optBoolean("primary", false)
                        val bgHex = obj.optString("backgroundColor", "")
                        val colorArgb = parseHexColorOrDefault(bgHex, CalendarColors.palette[i % CalendarColors.palette.size])
                        val prev = existingById[localId]

                        val src = CalendarSource(
                            id = localId,
                            accountId = account.id,
                            name = summary,
                            colorArgb = prev?.colorArgb ?: colorArgb,
                            accountType = CalendarAccountType.GOOGLE_API,
                            isVisible = prev?.isVisible ?: true,
                            isReadOnly = readOnly,
                            isDefault = prev?.isDefault ?: primary,
                            remoteId = remoteCalId
                        )
                        database.upsertCalendar(src)
                        syncedCalendars += src
                    }
                }

                // Push pending edits
                for (cal in syncedCalendars) {
                    if (cal.isReadOnly) continue
                    val remoteCalId = URLEncoder.encode(cal.remoteId ?: continue, "UTF-8")
                    val pending = database.getEventsByCalendar(cal.id, includeDeleted = true)
                        .filter { it.syncState != SyncState.SYNCED }

                    for (ev in pending) {
                        when (ev.syncState) {
                            SyncState.PENDING_CREATE -> {
                                val req = Request.Builder()
                                    .url("$BASE_URL/calendars/$remoteCalId/events")
                                    .header("Authorization", "Bearer $token")
                                    .post(eventToGoogleJson(ev).toString().toRequestBody(JSON_MEDIA))
                                    .build()
                                httpClient.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val created = JSONObject(resp.body?.string().orEmpty())
                                        database.upsertEvent(
                                            ev.copy(
                                                remoteId = created.optString("id", ev.remoteId),
                                                etag = created.optString("etag", ""),
                                                syncState = SyncState.SYNCED
                                            )
                                        )
                                        pushed++
                                    }
                                }
                            }
                            SyncState.PENDING_UPDATE -> {
                                val rId = ev.remoteId ?: continue
                                val req = Request.Builder()
                                    .url("$BASE_URL/calendars/$remoteCalId/events/${URLEncoder.encode(rId, "UTF-8")}")
                                    .header("Authorization", "Bearer $token")
                                    .patch(eventToGoogleJson(ev).toString().toRequestBody(JSON_MEDIA))
                                    .build()
                                httpClient.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        database.upsertEvent(ev.copy(syncState = SyncState.SYNCED))
                                        pushed++
                                    }
                                }
                            }
                            SyncState.PENDING_DELETE -> {
                                val rId = ev.remoteId
                                if (rId != null) {
                                    val req = Request.Builder()
                                        .url("$BASE_URL/calendars/$remoteCalId/events/${URLEncoder.encode(rId, "UTF-8")}")
                                        .header("Authorization", "Bearer $token")
                                        .delete()
                                        .build()
                                    httpClient.newCall(req).execute().close()
                                    pushed++
                                }
                                database.hardDeleteEvent(ev.id)
                            }
                            SyncState.SYNCED -> Unit
                        }
                    }
                }

                // Pull events
                val now = Instant.now()
                val timeMin = DateTimeFormatter.ISO_INSTANT.format(now.minusSeconds(90L * 86400L))
                val timeMax = DateTimeFormatter.ISO_INSTANT.format(now.plusSeconds(365L * 86400L))

                for (cal in syncedCalendars) {
                    val remoteCalId = URLEncoder.encode(cal.remoteId ?: continue, "UTF-8")
                    val url = "$BASE_URL/calendars/$remoteCalId/events?singleEvents=false&maxResults=500&timeMin=$timeMin&timeMax=$timeMax"
                    val req = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()

                    val activeIds = mutableSetOf<String>()
                    val batch = mutableListOf<CalendarEvent>()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("Google Events HTTP ${resp.code}")
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val items = json.optJSONArray("items") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val obj = items.getJSONObject(i)
                            if (obj.optString("status") == "cancelled") continue
                            val gId = obj.getString("id")
                            activeIds += gId

                            val startObj = obj.optJSONObject("start") ?: continue
                            val endObj = obj.optJSONObject("end") ?: continue
                            val allDay = startObj.has("date")
                            val zone = ZoneId.systemDefault()
                            val startMillis = if (allDay) {
                                LocalDate.parse(startObj.getString("date")).atStartOfDay(zone).toInstant().toEpochMilli()
                            } else {
                                OffsetDateTime.parse(startObj.getString("dateTime")).toInstant().toEpochMilli()
                            }
                            val endMillis = if (allDay) {
                                LocalDate.parse(endObj.getString("date")).atStartOfDay(zone).toInstant().toEpochMilli()
                            } else {
                                OffsetDateTime.parse(endObj.getString("dateTime")).toInstant().toEpochMilli()
                            }

                            val recArr = obj.optJSONArray("recurrence")
                            var rrule: String? = null
                            if (recArr != null) {
                                for (r in 0 until recArr.length()) {
                                    val line = recArr.getString(r)
                                    if (line.startsWith("RRULE:", ignoreCase = true)) {
                                        rrule = line.removePrefix("RRULE:").trim()
                                    }
                                }
                            }

                            val avail = if (obj.optString("transparency") == "transparent") {
                                EventAvailability.FREE
                            } else {
                                EventAvailability.BUSY
                            }

                            batch += CalendarEvent(
                                id = "${cal.id}_$gId",
                                uid = obj.optString("iCalUID", UUID.randomUUID().toString()),
                                calendarId = cal.id,
                                title = obj.optString("summary", "Ohne Titel"),
                                description = obj.optString("description", ""),
                                location = obj.optString("location", ""),
                                url = obj.optString("htmlLink", ""),
                                startMillis = startMillis,
                                endMillis = endMillis,
                                allDay = allDay,
                                timeZoneId = startObj.optString("timeZone", zone.id),
                                rrule = rrule,
                                availability = avail,
                                remoteId = gId,
                                etag = obj.optString("etag", ""),
                                syncState = SyncState.SYNCED,
                                updatedAt = System.currentTimeMillis(),
                                createdAt = System.currentTimeMillis()
                            )
                            pulled++
                        }
                    }
                    database.upsertEventsBatch(batch)
                    database.deleteSyncedEventsMissingFrom(cal.id, activeIds)
                }

                database.upsertAccount(
                    account.copy(
                        lastSyncedAt = System.currentTimeMillis(),
                        syncStatusMessage = "$pulled geladen · $pushed gesendet"
                    )
                )

                SyncAdapterResult(
                    sourceLabel = "Google API: ${account.name}",
                    success = true,
                    pulledCount = pulled,
                    pushedCount = pushed,
                    message = "${syncedCalendars.size} Kalender · $pulled geladen, $pushed gesendet"
                )
            }.getOrElse { ex ->
                database.upsertAccount(account.copy(syncStatusMessage = "Fehler: ${ex.message}"))
                SyncAdapterResult(
                    sourceLabel = "Google API: ${account.name}",
                    success = false,
                    message = ex.message ?: "Google API Fehler"
                )
            }
            results += res
        }
        return results
    }

    private fun eventToGoogleJson(ev: CalendarEvent): JSONObject {
        val zone = runCatching { ZoneId.of(ev.timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val startJson = JSONObject()
        val endJson = JSONObject()
        if (ev.allDay) {
            startJson.put("date", Instant.ofEpochMilli(ev.startMillis).atZone(zone).toLocalDate().toString())
            endJson.put("date", Instant.ofEpochMilli(ev.endMillis).atZone(zone).toLocalDate().toString())
        } else {
            startJson.put("dateTime", Instant.ofEpochMilli(ev.startMillis).atOffset(ZoneOffset.UTC).toString())
            startJson.put("timeZone", ev.timeZoneId)
            endJson.put("dateTime", Instant.ofEpochMilli(ev.endMillis).atOffset(ZoneOffset.UTC).toString())
            endJson.put("timeZone", ev.timeZoneId)
        }
        return JSONObject().apply {
            put("summary", ev.title)
            if (ev.description.isNotBlank()) put("description", ev.description)
            if (ev.location.isNotBlank()) put("location", ev.location)
            put("start", startJson)
            put("end", endJson)
            put("transparency", if (ev.availability == EventAvailability.FREE) "transparent" else "opaque")
            if (!ev.rrule.isNullOrBlank()) {
                put("recurrence", JSONArray(listOf("RRULE:${ev.rrule.removePrefix("RRULE:")}")))
            }
        }
    }

    private fun parseHexColorOrDefault(hex: String, fallback: Int): Int {
        if (!hex.startsWith("#")) return fallback
        return runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(fallback)
    }

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/calendar/v3"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
