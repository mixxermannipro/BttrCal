package com.bttrcal.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bttrcal.app.BttrCalApplication

class CalendarSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = BttrCalApplication.from(applicationContext)
        return runCatching {
            val results = app.syncOrchestrator.syncAll()
            val allFailed = results.isNotEmpty() && results.none { it.success }
            if (allFailed) Result.retry() else Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
