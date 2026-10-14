package io.github.yarkoody.scalesync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Saves a weight-only weigh-in when no impedance reading followed (shoes, socks, stepped off early). */
class CommitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Processor.commitPending(applicationContext)
        return Result.success()
    }
}
