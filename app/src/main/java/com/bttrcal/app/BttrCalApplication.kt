package com.bttrcal.app

import android.app.Application
import android.content.Context
import com.bttrcal.app.data.ai.AiCalendarService
import com.bttrcal.app.data.ai.AiClient
import com.bttrcal.app.data.local.BttrCalDatabase
import com.bttrcal.app.data.local.PreferencesRepository
import com.bttrcal.app.data.repository.CalendarRepository
import com.bttrcal.app.data.security.SecretVault
import com.bttrcal.app.data.sync.SyncOrchestrator
import com.bttrcal.app.data.sync.SyncScheduler
import com.bttrcal.app.notifications.ReminderScheduler
import com.bttrcal.app.widget.WidgetUpdater

class BttrCalApplication : Application() {

    lateinit var secretVault: SecretVault
        private set
    lateinit var database: BttrCalDatabase
        private set
    lateinit var preferences: PreferencesRepository
        private set
    lateinit var repository: CalendarRepository
        private set
    lateinit var syncOrchestrator: SyncOrchestrator
        private set
    lateinit var aiClient: AiClient
        private set
    lateinit var aiService: AiCalendarService
        private set

    override fun onCreate() {
        super.onCreate()
        secretVault = SecretVault(this)
        database = BttrCalDatabase(this)
        preferences = PreferencesRepository(this, secretVault)

        val notifySideEffects: () -> Unit = {
            ReminderScheduler.rescheduleUpcoming(this, database)
            WidgetUpdater.updateAllWidgets(this)
        }

        repository = CalendarRepository(
            context = this,
            database = database,
            preferences = preferences,
            secretVault = secretVault,
            onDataMutated = notifySideEffects
        )

        syncOrchestrator = SyncOrchestrator(
            context = this,
            database = database,
            preferences = preferences,
            secretVault = secretVault,
            onSyncFinished = {
                repository.refreshFromDatabase()
                notifySideEffects()
            }
        )

        aiClient = AiClient()
        aiService = AiCalendarService(aiClient, preferences)

        ReminderScheduler.ensureChannel(this)
        ReminderScheduler.rescheduleUpcoming(this, database)

        val currentSettings = preferences.settings.value
        SyncScheduler.schedulePeriodicSync(
            context = this,
            interval = currentSettings.syncInterval,
            wifiOnly = currentSettings.wifiOnlySync
        )
    }

    companion object {
        fun from(context: Context): BttrCalApplication =
            context.applicationContext as BttrCalApplication
    }
}
