package com.bttrcal.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bttrcal.app.domain.model.GlassVariant
import com.bttrcal.app.domain.model.ThemeMode
import com.bttrcal.app.ui.MainViewModel
import com.bttrcal.app.ui.screens.BttrCalAppRoot
import com.bttrcal.app.ui.theme.BttrCalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(BttrCalApplication.from(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isDark = settings.themeMode == ThemeMode.DARK ||
                (settings.themeMode == ThemeMode.LIQUID_GLASS && settings.glassVariant == GlassVariant.DARK_GLASS)

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
            }

            BttrCalTheme(
                themeMode = settings.themeMode,
                glassVariant = settings.glassVariant
            ) {
                BttrCalAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val icsText = runCatching {
                contentResolver.openInputStream(intent.data!!)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!icsText.isNullOrBlank()) {
                val calId = viewModel.calendars.value.firstOrNull { !it.isReadOnly }?.id ?: "cal_local_private"
                viewModel.importIcsText(icsText, calId)
            }
        }
        viewModel.handleLaunchIntent(intent)
    }

    companion object {
        const val EXTRA_OPEN_EVENT_ID = "extra_open_event_id"
        const val EXTRA_OPEN_DATE_MILLIS = "extra_open_date_millis"
        const val EXTRA_QUICK_ADD = "extra_quick_add"
    }
}
