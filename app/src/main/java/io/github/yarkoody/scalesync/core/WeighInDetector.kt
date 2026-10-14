package io.github.yarkoody.scalesync.core

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Turns the stream of advertisement frames into weigh-ins.
 *
 * The scale repeats the same frame many times: first unstable weights, then a stable weight,
 * then (if you are barefoot) a stable weight with impedance. A weigh-in is saved once, as soon
 * as the impedance frame arrives. If impedance never comes, the stable weight is saved on its
 * own after [WEIGHT_ONLY_DELAY_MS]. Both writes share one session id, so a late impedance frame
 * upgrades the weight-only record instead of duplicating it.
 */
object WeighInDetector {
    const val SESSION_WINDOW_MS = 120_000L
    const val WEIGHT_ONLY_DELAY_MS = 20_000L
    private const val SAME_WEIGHT_KG = 0.5f
    /** The scale re-broadcasts its last result when woken; ignore results older than this. */
    private val MAX_FRAME_AGE: Duration = Duration.ofMinutes(10)

    data class State(
        val sessionId: Long = 0L,
        val sessionVersion: Long = 0L,
        val lastSavedAtMs: Long = 0L,
        val lastSavedWeight: Float = 0f,
        val lastSavedHadImpedance: Boolean = false,
        val pendingWeight: Float = 0f,
        val pendingAtMs: Long = 0L,
    ) {
        val hasPending get() = pendingAtMs != 0L
    }

    sealed interface Action {
        data object Ignore : Action
        /**
         * Save now. [sessionId] is the epoch-ms the weigh-in started (used as the record time);
         * with [version] it becomes the Health Connect client record id/version.
         */
        data class Save(val weightKg: Float, val impedance: Int?, val sessionId: Long, val version: Long) : Action
        /** Run [commitPending] after [delayMs]. */
        data class ScheduleWeightOnly(val delayMs: Long) : Action
    }

    fun onFrame(state: State, frame: MiScaleFrame, nowMs: Long, now: LocalDateTime): Pair<State, Action> {
        if (!frame.stable || frame.removed) return state to Action.Ignore
        val scaleTime = frame.scaleTime
        if (scaleTime != null && scaleTime.isBefore(now.minus(MAX_FRAME_AGE))) return state to Action.Ignore

        val inSession = state.lastSavedAtMs != 0L &&
            nowMs - state.lastSavedAtMs < SESSION_WINDOW_MS &&
            abs(frame.weightKg - state.lastSavedWeight) < SAME_WEIGHT_KG

        if (frame.impedance != null) {
            if (inSession && state.lastSavedHadImpedance) return state to Action.Ignore
            val (id, version) = when {
                inSession -> state.sessionId to state.sessionVersion + 1
                state.hasPending && nowMs - state.pendingAtMs < SESSION_WINDOW_MS -> state.pendingAtMs to 0L
                else -> nowMs to 0L
            }
            val next = State(
                sessionId = id, sessionVersion = version,
                lastSavedAtMs = nowMs, lastSavedWeight = frame.weightKg, lastSavedHadImpedance = true,
            )
            return next to Action.Save(frame.weightKg, frame.impedance, id, version)
        }

        if (inSession) return state to Action.Ignore
        if (state.hasPending && nowMs - state.pendingAtMs < SESSION_WINDOW_MS) {
            return state.copy(pendingWeight = frame.weightKg) to Action.Ignore
        }
        return state.copy(pendingWeight = frame.weightKg, pendingAtMs = nowMs) to
            Action.ScheduleWeightOnly(WEIGHT_ONLY_DELAY_MS)
    }

    /** Called when the weight-only timer fires. */
    fun commitPending(state: State, nowMs: Long): Pair<State, Action> {
        if (!state.hasPending) return state to Action.Ignore
        // Saved with impedance in the meantime.
        if (state.lastSavedAtMs >= state.pendingAtMs) return state.copy(pendingAtMs = 0L, pendingWeight = 0f) to Action.Ignore
        val id = state.pendingAtMs
        val next = State(
            sessionId = id, sessionVersion = 0L,
            lastSavedAtMs = maxOf(nowMs, state.pendingAtMs), lastSavedWeight = state.pendingWeight,
            lastSavedHadImpedance = false,
        )
        return next to Action.Save(state.pendingWeight, null, id, 0L)
    }
}
