package com.bttrcal.app.domain.engine

import com.bttrcal.app.domain.model.CalendarEvent
import com.bttrcal.app.domain.model.EventOccurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DuplicateCluster(
    val id: String,
    val occurrences: List<EventOccurrence>,
    val suggestedTitle: String,
    val reasonDe: String,
    val similarityScore: Double,
    val spansMultipleCalendars: Boolean
)

data class MergePlan(
    val mergedEvent: CalendarEvent,
    val eventIdsToDelete: List<String>
)

object DuplicateDetector {

    private val DAY_FMT = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

    private val STOP_WORDS = setOf(
        "der", "die", "das", "und", "oder", "mit", "von", "vom", "zum", "zur",
        "am", "im", "in", "an", "auf", "ein", "eine", "einen", "einem", "einer",
        "fuer", "fur", "bei", "nach", "vor", "hat", "wird", "ist", "des", "dem", "den"
    )

    private val GENERIC_SINGLE_TOKENS = setOf(
        "termin", "meeting", "treffen", "anruf", "telefonat", "event", "erinnerung",
        "notiz", "pause", "arbeit", "schule", "uni", "training", "sport"
    )

    internal data class NormalizedTitle(
        val fullNormalized: String,
        val concatenated: String,
        val coreTokens: List<String>,
        val numbers: Set<String>
    )

    internal fun normalizeTitle(raw: String): NormalizedTitle {
        val folded = raw.lowercase(Locale.GERMAN)
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace("'", " ")
            .replace("’", " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val allTokens = folded.split(' ').filter { it.isNotBlank() }
        val nums = allTokens.filter { tok -> tok.all { ch -> ch.isDigit() } }.toSet()
        val core = allTokens.filter { tok ->
            tok !in STOP_WORDS && !tok.all { ch -> ch.isDigit() }
        }.ifEmpty { allTokens }

        val concat = core.joinToString("")
        return NormalizedTitle(
            fullNormalized = folded,
            concatenated = concat,
            coreTokens = core,
            numbers = nums
        )
    }

    fun detectDuplicates(
        occurrences: List<EventOccurrence>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DuplicateCluster> {
        if (occurrences.size < 2) return emptyList()

        // Group occurrences by LocalDate (each day slice checks pairs of distinct underlying events).
        val byDate = mutableMapOf<LocalDate, MutableList<EventOccurrence>>()
        for (occ in occurrences) {
            val startDate = Instant.ofEpochMilli(occ.occurrenceStartMillis).atZone(zoneId).toLocalDate()
            byDate.getOrPut(startDate) { mutableListOf() }.add(occ)
        }

        val representativeByEventId = linkedMapOf<String, EventOccurrence>()
        for (occ in occurrences.sortedBy { it.occurrenceStartMillis }) {
            representativeByEventId.putIfAbsent(occ.event.id, occ)
        }

        val eventIds = representativeByEventId.keys.toList()
        if (eventIds.size < 2) return emptyList()

        val normByEventId = representativeByEventId.mapValues { (_, occ) ->
            normalizeTitle(occ.event.title)
        }

        val indexByEventId = eventIds.withIndex().associate { it.value to it.index }
        val parent = IntArray(eventIds.size) { it }

        fun find(x: Int): Int {
            var cur = x
            while (parent[cur] != cur) {
                parent[cur] = parent[parent[cur]]
                cur = parent[cur]
            }
            return cur
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val pairBestScores = mutableMapOf<Pair<Int, Int>, Double>()
        val pairMatchContext = mutableMapOf<Pair<Int, Int>, Pair<LocalDate, Boolean>>()

        for ((date, dayOccs) in byDate) {
            if (dayOccs.size < 2) continue
            for (i in dayOccs.indices) {
                val occA = dayOccs[i]
                for (j in (i + 1) until dayOccs.size) {
                    val occB = dayOccs[j]
                    if (occA.event.id == occB.event.id) continue
                    if (!areTemporallyDuplicate(occA, occB)) continue

                    val normA = normByEventId[occA.event.id] ?: continue
                    val normB = normByEventId[occB.event.id] ?: continue
                    val score = computeTitleSimilarity(normA, normB)
                    if (score >= 0.72) {
                        val idxA = indexByEventId[occA.event.id] ?: continue
                        val idxB = indexByEventId[occB.event.id] ?: continue
                        val key = if (idxA < idxB) idxA to idxB else idxB to idxA
                        val prevScore = pairBestScores[key] ?: 0.0
                        if (score > prevScore) {
                            pairBestScores[key] = score
                            val bothAllDay = occA.event.allDay && occB.event.allDay
                            pairMatchContext[key] = date to bothAllDay
                        }
                        union(idxA, idxB)
                    }
                }
            }
        }

        if (pairBestScores.isEmpty()) return emptyList()

        val groups = mutableMapOf<Int, MutableList<Int>>()
        for ((idxA, idxB) in pairBestScores.keys) {
            val root = find(idxA)
            groups.getOrPut(root) { mutableListOf() }.apply {
                if (idxA !in this) add(idxA)
                if (idxB !in this) add(idxB)
            }
        }

        val clusters = mutableListOf<DuplicateCluster>()
        for (memberIndices in groups.values) {
            val distinctIndices = memberIndices.distinct().sorted()
            if (distinctIndices.size < 2) continue

            val memberOccs = distinctIndices.map { representativeByEventId.getValue(eventIds[it]) }
                .sortedWith(compareBy<EventOccurrence> { it.occurrenceStartMillis }.thenBy { it.calendarName })

            var maxScore = 0.0
            var anyDate: LocalDate? = null
            var allAllDay = true
            for (a in distinctIndices) {
                for (b in distinctIndices) {
                    if (a >= b) continue
                    val score = pairBestScores[a to b] ?: continue
                    if (score > maxScore) {
                        maxScore = score
                        val ctx = pairMatchContext[a to b]
                        anyDate = ctx?.first
                        allAllDay = ctx?.second ?: false
                    }
                }
            }

            val calIds = memberOccs.map { it.event.calendarId }.distinct()
            val spansMulti = calIds.size > 1
            val suggestedTitle = chooseBestTitle(memberOccs)

            val firstZdt = Instant.ofEpochMilli(memberOccs.first().occurrenceStartMillis).atZone(zoneId)
            val dateLabel = (anyDate ?: firstZdt.toLocalDate()).format(DAY_FMT)
            val timePart = if (allAllDay || memberOccs.any { it.event.allDay }) {
                "am $dateLabel"
            } else {
                "am $dateLabel um ${firstZdt.format(TIME_FMT)}"
            }
            val calPart = if (spansMulti) "über ${calIds.size} Kalender" else "im selben Kalender"
            val reason = "${memberOccs.size}× $timePart $calPart"

            clusters += DuplicateCluster(
                id = memberOccs.joinToString("|") { it.event.id }.let { "dup_${it.hashCode()}" },
                occurrences = memberOccs,
                suggestedTitle = suggestedTitle,
                reasonDe = reason,
                similarityScore = maxScore,
                spansMultipleCalendars = spansMulti
            )
        }

        return clusters.sortedWith(
            compareByDescending<DuplicateCluster> { it.occurrences.size }
                .thenBy { it.occurrences.first().occurrenceStartMillis }
        )
    }

    fun buildMergePlan(
        cluster: DuplicateCluster,
        targetCalendarId: String,
        mergedTitle: String
    ): MergePlan {
        val events = cluster.occurrences.map { it.event }
        val existingInTarget = events.firstOrNull { it.calendarId == targetCalendarId }
        val richest = events.maxByOrNull { scoreRichness(it) } ?: events.first()
        val base = existingInTarget ?: richest

        val cleanTitle = mergedTitle.trim().ifBlank { cluster.suggestedTitle.ifBlank { base.title } }
        val mergedDesc = events.map { it.description.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n---\n")
        val mergedLocation = events.firstOrNull { it.location.isNotBlank() }?.location?.trim().orEmpty()
        val mergedUrl = events.firstOrNull { it.url.isNotBlank() }?.url?.trim().orEmpty()
        val mergedReminders = events.flatMap { it.reminderMinutes }.distinct().sorted().ifEmpty { listOf(15) }
        val mergedRRule = events.firstOrNull { !it.rrule.isNullOrBlank() }?.rrule
        val mergedAttendees = events.map { it.attendeesSummary.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val now = System.currentTimeMillis()
        val mergedEvent = if (existingInTarget != null) {
            existingInTarget.copy(
                title = cleanTitle,
                description = mergedDesc,
                location = mergedLocation,
                url = mergedUrl,
                reminderMinutes = mergedReminders,
                rrule = mergedRRule,
                attendeesSummary = mergedAttendees,
                updatedAt = now
            )
        } else {
            base.copy(
                id = "evt_${UUID.randomUUID()}",
                uid = UUID.randomUUID().toString(),
                calendarId = targetCalendarId,
                title = cleanTitle,
                description = mergedDesc,
                location = mergedLocation,
                url = mergedUrl,
                reminderMinutes = mergedReminders,
                rrule = mergedRRule,
                attendeesSummary = mergedAttendees,
                androidEventId = null,
                remoteId = null,
                etag = null,
                createdAt = now,
                updatedAt = now
            )
        }

        val toDelete = if (existingInTarget != null) {
            events.map { it.id }.filter { it != existingInTarget.id }
        } else {
            events.map { it.id }
        }

        return MergePlan(mergedEvent = mergedEvent, eventIdsToDelete = toDelete)
    }

    private fun scoreRichness(ev: CalendarEvent): Int {
        var s = ev.title.length
        if (ev.description.isNotBlank()) s += 40 + ev.description.length
        if (ev.location.isNotBlank()) s += 30
        if (!ev.rrule.isNullOrBlank()) s += 25
        if (ev.url.isNotBlank()) s += 15
        if (ev.attendeesSummary.isNotBlank()) s += 20
        return s
    }

    private fun chooseBestTitle(occs: List<EventOccurrence>): String {
        val ranked = occs.map { it.event.title.trim() }.filter { it.isNotBlank() }
        // Prefer titles with more meaningful detail (e.g. "Manuels 18. Geburtstag" or full name), avoiding "(Kopie)" suffixes.
        return ranked.maxByOrNull { title ->
            var score = title.length
            if (title.contains("kopie", ignoreCase = true)) score -= 50
            score
        } ?: occs.first().event.title
    }

    private fun areTemporallyDuplicate(a: EventOccurrence, b: EventOccurrence): Boolean {
        val evA = a.event
        val evB = b.event
        if (evA.allDay || evB.allDay) {
            // Same start date already guaranteed by date bucket; both all-day or one all-day birthday on the same day.
            return true
        }
        val startDiffMin = abs(a.occurrenceStartMillis - b.occurrenceStartMillis) / 60_000L
        if (startDiffMin <= 30L) return true
        // Or overlapping timed intervals on the same day.
        val overlap = min(a.occurrenceEndMillis, b.occurrenceEndMillis) - max(a.occurrenceStartMillis, b.occurrenceStartMillis)
        return overlap > 0L
    }

    internal fun computeTitleSimilarity(a: NormalizedTitle, b: NormalizedTitle): Double {
        if (a.fullNormalized.isBlank() || b.fullNormalized.isBlank()) return 0.0
        if (a.fullNormalized == b.fullNormalized) return 1.0
        // If both titles carry explicit numbers that differ (e.g. "Teil 1" vs "Teil 2"), keep them distinct.
        if (a.numbers.isNotEmpty() && b.numbers.isNotEmpty() && a.numbers != b.numbers) return 0.25
        if (a.concatenated.isNotBlank() && a.concatenated == b.concatenated) return 0.98

        val tokensA = a.coreTokens
        val tokensB = b.coreTokens
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val (smaller, larger) = if (tokensA.size <= tokensB.size) tokensA to tokensB else tokensB to tokensA

        // Count how many tokens in smaller have an equivalent match in larger (1-to-1 greedy).
        val used = BooleanArray(larger.size)
        var matched = 0
        for (tSmall in smaller) {
            val matchIdx = larger.indices.firstOrNull { idx ->
                !used[idx] && tokensEquivalent(tSmall, larger[idx])
            }
            if (matchIdx != null) {
                used[matchIdx] = true
                matched++
            }
        }

        // Exact or fuzzy subset containment:
        // E.g. ["manuel", "geburtstag"] is completely contained in ["manuels", "geburtstag", "nachname"].
        if (matched == smaller.size) {
            val allowSingleTokenSubset = smaller.size == 1 &&
                smaller.first().length >= 5 &&
                smaller.first() !in GENERIC_SINGLE_TOKENS &&
                larger.size <= 4
            if (smaller.size >= 2 || allowSingleTokenSubset) {
                val coveragePenalty = (larger.size - smaller.size) * 0.03
                return (0.93 - coveragePenalty).coerceAtLeast(0.76)
            }
        }

        // Jaccard token similarity
        val unionCount = tokensA.size + tokensB.size - matched
        val jaccard = if (unionCount > 0) matched.toDouble() / unionCount.toDouble() else 0.0

        // Character-level normalized Damerau-Levenshtein similarity
        val maxLen = max(a.concatenated.length, b.concatenated.length).coerceAtLeast(1)
        val dist = damerauLevenshtein(a.concatenated, b.concatenated)
        val charSim = 1.0 - (dist.toDouble() / maxLen.toDouble())

        return max(jaccard, charSim)
    }

    internal fun tokensEquivalent(t1: String, t2: String): Boolean {
        if (t1 == t2) return true
        // German possessive trailing -s / -es (e.g. "manuel" <-> "manuels", "peter" <-> "peters")
        if (t1.length >= 4 && t2.length >= 4) {
            if ("${t1}s" == t2 || "${t2}s" == t1) return true
            if ("${t1}es" == t2 || "${t2}es" == t1) return true
        }
        // Allow small typo (1 edit) on sufficiently long tokens
        val minLen = min(t1.length, t2.length)
        if (minLen >= 5 && abs(t1.length - t2.length) <= 1) {
            if (damerauLevenshtein(t1, t2) <= 1) return true
        }
        return false
    }

    internal fun damerauLevenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, dp[i - 2][j - 2] + 1)
                }
                dp[i][j] = best
            }
        }
        return dp[m][n]
    }
}
