package io.github.yarkoody.scalesync.core

import io.github.yarkoody.scalesync.core.WeighInDetector.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WeighInDetectorTest {
    private val now = LocalDateTime.of(2026, 10, 14, 7, 30)
    private fun f(w: Float, imp: Int? = null, stable: Boolean = true, removed: Boolean = false, t: LocalDateTime? = now) =
        MiScaleFrame(w, imp, stable, removed, t)

    @Test
    fun barefoot_weigh_in_saves_once() {
        var s = WeighInDetector.State()
        val t0 = 1_000_000L
        var (s1, a) = WeighInDetector.onFrame(s, f(72f, stable = false), t0, now)
        assertEquals(Action.Ignore, a)
        val r1 = WeighInDetector.onFrame(s1, f(72f), t0 + 1000, now)
        assertTrue(r1.second is Action.ScheduleWeightOnly)
        val r2 = WeighInDetector.onFrame(r1.first, f(72f, 480), t0 + 3000, now)
        val save = r2.second as Action.Save
        assertEquals(480, save.impedance)
        assertEquals(t0 + 1000, save.sessionId)
        // Repeats are ignored.
        assertEquals(Action.Ignore, WeighInDetector.onFrame(r2.first, f(72f, 480), t0 + 4000, now).second)
        assertEquals(Action.Ignore, WeighInDetector.onFrame(r2.first, f(72f), t0 + 5000, now).second)
        // Timer fires afterwards: nothing to do.
        assertEquals(Action.Ignore, WeighInDetector.commitPending(r2.first, t0 + 21000).second)
    }

    @Test
    fun socks_weigh_in_saves_weight_only_then_upgrades() {
        val t0 = 1_000_000L
        val r1 = WeighInDetector.onFrame(WeighInDetector.State(), f(72f), t0, now)
        val c = WeighInDetector.commitPending(r1.first, t0 + 20_000)
        val save = c.second as Action.Save
        assertEquals(null, save.impedance)
        assertEquals(t0, save.sessionId)
        // Late impedance frame replaces the same record with a higher version.
        val up = WeighInDetector.onFrame(c.first, f(72.05f, 470), t0 + 25_000, now).second as Action.Save
        assertEquals(t0, up.sessionId)
        assertEquals(1L, up.version)
    }

    @Test
    fun replayed_old_result_is_ignored() {
        val old = now.minusHours(3)
        assertEquals(Action.Ignore, WeighInDetector.onFrame(WeighInDetector.State(), f(72f, 480, t = old), 1L, now).second)
    }

    @Test
    fun second_person_is_a_new_weigh_in() {
        val t0 = 1_000_000L
        val a = WeighInDetector.onFrame(WeighInDetector.State(), f(72f, 480), t0, now)
        val b = WeighInDetector.onFrame(a.first, f(58f, 520), t0 + 30_000, now).second as Action.Save
        assertEquals(t0 + 30_000, b.sessionId)
    }
}
