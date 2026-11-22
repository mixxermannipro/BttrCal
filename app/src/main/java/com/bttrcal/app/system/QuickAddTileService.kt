package com.bttrcal.app.system

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bttrcal.app.BttrCalApplication
import com.bttrcal.app.MainActivity
import com.bttrcal.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = getString(R.string.qs_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        tile.state = Tile.STATE_ACTIVE

        val nextSubtitle = runCatching {
            val app = BttrCalApplication.from(this)
            val now = System.currentTimeMillis()
            val upcoming = app.repository.getOccurrencesInRange(
                rangeStartMillis = now,
                rangeEndMillis = now + 7L * 86_400_000L,
                onlyVisible = true
            ).firstOrNull()

            if (upcoming != null) {
                val timeStr = if (upcoming.event.allDay) {
                    "Ganztägig"
                } else {
                    Instant.ofEpochMilli(upcoming.occurrenceStartMillis)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN))
                }
                "$timeStr · ${upcoming.event.title}"
            } else {
                "Termin erstellen"
            }
        }.getOrDefault("Termin erstellen")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = nextSubtitle
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                7001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
