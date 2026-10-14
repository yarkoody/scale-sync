package io.github.yarkoody.scalesync

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.yarkoody.scalesync.core.BodyComposition
import io.github.yarkoody.scalesync.core.MiScaleFrame
import io.github.yarkoody.scalesync.core.WeighInDetector
import io.github.yarkoody.scalesync.data.History
import io.github.yarkoody.scalesync.data.Store
import io.github.yarkoody.scalesync.data.WeighIn
import io.github.yarkoody.scalesync.health.HealthConnectWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class LiveReading(val frame: MiScaleFrame, val atMs: Long)

/** Single entry point for every frame, from the background scan and the in-app scan alike. */
object Processor {
    private const val TAG = "ScaleSync"
    private const val COMMIT_WORK = "commit-weight-only"

    private val _live = MutableStateFlow<LiveReading?>(null)
    val live: StateFlow<LiveReading?> = _live

    /** Bumped whenever history changes so the UI can reload it. */
    private val _historyVersion = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _historyVersion

    suspend fun onServiceData(context: Context, data: ByteArray?) {
        val frame = MiScaleFrame.parse(data) ?: return
        val now = System.currentTimeMillis()
        _live.value = LiveReading(frame, now)

        val store = Store(context)
        val action = synchronized(this) {
            val (next, action) = WeighInDetector.onFrame(store.detector, frame, now, LocalDateTime.now())
            store.detector = next
            action
        }
        handle(context, action)
    }

    suspend fun commitPending(context: Context) {
        val store = Store(context)
        val action = synchronized(this) {
            val (next, action) = WeighInDetector.commitPending(store.detector, System.currentTimeMillis())
            store.detector = next
            action
        }
        handle(context, action)
    }

    private suspend fun handle(context: Context, action: WeighInDetector.Action) {
        when (action) {
            WeighInDetector.Action.Ignore -> Unit
            is WeighInDetector.Action.ScheduleWeightOnly -> {
                val req = OneTimeWorkRequestBuilder<CommitWorker>()
                    .setInitialDelay(action.delayMs, TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(COMMIT_WORK, ExistingWorkPolicy.REPLACE, req)
            }
            is WeighInDetector.Action.Save -> save(context, action)
        }
    }

    private suspend fun save(context: Context, a: WeighInDetector.Action.Save) {
        if (a.impedance != null) WorkManager.getInstance(context).cancelUniqueWork(COMMIT_WORK)
        val date = Instant.ofEpochMilli(a.sessionId).atZone(ZoneId.systemDefault()).toLocalDate()
        val metrics = BodyComposition.compute(a.weightKg, a.impedance, Store(context).profile, date)
        val history = History(context)
        val previous = history.all().find { it.sessionId == a.sessionId }
        val version = maxOf(a.version, (previous?.version ?: -1L) + 1)
        val weighIn = WeighIn(a.sessionId, version, metrics, syncedToHealthConnect = false)
        history.upsert(weighIn)
        _historyVersion.value++

        val synced = runCatching { HealthConnectWriter.write(context, weighIn) }
            .onFailure { Log.w(TAG, "Health Connect write failed", it) }
            .getOrDefault(false)
        if (synced) {
            history.markSynced(a.sessionId)
            _historyVersion.value++
        }
        Notifier.weighInSaved(context, weighIn, synced)
    }

    /** Retries weigh-ins that could not be written to Health Connect (no permission yet, etc). */
    suspend fun retryUnsynced(context: Context): Int {
        val history = History(context)
        var count = 0
        for (w in history.all().filterNot { it.syncedToHealthConnect }) {
            val ok = runCatching { HealthConnectWriter.write(context, w) }.getOrDefault(false)
            if (ok) { history.markSynced(w.sessionId); count++ }
        }
        if (count > 0) _historyVersion.value++
        return count
    }

    /** Recomputes composition for stored weigh-ins after the profile changes, and re-syncs them. */
    suspend fun recomputeAll(context: Context) {
        val history = History(context)
        val profile = Store(context).profile
        for (w in history.all()) {
            val date = w.time.atZone(ZoneId.systemDefault()).toLocalDate()
            val metrics = BodyComposition.compute(w.metrics.weightKg, w.metrics.impedance, profile, date)
            history.upsert(w.copy(version = w.version + 1, metrics = metrics, syncedToHealthConnect = false))
        }
        _historyVersion.value++
        retryUnsynced(context)
    }
}
