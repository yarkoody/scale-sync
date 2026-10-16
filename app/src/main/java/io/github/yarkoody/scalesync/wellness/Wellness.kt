package io.github.yarkoody.scalesync.wellness

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class Stage { AWAKE, LIGHT, DEEP, REM, ASLEEP_UNSPECIFIED, UNKNOWN }

data class StageSpan(val stage: Stage, val start: Instant, val end: Instant) {
    val minutes: Long get() = Duration.between(start, end).toMinutes()
}

/** One night of sleep, filed under the morning you woke up. */
data class Night(
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val asleepMin: Long,
    val deepMin: Long,
    val remMin: Long,
    val lightMin: Long,
    val awakeMin: Long,
    val stages: List<StageSpan>,
    val source: String = "",
) {
    val inBedMin: Long get() = Duration.between(start, end).toMinutes()
    val hasStages: Boolean get() = deepMin + remMin + lightMin > 0
    /** Share of time in bed actually asleep. */
    val efficiency: Int get() = if (inBedMin <= 0) 0 else (100.0 * asleepMin / inBedMin).roundToInt()

    companion object {
        fun from(start: Instant, end: Instant, stages: List<StageSpan>, zone: ZoneId, source: String = ""): Night {
            fun sum(vararg s: Stage) = stages.filter { it.stage in s }.sumOf { it.minutes }
            val total = Duration.between(start, end).toMinutes()
            val awake = sum(Stage.AWAKE)
            val light = sum(Stage.LIGHT)
            val deep = sum(Stage.DEEP)
            val rem = sum(Stage.REM)
            val asleep = if (stages.isEmpty()) total
            else (light + deep + rem + sum(Stage.ASLEEP_UNSPECIFIED)).takeIf { it > 0 } ?: (total - awake)
            return Night(end.atZone(zone).toLocalDate(), start, end, asleep, deep, rem, light, awake,
                stages.sortedBy { it.start }, source)
        }

        /** Keeps the main sleep for each morning (the longest), so naps don't count as the night. */
        fun mainPerMorning(nights: List<Night>): List<Night> =
            nights.groupBy { it.date }.values.map { n -> n.maxBy { it.asleepMin } }.sortedByDescending { it.date }
    }
}

/** Daily values read from Health Connect. Null means nothing was recorded that day. */
data class Day(
    val date: LocalDate,
    val steps: Long? = null,
    val restingHr: Double? = null,
    val hrvMs: Double? = null,
    val spo2: Double? = null,
    val breathingRate: Double? = null,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val activeMinutes: Long? = null,
)

/** A metric's latest value against your own recent normal. */
data class Trend(val latest: Double?, val latestDate: LocalDate?, val avg7: Double?, val avg30: Double?, val count: Int) {
    /** Latest week's average compared with the 30-day baseline, in percent. */
    val weekVsBaselinePct: Double?
        get() = if (avg7 == null || avg30 == null || avg30 == 0.0 || count < 10) null else 100 * (avg7 - avg30) / avg30
}

data class Wellness(val days: List<Day> = emptyList(), val nights: List<Night> = emptyList()) {
    val isEmpty: Boolean get() = nights.isEmpty() && days.all { it == Day(it.date) }

    fun trend(today: LocalDate, pick: (Day) -> Double?): Trend = trendOf(today, days.mapNotNull { d -> pick(d)?.let { d.date to it } })

    fun sleepTrend(today: LocalDate): Trend = trendOf(today, nights.map { it.date to it.asleepMin.toDouble() })

    companion object {
        fun trendOf(today: LocalDate, points: List<Pair<LocalDate, Double>>): Trend {
            val sorted = points.sortedBy { it.first }
            fun avg(days: Long) = sorted.filter { it.first > today.minusDays(days) && it.first <= today }
                .map { it.second }.takeIf { it.isNotEmpty() }?.average()
            val last = sorted.lastOrNull()
            return Trend(last?.second, last?.first, avg(7), avg(30), sorted.count { it.first > today.minusDays(30) })
        }
    }
}

fun hm(minutes: Long): String = "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
