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
 * own after [WEIGHT_ONLY_DELAY_MS] (or a shorter delay for scales that never send impedance).
 * Both writes share one session id, so a late impedance frame upgrades the weight-only record
 * instead of duplicating it.
 *
 * Scales without a "stable" flag ([ScaleFrame.stable] null) count as stable once the weight has
 * stayed within [STEADY_KG] for [STEADY_MS].
 */
object WeighInDetector {
    const val SESSION_WINDOW_MS = 120_000L
    const val WEIGHT_ONLY_DELAY_MS = 20_000L
    /** For scales that never send impedance there is nothing to wait for but a settling weight. */
    const val WEIGHT_ONLY_SCALE_DELAY_MS = 5_000L
    const val STEADY_MS = 2_000L
    const val STEADY_KG = 0.1f
    /** Frames further apart than this don't count as one steady reading. */
    private const val STEADY_GAP_MS = 5_000L
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
        /** Last weight seen, for scales without a stable flag. */
        val rawWeight: Float = 0f,
        val rawAtMs: Long = 0L,
        val steadySinceMs: Long = 0L,
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

    fun onFrame(
        state: State,
        frame: ScaleFrame,
        nowMs: Long,
        now: LocalDateTime,
        weightOnlyDelayMs: Long = WEIGHT_ONLY_DELAY_MS,
    ): Pair<State, Action> = decide(track(state, frame, nowMs), frame, nowMs, now, weightOnlyDelayMs)

    /** Updates the steadiness tracking used for scales without a stable flag. */
    private fun track(state: State, frame: ScaleFrame, nowMs: Long): State {
        if (frame.stable != null) return state
        val steady = state.rawAtMs != 0L && nowMs - state.rawAtMs in 0..STEADY_GAP_MS &&
            abs(frame.weightKg - state.rawWeight) <= STEADY_KG
        return state.copy(
            rawWeight = frame.weightKg, rawAtMs = nowMs,
            steadySinceMs = if (steady) state.steadySinceMs else nowMs,
        )
    }

    private fun decide(
        state: State,
        frame: ScaleFrame,
        nowMs: Long,
        now: LocalDateTime,
        weightOnlyDelayMs: Long,
    ): Pair<State, Action> {
        val stable = frame.stable ?: (nowMs - state.steadySinceMs >= STEADY_MS)
        if (!stable || frame.removed) return state to Action.Ignore
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
            val next = state.copy(
                sessionId = id, sessionVersion = version,
                lastSavedAtMs = nowMs, lastSavedWeight = frame.weightKg, lastSavedHadImpedance = true,
                pendingWeight = 0f, pendingAtMs = 0L,
            )
            return next to Action.Save(frame.weightKg, frame.impedance, id, version)
        }

        if (inSession) return state to Action.Ignore
        if (state.hasPending && nowMs - state.pendingAtMs < SESSION_WINDOW_MS) {
            return state.copy(pendingWeight = frame.weightKg) to Action.Ignore
        }
        return state.copy(pendingWeight = frame.weightKg, pendingAtMs = nowMs) to
            Action.ScheduleWeightOnly(weightOnlyDelayMs)
    }

    /** Called when the weight-only timer fires. */
    fun commitPending(state: State, nowMs: Long): Pair<State, Action> {
        if (!state.hasPending) return state to Action.Ignore
        // Saved with impedance in the meantime.
        if (state.lastSavedAtMs >= state.pendingAtMs) return state.copy(pendingAtMs = 0L, pendingWeight = 0f) to Action.Ignore
        val id = state.pendingAtMs
        val next = state.copy(
            sessionId = id, sessionVersion = 0L,
            lastSavedAtMs = maxOf(nowMs, state.pendingAtMs), lastSavedWeight = state.pendingWeight,
            lastSavedHadImpedance = false,
            pendingWeight = 0f, pendingAtMs = 0L,
        )
        return next to Action.Save(state.pendingWeight, null, id, 0L)
    }
}
