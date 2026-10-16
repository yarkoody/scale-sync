package io.github.yarkoody.scalesync.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WellnessTest {
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Test
    fun night_counts_stages_and_files_under_the_morning() {
        val s = Instant.parse("2026-10-14T20:30:00Z")
        val e = Instant.parse("2026-10-15T04:00:00Z")
        val n = Night.from(s, e, listOf(
            StageSpan(Stage.LIGHT, s, s.plusSeconds(3 * 3600)),
            StageSpan(Stage.DEEP, s.plusSeconds(3 * 3600), s.plusSeconds(5 * 3600)),
            StageSpan(Stage.AWAKE, s.plusSeconds(5 * 3600), s.plusSeconds(5 * 3600 + 1800)),
            StageSpan(Stage.REM, s.plusSeconds(5 * 3600 + 1800), e),
        ), zone)
        assertEquals(LocalDate.of(2026, 10, 15), n.date)
        assertEquals(420L, n.asleepMin)
        assertEquals(120L, n.deepMin)
        assertEquals(30L, n.awakeMin)
        assertEquals(93, n.efficiency)
    }

    @Test
    fun naps_do_not_replace_the_night() {
        val night = Night.from(Instant.parse("2026-10-14T21:00:00Z"), Instant.parse("2026-10-15T04:00:00Z"), emptyList(), zone)
        val nap = Night.from(Instant.parse("2026-10-15T12:00:00Z"), Instant.parse("2026-10-15T12:40:00Z"), emptyList(), zone)
        assertEquals(listOf(night), Night.mainPerMorning(listOf(nap, night)))
    }

    @Test
    fun trend_compares_week_with_baseline() {
        val today = LocalDate.of(2026, 10, 15)
        val days = (0L..29L).map { Day(today.minusDays(it), restingHr = if (it < 7) 66.0 else 60.0) }
        val t = Wellness(days).trend(today) { it.restingHr }
        assertEquals(66.0, t.latest!!, 0.0)
        assertEquals(66.0, t.avg7!!, 0.0)
        assertEquals(7.5, t.weekVsBaselinePct!!, 0.1)
        assertNull(Wellness(days.take(5)).trend(today) { it.restingHr }.weekVsBaselinePct)
    }
}
