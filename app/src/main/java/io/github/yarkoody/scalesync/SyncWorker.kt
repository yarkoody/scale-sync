package io.github.yarkoody.scalesync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.yarkoody.scalesync.data.History
import java.util.concurrent.TimeUnit

/**
 * Retries weigh-ins that didn't reach Health Connect, with backoff (30 s, 1 min, 2 min, ...),
 * until all are synced. Scheduled after every save that fails.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Processor.retryUnsynced(applicationContext)
        val left = History(applicationContext).all().count { !it.syncedToHealthConnect }
        return when {
            left == 0 -> Result.success()
            runAttemptCount < 12 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val NAME = "health-connect-sync"

        fun schedule(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
        }
    }
}
