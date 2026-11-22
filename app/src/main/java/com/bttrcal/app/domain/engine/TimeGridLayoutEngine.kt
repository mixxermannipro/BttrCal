package com.bttrcal.app.domain.engine

import com.bttrcal.app.domain.model.EventOccurrence

data class PlacedEvent(
    val occurrence: EventOccurrence,
    val column: Int,
    val totalColumns: Int
)

object TimeGridLayoutEngine {

    fun layoutDayEvents(occurrences: List<EventOccurrence>): List<PlacedEvent> {
        if (occurrences.isEmpty()) return emptyList()
        val sorted = occurrences.sortedWith(
            compareBy<EventOccurrence> { it.occurrenceStartMillis }
                .thenByDescending { it.occurrenceEndMillis - it.occurrenceStartMillis }
        )

        val clusters = mutableListOf<MutableList<EventOccurrence>>()
        var currentCluster = mutableListOf<EventOccurrence>()
        var clusterMaxEnd = Long.MIN_VALUE

        for (item in sorted) {
            if (currentCluster.isEmpty() || item.occurrenceStartMillis < clusterMaxEnd) {
                currentCluster += item
                clusterMaxEnd = maxOf(clusterMaxEnd, item.occurrenceEndMillis)
            } else {
                clusters += currentCluster
                currentCluster = mutableListOf(item)
                clusterMaxEnd = item.occurrenceEndMillis
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters += currentCluster
        }

        val placed = mutableListOf<PlacedEvent>()
        for (cluster in clusters) {
            val columnEndTimes = mutableListOf<Long>()
            val assignedColumns = mutableListOf<Pair<EventOccurrence, Int>>()

            for (occ in cluster) {
                var targetCol = -1
                for (i in columnEndTimes.indices) {
                    if (columnEndTimes[i] <= occ.occurrenceStartMillis) {
                        targetCol = i
                        columnEndTimes[i] = occ.occurrenceEndMillis
                        break
                    }
                }
                if (targetCol == -1) {
                    targetCol = columnEndTimes.size
                    columnEndTimes += occ.occurrenceEndMillis
                }
                assignedColumns += occ to targetCol
            }

            val totalCols = columnEndTimes.size.coerceAtLeast(1)
            for ((occ, col) in assignedColumns) {
                placed += PlacedEvent(
                    occurrence = occ,
                    column = col,
                    totalColumns = totalCols
                )
            }
        }
        return placed
    }
}
