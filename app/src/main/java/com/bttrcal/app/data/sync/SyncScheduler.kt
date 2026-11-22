package com.bttrcal.app.data.sync

import android.content.Context
import android.provider.CalendarContract
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bttrcal.app.domain.model.SyncIntervalOption
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK_NAME = "bttrcal_periodic_sync"
    private const val IMMEDIATE_WORK_NAME = "bttrcal_immediate_sync"
    private const val CONTENT_OBSERVER_WORK_NAME = "bttrcal_system_calendar_observer"

    fun schedulePeriodicSync(
        context: Context,
        interval: SyncIntervalOption,
        wifiOnly: Boolean
    ) {
        val workManager = WorkManager.getInstance(context)
        if (interval == SyncIntervalOption.MANUAL || interval.minutes < 15L) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            interval.minutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun triggerImmediateBackgroundSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Zero-polling system integration: wakes up automatically via JobScheduler
     * whenever Google Calendar, Honor Calendar, or Exchange modifies CalendarContract.
     */
    fun scheduleSystemCalendarObserver(context: Context, replaceExisting: Boolean = false) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(CalendarContract.Events.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(4, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(20, TimeUnit.SECONDS)
            .build()

        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CONTENT_OBSERVER_WORK_NAME,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }
}
