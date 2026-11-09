package io.github.yarkoody.scalesync.core

import io.github.yarkoody.scalesync.core.WeighInDetector.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Scales without a stable flag: a weight that holds still for 2 s counts as stable. */
class SteadyWeightTest {
    private val now = LocalDateTime.of(2026, 10, 14, 7, 30)
    private fun f(w: Float) = Frame(weightKg = w, stable = null)

    @Test
    fun holds_still_then_saves_weight_only_quickly() {
        var s = WeighInDetector.State()
        val t0 = 1_000_000L
        val delay = WeighInDetector.WEIGHT_ONLY_SCALE_DELAY_MS
        // Settling.
        for ((i, w) in listOf(40f, 65f, 71.8f).withIndex()) {
            val (n, a) = WeighInDetector.onFrame(s, f(w), t0 + i * 500L, now, delay)
            assertEquals(Action.Ignore, a); s = n
        }
        // Holds at 72.0 ±0.05 from t0+1500.
        var scheduled: Action? = null
        var t = t0 + 1500
        while (t <= t0 + 4000) {
            val (n, a) = WeighInDetector.onFrame(s, f(if (t % 1000 == 0L) 72.05f else 72.0f), t, now, delay)
            s = n
            if (a != Action.Ignore) { scheduled = a; break }
            t += 250
        }
        assertEquals(Action.ScheduleWeightOnly(delay), scheduled)
        assertTrue(t - (t0 + 1500) >= WeighInDetector.STEADY_MS)
        val save = WeighInDetector.commitPending(s, t + delay).second as Action.Save
        assertEquals(72.0f, save.weightKg, 0.06f)
        assertEquals(null, save.impedance)
    }

    @Test
    fun a_gap_in_frames_restarts_the_steady_clock() {
        val t0 = 1_000_000L
        val a = WeighInDetector.onFrame(WeighInDetector.State(), f(72f), t0, now).first
        // Same weight but 10 s later: not counted as one steady stretch.
        val (b, act) = WeighInDetector.onFrame(a, f(72f), t0 + 10_000, now)
        assertEquals(Action.Ignore, act)
        assertEquals(t0 + 10_000, b.steadySinceMs)
    }

    @Test
    fun a_stable_flag_is_used_as_is() {
        val (_, a) = WeighInDetector.onFrame(WeighInDetector.State(), Frame(72f, stable = true), 5L, now)
        assertTrue(a is Action.ScheduleWeightOnly)
    }
}
